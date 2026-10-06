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
        java.awt.Component wizard = (java.awt.Component) instance.getConfigurationWizard();
        ((org.openpnp.gui.support.AbstractConfigurationWizard) wizard).createBindings();
        // One belongs to the base feeder pick location; three are added for the fiducials.
        if (count(wizard, org.openpnp.gui.components.LocationButtonsPanel.class) != 4) {
            throw new AssertionError("The three fiducial location controls are missing.");
        }
        if (countPartsCombos(wizard) < 2 || countModeCombos(wizard) != 1) {
            throw new AssertionError("The fiducial Part and recognition mode dropdowns are missing.");
        }
        System.out.println("BinaryOverlaySmokeTest passed");
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
