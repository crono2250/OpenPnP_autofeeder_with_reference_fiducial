/*
 * Copyright (C) 2026 crono2250
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.openpnp.machine.reference.feeder;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import org.openpnp.model.Configuration;
import org.openpnp.model.LengthUnit;
import org.openpnp.model.Location;
import org.openpnp.spi.Feeder;
import org.openpnp.spi.Machine;
import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Element;
import org.simpleframework.xml.ElementList;
import org.simpleframework.xml.Root;

/** Machine-wide, persistent fiducial definitions and installation-surface assignments. */
@Root
public class FiducialCatalog {
    private static final String PROPERTY = "ReferenceFiducialAutoFeeder.fiducialCatalog";

    @ElementList(required = false)
    private List<Mark> marks = new ArrayList<>();
    @ElementList(required = false)
    private List<Surface> surfaces = new ArrayList<>();
    private transient List<Runnable> listeners;

    public static synchronized FiducialCatalog get() {
        Machine machine = Configuration.get().getMachine();
        FiducialCatalog catalog = (FiducialCatalog) machine.getProperty(PROPERTY);
        if (catalog == null) {
            catalog = new FiducialCatalog();
            machine.setProperty(PROPERTY, catalog);
        }
        return catalog;
    }

    private List<Runnable> listeners() {
        if (listeners == null) {
            listeners = new CopyOnWriteArrayList<>();
        }
        return listeners;
    }

    public void addListener(Runnable listener) { listeners().add(listener); }
    public void removeListener(Runnable listener) { listeners().remove(listener); }

    private void changed() {
        for (Feeder feeder : Configuration.get().getMachine().getFeeders()) {
            if (feeder instanceof ReferenceFiducialAutoFeeder) {
                ((ReferenceFiducialAutoFeeder) feeder).invalidateCalibration();
            }
        }
        for (Runnable listener : listeners()) {
            listener.run();
        }
    }

    public synchronized List<Mark> getMarks() { return new ArrayList<>(marks); }
    public synchronized List<Surface> getSurfaces() { return new ArrayList<>(surfaces); }

    public synchronized Mark getMark(String id) {
        if (id == null) {
            return null;
        }
        for (Mark mark : marks) {
            if (id.equals(mark.id)) {
                return mark;
            }
        }
        return null;
    }

    public synchronized Surface getSurface(String id) {
        if (id == null) {
            return null;
        }
        for (Surface surface : surfaces) {
            if (id.equals(surface.id)) {
                return surface;
            }
        }
        return null;
    }

    private static Location xy(Location location) {
        if (location == null) {
            throw new IllegalArgumentException("Capture a camera position first.");
        }
        Location mm = location.convertToUnits(LengthUnit.Millimeters);
        if (!Double.isFinite(mm.getX()) || !Double.isFinite(mm.getY())) {
            throw new IllegalArgumentException("Fiducial X and Y must be finite.");
        }
        return new Location(LengthUnit.Millimeters, mm.getX(), mm.getY(), 0, 0);
    }

    private static String name(Location location) {
        return String.format(Locale.ROOT, "Fid_%.3f_%.3f", location.getX(), location.getY());
    }

    private void ensureUniqueName(String name, String exceptId) {
        for (Mark mark : marks) {
            if (!Objects.equals(mark.id, exceptId) && name.equals(mark.name)) {
                throw new IllegalArgumentException("A fiducial with these rounded XY coordinates already exists: " + name);
            }
        }
    }

    public synchronized Mark addMark(Location position) {
        Location location = xy(position);
        String name = name(location);
        ensureUniqueName(name, null);
        Mark mark = new Mark(UUID.randomUUID().toString(), name, location);
        marks.add(mark);
        changed();
        return mark;
    }

    public synchronized void updateMark(String id, Location position) {
        Mark mark = getMark(id);
        if (mark == null) {
            throw new IllegalArgumentException("Select a saved fiducial first.");
        }
        Location location = xy(position);
        String name = name(location);
        ensureUniqueName(name, id);
        mark.location = location;
        mark.name = name;
        changed();
    }

    public synchronized void removeMark(String id) {
        Mark mark = getMark(id);
        if (mark == null) {
            return;
        }
        marks.remove(mark);
        for (Surface surface : surfaces) {
            if (id.equals(surface.aId)) {
                surface.aId = null;
            }
            if (id.equals(surface.bId)) {
                surface.bId = null;
            }
            if (id.equals(surface.cId)) {
                surface.cId = null;
            }
        }
        changed();
    }

    public synchronized Surface addSurface(String requestedName) {
        String name = requestedName == null ? "" : requestedName.trim();
        if (name.isEmpty()) {
            throw new IllegalArgumentException("Enter an installation surface name.");
        }
        for (Surface surface : surfaces) {
            if (surface.name.equalsIgnoreCase(name)) {
                throw new IllegalArgumentException("An installation surface with that name already exists.");
            }
        }
        Surface surface = new Surface(UUID.randomUUID().toString(), name);
        surfaces.add(surface);
        changed();
        return surface;
    }

