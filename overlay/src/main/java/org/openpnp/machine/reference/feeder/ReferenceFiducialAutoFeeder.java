/*
 * Copyright (C) 2026 crono2250
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.openpnp.machine.reference.feeder;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.opencv.core.RotatedRect;
import org.openpnp.ConfigurationListener;
import org.openpnp.gui.support.PropertySheetWizardAdapter;
import org.openpnp.gui.support.Wizard;
import org.openpnp.machine.reference.feeder.wizards.ReferenceAutoFeederConfigurationWizard;
import org.openpnp.machine.reference.feeder.wizards.ReferenceFiducialAutoFeederCalibrationWizard;
import org.openpnp.model.Configuration;
import org.openpnp.model.LengthUnit;
import org.openpnp.model.Location;
import org.openpnp.model.Part;
import org.openpnp.spi.Camera;
import org.openpnp.spi.Machine;
import org.openpnp.spi.MachineListener;
import org.openpnp.spi.Nozzle;
import org.openpnp.spi.PropertySheetHolder.PropertySheet;
import org.openpnp.util.MovableUtils;
import org.openpnp.util.VisionUtils;
import org.openpnp.vision.pipeline.CvPipeline;
import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Element;
import org.pmw.tinylog.Logger;

/** ReferenceAutoFeeder with three machine fiducials and optional top-camera part vision. */
public class ReferenceFiducialAutoFeeder extends ReferenceAutoFeeder {
    public enum PartVisionMode {
        EveryFeed("Every feed"),
        FirstFeedInJob("First feed in job"),
        ManualOnly("Manual only");

        private final String label;

        PartVisionMode(String label) { this.label = label; }
        @Override public String toString() { return label; }
    }

