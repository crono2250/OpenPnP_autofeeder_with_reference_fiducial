/** Checks class loading with the exact OpenPnP binary and the overlay first on the classpath. */
public class BinaryOverlaySmokeTest {
    public static void main(String[] args) throws Exception {
        Class<?> machine = Class.forName("org.openpnp.machine.reference.ReferenceMachine");
        Class<?> feeder = Class.forName("org.openpnp.machine.reference.feeder.ReferenceFiducialAutoFeeder");
        String machineJar = machine.getProtectionDomain().getCodeSource().getLocation().toString();
        String feederJar = feeder.getProtectionDomain().getCodeSource().getLocation().toString();
        if (!machineJar.contains("openpnp-fiducial-auto-feeder-50dcdce.jar") ||
                !feederJar.equals(machineJar)) {
            throw new AssertionError("The overlay JAR did not take precedence: " + machineJar);
        }
        org.openpnp.model.Configuration.initialize();
        org.openpnp.machine.reference.ReferenceMachine referenceMachine =
                new org.openpnp.machine.reference.ReferenceMachine();
        if (!referenceMachine.getCompatibleFeederClasses().contains(feeder)) {
            throw new AssertionError("The custom feeder is not registered in ReferenceMachine.");
        }
        org.openpnp.model.Configuration.get().setMachine(referenceMachine);
        org.openpnp.machine.reference.feeder.ReferenceFiducialAutoFeeder instance =
                new org.openpnp.machine.reference.feeder.ReferenceFiducialAutoFeeder();
        instance.setFidCLocation(new org.openpnp.model.Location(
                org.openpnp.model.LengthUnit.Millimeters, 12, 34, 7, 15));
        instance.setFidCUseMachineOrigin(true);
        assertXY(instance.getEffectiveFidCLocation(), 0, 0);
        org.openpnp.machine.reference.feeder.FiducialCatalog catalog =
                org.openpnp.machine.reference.feeder.FiducialCatalog.get();
        org.openpnp.spi.PropertySheetHolder.PropertySheet[] sheets = instance.getPropertySheets();
        if (sheets.length != 2 || !"Configuration".equals(sheets[0].getPropertySheetTitle())
                || !"Calibration".equals(sheets[1].getPropertySheetTitle())) {
            throw new AssertionError("Configuration and Calibration must be separate feeder tabs.");
        }
        java.awt.Component configuration = sheets[0].getPropertySheetPanel();
        java.awt.Component calibration = sheets[1].getPropertySheetPanel();
        if (!(configuration instanceof org.openpnp.machine.reference.feeder.wizards.ReferenceAutoFeederConfigurationWizard)
                || count(configuration, org.openpnp.gui.components.LocationButtonsPanel.class) != 1
                || countPartsCombos(configuration) != 1
                || countActuatorCombos(configuration) != 2
                || count(configuration, javax.swing.JCheckBox.class) != 2) {
            throw new AssertionError("The standard ReferenceAutoFeeder controls are incomplete.");
        }
        if (!(calibration instanceof org.openpnp.machine.reference.feeder.wizards.ReferenceFiducialAutoFeederCalibrationWizard)) {
            throw new AssertionError("The Calibration tab has the wrong wizard.");
        }
        org.openpnp.machine.reference.feeder.FiducialCatalog.Surface surface =
                instance.getOrCreateSurface();
        if (surface == null || !surface.isCUseMachineOrigin()) {
            throw new AssertionError("Legacy fiducials were not migrated to a shared surface.");
        }
        if (count(calibration, org.openpnp.gui.components.LocationButtonsPanel.class) != 3) {
            throw new AssertionError("The three fiducial location controls are missing.");
        }
        if (countPartsCombos(calibration) != 1 || countModeCombos(calibration) != 1) {
            throw new AssertionError("The fiducial Part and recognition mode dropdowns are missing.");
        }
        if (countSurfaceCombos(calibration) != 1 || countMarkCombos(calibration) != 3) {
            throw new AssertionError("The surface and saved fiducial dropdowns are missing.");
        }
        javax.swing.JCheckBox origin = findOriginCheckBox(calibration);
        if (origin == null || !origin.isSelected()
                || countDisabledLocationControls(calibration) != 1
                || countVisibleOriginFields(calibration) != 2) {
            throw new AssertionError("The machine-origin option did not disable fid_C X/Y and controls.");
        }
        origin.setSelected(false);
        if (countDisabledLocationControls(calibration) != 0
                || countVisibleOriginFields(calibration) != 0) {
            throw new AssertionError("Custom fid_C controls did not become enabled.");
        }
        assertXY(instance.getEffectiveFidCLocation(), 12, 34);
        org.openpnp.machine.reference.feeder.FiducialCatalog.Mark mark = catalog.addMark(
                new org.openpnp.model.Location(org.openpnp.model.LengthUnit.Millimeters,
                        51.25, -7.5, 0, 0));
        catalog.setMark(surface, 0, mark.getId());
        if (!"Fid_51.250_-7.500".equals(mark.getName())) {
            throw new AssertionError("Saved fiducial name does not use the XY format.");
        }
        catalog.updateMark(mark.getId(), new org.openpnp.model.Location(
                org.openpnp.model.LengthUnit.Millimeters, 52, -8, 0, 0));
        if (!"Fid_52.000_-8.000".equals(mark.getName())
                || !mark.getId().equals(surface.getMarkId(0))) {
            throw new AssertionError("Coordinate edits broke the stable fiducial reference.");
        }
        catalog.removeMark(mark.getId());
        if (surface.getMarkId(0) != null) {
            throw new AssertionError("Deleting a fiducial did not clear surface references.");
        }
        org.openpnp.machine.reference.feeder.ReferenceFiducialAutoFeeder another =
                new org.openpnp.machine.reference.feeder.ReferenceFiducialAutoFeeder();
        if (!surface.getId().equals(another.getOrCreateSurface().getId())) {
            throw new AssertionError("A second feeder did not reuse the shared surface.");
        }
        org.openpnp.machine.reference.feeder.FiducialCatalog.Surface second =
                catalog.addSurface("Other face");
        another.setFiducialSurfaceId(second.getId());
        if (!second.getId().equals(another.getOrCreateSurface().getId())) {
            throw new AssertionError("The feeder could not select another installation surface.");
        }
        java.io.StringWriter xml = new java.io.StringWriter();
        org.simpleframework.xml.Serializer serializer = new org.simpleframework.xml.core.Persister();
        serializer.write(catalog, xml);
        org.openpnp.machine.reference.feeder.FiducialCatalog restored = serializer.read(
                org.openpnp.machine.reference.feeder.FiducialCatalog.class, xml.toString());
        if (restored.getSurfaces().size() != 2 || restored.getMarks().isEmpty()) {
            throw new AssertionError("The shared catalog did not survive XML serialization.");
        }
        java.io.StringWriter machineXml = new java.io.StringWriter();
        serializer.write(referenceMachine, machineXml);
        if (!machineXml.toString().contains("ReferenceFiducialAutoFeeder.fiducialCatalog")
                || !machineXml.toString().contains("Other face")) {
            throw new AssertionError("The shared catalog was not saved in machine.xml.");
        }
        org.openpnp.machine.reference.ReferenceMachine restoredMachine = serializer.read(
                org.openpnp.machine.reference.ReferenceMachine.class, machineXml.toString());
        Object savedCatalog = restoredMachine.getProperty(
                "ReferenceFiducialAutoFeeder.fiducialCatalog");
        if (!(savedCatalog instanceof org.openpnp.machine.reference.feeder.FiducialCatalog)
                || ((org.openpnp.machine.reference.feeder.FiducialCatalog) savedCatalog)
                        .getSurfaces().size() != 2) {
            throw new AssertionError("The shared catalog did not reload from machine.xml.");
        }
        catalog.removeSurface(second.getId());
        if (another.getOrCreateSurface() != null) {
            throw new AssertionError("A deleted surface was silently replaced for its feeder.");
        }
        System.out.println("BinaryOverlaySmokeTest passed");
    }

