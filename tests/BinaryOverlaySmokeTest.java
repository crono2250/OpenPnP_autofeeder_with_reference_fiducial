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
        java.awt.Component wizard = (java.awt.Component) instance.getConfigurationWizard();
        ((org.openpnp.gui.support.AbstractConfigurationWizard) wizard).createBindings();
        // One belongs to the base feeder pick location; three are added for the fiducials.
        if (count(wizard, org.openpnp.gui.components.LocationButtonsPanel.class) != 4) {
            throw new AssertionError("The three fiducial location controls are missing.");
        }
        if (countPartsCombos(wizard) < 2 || countModeCombos(wizard) != 1) {
            throw new AssertionError("The fiducial Part and recognition mode dropdowns are missing.");
        }
        javax.swing.JCheckBox origin = findOriginCheckBox(wizard);
        if (origin == null || !origin.isSelected()
                || countDisabledLocationControls(wizard) != 1
                || countVisibleOriginFields(wizard) != 2) {
            throw new AssertionError("The machine-origin option did not disable fid_C X/Y and controls.");
        }
        origin.setSelected(false);
        if (countDisabledLocationControls(wizard) != 0 || countVisibleOriginFields(wizard) != 0) {
            throw new AssertionError("Custom fid_C controls did not become enabled.");
        }
        instance.setFidCUseMachineOrigin(false);
        assertXY(instance.getEffectiveFidCLocation(), 12, 34);
        System.out.println("BinaryOverlaySmokeTest passed");
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