    private static final ScheduledExecutorService TIMER = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "fiducial-feeder-timer");
        thread.setDaemon(true);
        return thread;
    });

    @Element(required = false)
    private Location fidALocation = new Location(LengthUnit.Millimeters);
    @Element(required = false)
    private Location fidBLocation = new Location(LengthUnit.Millimeters);
    @Element(required = false)
    private Location fidCLocation = new Location(LengthUnit.Millimeters);
    @Attribute(required = false)
    private boolean fidCUseMachineOrigin;
    @Attribute(required = false)
    private String fiducialSurfaceId;
    @Attribute(required = false)
    private String fiducialPartId = "";
    @Attribute(required = false)
    private boolean periodicCalibration;
    @Attribute(required = false)
    private int calibrationIntervalMinutes = 5;
    @Attribute(required = false)
    private double maxFiducialShiftMm = 3.0;
    @Attribute(required = false)
    private double maxBasisChangePercent = 1.0;
    @Attribute(required = false)
    private boolean partVisionEnabled;
    @Attribute(required = false)
    private PartVisionMode partVisionMode = PartVisionMode.EveryFeed;
    @Attribute(required = false)
    private double maxPartShiftMm = 1.0;
    @Element(required = false)
    private CvPipeline partPipeline = ReferenceLoosePartFeeder.createDefaultPipeline();

    private transient volatile ThreePointAffine transform;
    private transient volatile Location measuredPickLocation;
    private transient volatile boolean partVisionDoneForJob;
    private transient volatile long lastCalibrationNanos;
    private transient volatile Instant lastCalibrationTime;
    private transient volatile String lastCalibrationError;
    private transient volatile long calibrationGeneration;
    private transient final AtomicBoolean queued = new AtomicBoolean();

    public ReferenceFiducialAutoFeeder() {
        Configuration.get().addListener(new ConfigurationListener.Adapter() {
            @Override
            public void configurationComplete(Configuration configuration) throws Exception {
                Machine machine = configuration.getMachine();
                machine.addListener(new MachineListener.Adapter() {
                    @Override
                    public void machineHomed(Machine eventMachine, boolean homed) {
                        if (!homed) {
                            invalidateCalibration();
                        }
                        else if (isEnabled()) {
                            queueCalibration(eventMachine);
                        }
                    }
                });
                TIMER.scheduleAtFixedRate(() -> {
                    if (isEnabled() && periodicCalibration && machine.isEnabled() && machine.isHomed()
                            && !machine.isBusy() && isCalibrationDue()) {
                        queueCalibration(machine);
                    }
                }, 1, 1, TimeUnit.MINUTES);
            }
        });
    }

    private boolean isCalibrationDue() {
        return transform == null || calibrationIntervalMinutes > 0
                && System.nanoTime() - lastCalibrationNanos >= TimeUnit.MINUTES.toNanos(calibrationIntervalMinutes);
    }

    private void queueCalibration(Machine machine) {
        if (!queued.compareAndSet(false, true)) {
            return;
        }
        machine.submit(() -> {
            try {
                if (machine.isEnabled() && machine.isHomed() && isEnabled()) {
                    calibrateNow();
                }
            }
            catch (Exception e) {
                lastCalibrationError = e.getMessage();
                Logger.error(e, "Fiducial calibration failed for feeder {}", getName());
            }
            finally {
                queued.set(false);
            }
        });
    }

    public void invalidateCalibration() {
        calibrationGeneration++;
        transform = null;
        measuredPickLocation = null;
        partVisionDoneForJob = false;
        lastCalibrationNanos = 0;
    }

    /** Must be called on OpenPnP's machine task thread. */
    public synchronized void calibrateNow() throws Exception {
        Machine machine = Configuration.get().getMachine();
        if (!machine.isEnabled() || !machine.isHomed()) {
            throw new Exception("Enable and home the machine before fiducial calibration.");
        }
        if (fiducialPartId == null || fiducialPartId.trim().isEmpty()) {
            throw new Exception("Set the fiducial Part ID first.");
        }
        Part part = Configuration.get().getPart(fiducialPartId.trim());
        if (part == null) {
            throw new Exception("Fiducial Part ID not found: " + fiducialPartId);
        }
        FiducialCatalog catalog = FiducialCatalog.get();
        FiducialCatalog.Surface surface = getOrCreateSurface();
        if (surface == null) {
            throw new Exception("Select an installation surface for feeder " + getName());
        }
        long generation = calibrationGeneration;
        Location[] nominal = {
                getAssignedLocation(catalog, surface, 0),
                getAssignedLocation(catalog, surface, 1),
                surface.isCUseMachineOrigin() ? new Location(LengthUnit.Millimeters)
                        : getAssignedLocation(catalog, surface, 2)};
        String[] names = {"fid_A", "fid_B", "fid_C"};
        double[][] expected = new double[3][2];
        double[][] observed = new double[3][2];
        if (!Double.isFinite(maxFiducialShiftMm) || !Double.isFinite(maxBasisChangePercent)
                || maxFiducialShiftMm <= 0 || maxBasisChangePercent <= 0
                || periodicCalibration && calibrationIntervalMinutes <= 0) {
            throw new Exception("Calibration limits must be greater than zero.");
        }
        try {
            for (int i = 0; i < 3; i++) {
                if (nominal[i] == null) {
                    throw new Exception(names[i] + " has no nominal location.");
                }
                Location mm = nominal[i].convertToUnits(LengthUnit.Millimeters);
                expected[i][0] = mm.getX();
                expected[i][1] = mm.getY();
            }
            // Validate geometry before moving the camera.
            new ThreePointAffine(expected, expected);
            for (int i = 0; i < 3; i++) {
                // Fiducials are XY points. The locator supplies the camera's default focus Z.
                Location search = new Location(LengthUnit.Millimeters,
                        expected[i][0], expected[i][1], 0, 0);
                Location found = machine.getFiducialLocator().getHomeFiducialLocation(search, part);
                if (found == null) {
                    throw new Exception(names[i] + " was not detected.");
                }
                Location mm = found.convertToUnits(LengthUnit.Millimeters);
                observed[i][0] = mm.getX();
                observed[i][1] = mm.getY();
                if (Math.hypot(observed[i][0] - expected[i][0], observed[i][1] - expected[i][1])
                        > maxFiducialShiftMm) {
                    throw new Exception(names[i] + " exceeds the permitted shift.");
                }
            }
            ThreePointAffine next = new ThreePointAffine(expected, observed);
            if (next.maximumBasisChange() * 100 > maxBasisChangePercent) {
                throw new Exception("Fiducial scale or shear exceeds the permitted change.");
            }
            if (generation != calibrationGeneration) {
                throw new Exception("Fiducial settings changed during calibration; run it again.");
            }
            transform = next;
            measuredPickLocation = null;
            lastCalibrationNanos = System.nanoTime();
            lastCalibrationTime = Instant.now();
            lastCalibrationError = null;
            Logger.info("Three-point calibration completed for feeder {}", getName());
        }
        catch (Exception e) {
            invalidateCalibration();
            lastCalibrationError = e.getMessage();
            throw e;
        }
    }

    private void ensureCalibration(boolean force) throws Exception {
        if (force || transform == null || periodicCalibration && isCalibrationDue()) {
            calibrateNow();
        }
    }

    @Override
    public void prepareForJob(boolean visit) throws Exception {
        // OpenPnP calls this for every feeder used by the job.
        partVisionDoneForJob = false;
        ensureCalibration(true);
    }

    @Override
    public void feed(Nozzle nozzle) throws Exception {
        ensureCalibration(false);
        measuredPickLocation = null;
        FeedOptions option = getFeedOptions();
        if (option == FeedOptions.Normal
                && (getActuatorName() == null || getActuatorName().trim().isEmpty())) {
            throw new Exception("Set a feed actuator before using feeder " + getName());
        }
        super.feed(nozzle);
        if (partVisionEnabled && option != FeedOptions.Disable
                && (getPartVisionMode() == PartVisionMode.EveryFeed
                        || getPartVisionMode() == PartVisionMode.FirstFeedInJob && !partVisionDoneForJob)) {
            measuredPickLocation = locatePart(nozzle.getHead().getDefaultCamera());
            partVisionDoneForJob = true;
        }
    }

    /** Run part recognition on demand, after a part has been presented. */
    public synchronized void locatePartNow() throws Exception {
        if (!partVisionEnabled) {
            throw new Exception("Enable part vision before manual recognition.");
        }
        ensureCalibration(false);
        measuredPickLocation = null;
        measuredPickLocation = locatePart(Configuration.get().getMachine().getDefaultHead().getDefaultCamera());
    }

    @Override
    public Location getPickLocation() throws Exception {
        Location measured = measuredPickLocation;
        if (measured != null) {
            return measured;
        }
        ThreePointAffine current = transform;
        if (current == null) {
            if (Configuration.get().getMachine().isHomed()) {
                throw new Exception("Feeder " + getName() + " has no valid three-point calibration.");
            }
            return location;
        }
        Location mm = location.convertToUnits(LengthUnit.Millimeters);
        double[] xy = current.transform(mm.getX(), mm.getY());
        return new Location(LengthUnit.Millimeters, xy[0], xy[1], mm.getZ(),
                current.transformAngle(mm.getRotation())).convertToUnits(location.getUnits());
    }

    private Location locatePart(Camera camera) throws Exception {
        if (partPipeline == null) {
            throw new Exception("Part vision pipeline is missing.");
        }
        if (!Double.isFinite(maxPartShiftMm) || maxPartShiftMm <= 0) {
            throw new Exception("Maximum part shift must be greater than zero.");
        }
        Location nominalPick = getPickLocation();
        Location view = nominalPick.deriveLengths(null, null, camera.getDefaultZ(), null);
        MovableUtils.moveToLocationAtSafeZ(camera, view);
        try (CvPipeline pipeline = partPipeline.clone()) {
            pipeline.setProperty("camera", camera);
            pipeline.setProperty("feeder", this);
            pipeline.setProperty("part", getPart());
            pipeline.process();
            List<RotatedRect> results = pipeline.getExpectedResult(VisionUtils.PIPELINE_RESULTS_NAME)
                    .getExpectedListModel(RotatedRect.class,
                            new Exception("No part detected on feeder " + getName()));
            Location best = null;
            double distance = Double.POSITIVE_INFINITY;
            for (RotatedRect result : results) {
                Location candidate = VisionUtils.getPixelLocation(camera, result.center.x, result.center.y)
                        .convertToUnits(LengthUnit.Millimeters);
                double d = candidate.getLinearDistanceTo(nominalPick.convertToUnits(LengthUnit.Millimeters));
                if (d < distance) {
                    best = candidate;
                    distance = d;
                }
            }
            if (best == null || distance > maxPartShiftMm) {
                throw new Exception("Part vision result exceeds " + maxPartShiftMm + " mm or is missing.");
            }
            Location mm = nominalPick.convertToUnits(LengthUnit.Millimeters);
            return best.derive(null, null, mm.getZ(), mm.getRotation()).convertToUnits(location.getUnits());
        }
    }

    @Override
    public Wizard getConfigurationWizard() {
        return new ReferenceAutoFeederConfigurationWizard(this);
    }

    @Override
    public PropertySheet[] getPropertySheets() {
        return new PropertySheet[] {
                new PropertySheetWizardAdapter(getConfigurationWizard(), "Configuration"),
                new PropertySheetWizardAdapter(new ReferenceFiducialAutoFeederCalibrationWizard(this),
                        "Calibration")};
    }

    public Location getFidALocation() { return fidALocation; }
    public void setFidALocation(Location value) {
        fidALocation = value;
        invalidateCalibration();
        firePropertyChange("fidALocation", null, value);
    }
    public Location getFidBLocation() { return fidBLocation; }
    public void setFidBLocation(Location value) {
        fidBLocation = value;
        invalidateCalibration();
        firePropertyChange("fidBLocation", null, value);
    }
    public Location getFidCLocation() { return fidCLocation; }
    public void setFidCLocation(Location value) {
        fidCLocation = value;
        invalidateCalibration();
        firePropertyChange("fidCLocation", null, value);
    }
    /** The machine coordinate origin is X=0, Y=0; a custom selected mark is preserved. */
    public Location getEffectiveFidCLocation() {
        if (fiducialSurfaceId != null) {
            FiducialCatalog catalog = FiducialCatalog.get();
            FiducialCatalog.Surface surface = catalog.getSurface(fiducialSurfaceId);
            if (surface != null) {
                return surface.isCUseMachineOrigin() ? new Location(LengthUnit.Millimeters)
                        : getAssignedLocation(catalog, surface, 2);
            }
        }
        return fidCUseMachineOrigin ? new Location(LengthUnit.Millimeters) : fidCLocation;
    }
    public boolean isFidCUseMachineOrigin() { return fidCUseMachineOrigin; }
    public void setFidCUseMachineOrigin(boolean value) {
        boolean old = fidCUseMachineOrigin;
        fidCUseMachineOrigin = value;
        invalidateCalibration();
        firePropertyChange("fidCUseMachineOrigin", old, value);
    }
    public String getFiducialSurfaceId() { return fiducialSurfaceId; }
    public void setFiducialSurfaceId(String value) {
        String old = fiducialSurfaceId;
        fiducialSurfaceId = value;
        invalidateCalibration();
        firePropertyChange("fiducialSurfaceId", old, value);
    }

    /** New feeders join the first surface; old coordinates are migrated and deduplicated. */
    public synchronized FiducialCatalog.Surface getOrCreateSurface() {
        FiducialCatalog catalog = FiducialCatalog.get();
        FiducialCatalog.Surface surface = catalog.getSurface(fiducialSurfaceId);
        if (surface != null) {
            return surface;
        }
        if (fiducialSurfaceId != null) {
            return null;
        }
        boolean legacyCoordinates = fidCUseMachineOrigin || hasXY(fidALocation)
                || hasXY(fidBLocation) || hasXY(fidCLocation);
        if (!legacyCoordinates && !catalog.getSurfaces().isEmpty()) {
            surface = catalog.getSurfaces().get(0);
        }
        else {
            surface = catalog.migrate(fidALocation, fidBLocation, fidCLocation,
                    fidCUseMachineOrigin);
        }
        setFiducialSurfaceId(surface.getId());
        return surface;
    }

    private static boolean hasXY(Location location) {
        if (location == null) {
            return false;
        }
        Location mm = location.convertToUnits(LengthUnit.Millimeters);
        return mm.getX() != 0 || mm.getY() != 0;
    }

    private static Location getAssignedLocation(FiducialCatalog catalog,
            FiducialCatalog.Surface surface, int index) {
        if (surface == null) {
            return null;
        }
        FiducialCatalog.Mark mark = catalog.getMark(surface.getMarkId(index));
        return mark == null ? null : mark.getLocation();
    }
    public String getFiducialPartId() { return fiducialPartId; }
    public void setFiducialPartId(String value) {
        fiducialPartId = value;
        invalidateCalibration();
    }
    public Part getFiducialPart() {
        return fiducialPartId == null || fiducialPartId.isEmpty()
                ? null : Configuration.get().getPart(fiducialPartId);
    }
    public void setFiducialPart(Part value) {
        Part old = getFiducialPart();
        fiducialPartId = value == null ? "" : value.getId();
        invalidateCalibration();
        firePropertyChange("fiducialPart", old, value);
    }
    public boolean isPeriodicCalibration() { return periodicCalibration; }
    public void setPeriodicCalibration(boolean value) { periodicCalibration = value; }
    public int getCalibrationIntervalMinutes() { return calibrationIntervalMinutes; }
    public void setCalibrationIntervalMinutes(int value) { calibrationIntervalMinutes = value; }
    public double getMaxFiducialShiftMm() { return maxFiducialShiftMm; }
    public void setMaxFiducialShiftMm(double value) { maxFiducialShiftMm = value; }
    public double getMaxBasisChangePercent() { return maxBasisChangePercent; }
    public void setMaxBasisChangePercent(double value) { maxBasisChangePercent = value; }
    public boolean isPartVisionEnabled() { return partVisionEnabled; }
    public void setPartVisionEnabled(boolean value) { partVisionEnabled = value; }
    public PartVisionMode getPartVisionMode() {
        return partVisionMode == null ? PartVisionMode.EveryFeed : partVisionMode;
    }
    public void setPartVisionMode(PartVisionMode value) {
        partVisionMode = value == null ? PartVisionMode.EveryFeed : value;
        partVisionDoneForJob = false;
    }
    public double getMaxPartShiftMm() { return maxPartShiftMm; }
    public void setMaxPartShiftMm(double value) { maxPartShiftMm = value; }
    public CvPipeline getPartPipeline() { return partPipeline; }
    public void resetPartPipeline() { partPipeline = ReferenceLoosePartFeeder.createDefaultPipeline(); }
    public Instant getLastCalibrationTime() { return lastCalibrationTime; }
    public String getLastCalibrationError() { return lastCalibrationError; }
}