    public synchronized void renameSurface(String id, String requestedName) {
        Surface surface = getSurface(id);
        if (surface == null) {
            throw new IllegalArgumentException("Select an installation surface first.");
        }
        String name = requestedName == null ? "" : requestedName.trim();
        if (name.isEmpty()) {
            throw new IllegalArgumentException("Enter an installation surface name.");
        }
        for (Surface other : surfaces) {
            if (other != surface && other.name.equalsIgnoreCase(name)) {
                throw new IllegalArgumentException("An installation surface with that name already exists.");
            }
        }
        surface.name = name;
        changed();
    }

    public synchronized void removeSurface(String id) {
        Surface surface = getSurface(id);
        if (surface == null) {
            return;
        }
        surfaces.remove(surface);
        // Keep feeder IDs pointing at the missing surface until the operator selects a replacement.
        // A silent fallback could send the camera to a different installation face.
        changed();
    }

    public synchronized void setMark(Surface surface, int index, String markId) {
        if (getSurface(surface.id) == null) {
            throw new IllegalArgumentException("Installation surface was deleted.");
        }
        if (markId != null && getMark(markId) == null) {
            throw new IllegalArgumentException("Fiducial was deleted.");
        }
        if (index == 0) {
            surface.aId = markId;
        }
        else if (index == 1) {
            surface.bId = markId;
        }
        else if (index == 2) {
            surface.cId = markId;
        }
        else {
            throw new IllegalArgumentException("Invalid fiducial slot.");
        }
        changed();
    }

    public synchronized void setCUseMachineOrigin(Surface surface, boolean value) {
        if (getSurface(surface.id) == null) {
            throw new IllegalArgumentException("Installation surface was deleted.");
        }
        surface.cUseMachineOrigin = value;
        changed();
    }

    /** Converts old per-feeder coordinates once, reusing equal marks and equal surfaces. */
    public synchronized Surface migrate(Location a, Location b, Location c, boolean origin) {
        boolean empty = isZero(a) && isZero(b) && isZero(c) && !origin;
        String aId = empty ? null : findOrAdd(a);
        String bId = empty ? null : findOrAdd(b);
        String cId = empty ? null : findOrAdd(c);
        for (Surface surface : surfaces) {
            if (Objects.equals(aId, surface.aId) && Objects.equals(bId, surface.bId)
                    && Objects.equals(cId, surface.cId) && origin == surface.cUseMachineOrigin) {
                return surface;
            }
        }
        int number = 1;
        boolean used;
        do {
            String candidate = "Surface " + number++;
            used = false;
            for (Surface existing : surfaces) {
                if (existing.name.equalsIgnoreCase(candidate)) {
                    used = true;
                }
            }
        } while (used);
        Surface surface = addSurface("Surface " + (number - 1));
        surface.aId = aId;
        surface.bId = bId;
        surface.cId = cId;
        surface.cUseMachineOrigin = origin;
        changed();
        return surface;
    }

    private static boolean isZero(Location location) {
        if (location == null) {
            return true;
        }
        Location mm = location.convertToUnits(LengthUnit.Millimeters);
        return mm.getX() == 0 && mm.getY() == 0;
    }

    private String findOrAdd(Location position) {
        if (position == null) {
            return null;
        }
        Location location = xy(position);
        String target = name(location);
        for (Mark mark : marks) {
            if (mark.name.equals(target)) {
                return mark.id;
            }
        }
        return addMark(location).id;
    }

    @Root
    public static class Mark {
        @Attribute private String id;
        @Attribute private String name;
        @Element private Location location;

        public Mark() { }
        private Mark(String id, String name, Location location) {
            this.id = id;
            this.name = name;
            this.location = location;
        }
        public String getId() { return id; }
        public String getName() { return name; }
        public Location getLocation() { return location; }
        @Override public String toString() { return name; }
    }

    @Root
    public static class Surface {
        @Attribute private String id;
        @Attribute private String name;
        @Attribute(required = false) private String aId;
        @Attribute(required = false) private String bId;
        @Attribute(required = false) private String cId;
        @Attribute(required = false) private boolean cUseMachineOrigin;

        public Surface() { }
        private Surface(String id, String name) {
            this.id = id;
            this.name = name;
        }
        public String getId() { return id; }
        public String getName() { return name; }
        public String getMarkId(int index) {
            return index == 0 ? aId : index == 1 ? bId : index == 2 ? cId : null;
        }
        public boolean isCUseMachineOrigin() { return cUseMachineOrigin; }
        @Override public String toString() { return name; }
    }
}