    private static int countSurfaceCombos(java.awt.Component root) {
        return countCatalogCombos(root, true);
    }

    private static int countMarkCombos(java.awt.Component root) {
        return countCatalogCombos(root, false);
    }

    private static int countCatalogCombos(java.awt.Component root, boolean surface) {
        int total = 0;
        if (root instanceof javax.swing.JComboBox) {
            javax.swing.JComboBox<?> combo = (javax.swing.JComboBox<?>) root;
            for (int i = 0; i < combo.getItemCount(); i++) {
                Object item = combo.getItemAt(i);
                if (surface && item instanceof org.openpnp.machine.reference.feeder.FiducialCatalog.Surface
                        || !surface && item instanceof org.openpnp.machine.reference.feeder.FiducialCatalog.Mark) {
                    total = 1;
                    break;
                }
            }
        }
        if (root instanceof java.awt.Container) {
            for (java.awt.Component child : ((java.awt.Container) root).getComponents()) {
                total += countCatalogCombos(child, surface);
            }
        }
        return total;
    }

    private static void assertXY(org.openpnp.model.Location location, double x, double y) {
        if (location.getX() != x || location.getY() != y) {
            throw new AssertionError("Unexpected effective fid_C coordinates: " + location);
        }
    }

    private static javax.swing.JCheckBox findOriginCheckBox(java.awt.Component root) {
        if (root instanceof javax.swing.JCheckBox
                && ((javax.swing.JCheckBox) root).getText().startsWith("Use machine origin")) {
            return (javax.swing.JCheckBox) root;
        }
        if (root instanceof java.awt.Container) {
            for (java.awt.Component child : ((java.awt.Container) root).getComponents()) {
                javax.swing.JCheckBox found = findOriginCheckBox(child);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static int countDisabledLocationControls(java.awt.Component root) {
        int total = root instanceof org.openpnp.gui.components.LocationButtonsPanel
                && !root.isEnabled() ? 1 : 0;
        if (root instanceof java.awt.Container) {
            for (java.awt.Component child : ((java.awt.Container) root).getComponents()) {
                total += countDisabledLocationControls(child);
            }
        }
        return total;
    }

    private static int countVisibleOriginFields(java.awt.Component root) {
        int total = root instanceof javax.swing.JTextField && root.isVisible() && !root.isEnabled()
                && "0.000".equals(((javax.swing.JTextField) root).getText()) ? 1 : 0;
        if (root instanceof java.awt.Container) {
            for (java.awt.Component child : ((java.awt.Container) root).getComponents()) {
                total += countVisibleOriginFields(child);
            }
        }
        return total;
    }

    private static int count(java.awt.Component root, Class<?> type) {
        int total = type.isInstance(root) ? 1 : 0;
        if (root instanceof java.awt.Container) {
            for (java.awt.Component child : ((java.awt.Container) root).getComponents()) {
                total += count(child, type);
            }
        }
        return total;
    }

    private static int countPartsCombos(java.awt.Component root) {
        int total = root instanceof javax.swing.JComboBox
                && ((javax.swing.JComboBox<?>) root).getModel()
                        instanceof org.openpnp.gui.support.PartsComboBoxModel ? 1 : 0;
        if (root instanceof java.awt.Container) {
            for (java.awt.Component child : ((java.awt.Container) root).getComponents()) {
                total += countPartsCombos(child);
            }
        }
        return total;
    }

    private static int countActuatorCombos(java.awt.Component root) {
        int total = root instanceof javax.swing.JComboBox
                && ((javax.swing.JComboBox<?>) root).getModel()
                        instanceof org.openpnp.gui.support.ActuatorsComboBoxModel ? 1 : 0;
        if (root instanceof java.awt.Container) {
            for (java.awt.Component child : ((java.awt.Container) root).getComponents()) {
                total += countActuatorCombos(child);
            }
        }
        return total;
    }

    private static int countModeCombos(java.awt.Component root) {
        int total = 0;
        if (root instanceof javax.swing.JComboBox) {
            javax.swing.JComboBox<?> combo = (javax.swing.JComboBox<?>) root;
            if (combo.getItemCount() == 3 && combo.getItemAt(0)
                    instanceof org.openpnp.machine.reference.feeder.ReferenceFiducialAutoFeeder.PartVisionMode) {
                total = 1;
            }
        }
        if (root instanceof java.awt.Container) {
            for (java.awt.Component child : ((java.awt.Container) root).getComponents()) {
                total += countModeCombos(child);
            }
        }
        return total;
    }
}
