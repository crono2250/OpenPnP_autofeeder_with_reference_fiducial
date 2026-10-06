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
        System.out.println("BinaryOverlaySmokeTest passed");
    }
}
