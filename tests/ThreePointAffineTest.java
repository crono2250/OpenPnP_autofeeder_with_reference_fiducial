import org.openpnp.machine.reference.feeder.ThreePointAffine;

/** Run with javac/java; no OpenPnP runtime or camera required. */
public class ThreePointAffineTest {
    private static void near(double expected, double actual) {
        if (Math.abs(expected - actual) > 1e-9) {
            throw new AssertionError("Expected " + expected + ", got " + actual);
        }
    }

    public static void main(String[] args) {
        double[][] nominal = {{0, 0}, {100, 0}, {100, 100}};
        double[][] observed = {{0.2, -0.3}, {100.3, -0.2}, {100.1, 99.9}};
        ThreePointAffine affine = new ThreePointAffine(nominal, observed);
        for (int i = 0; i < 3; i++) {
            double[] point = affine.transform(nominal[i][0], nominal[i][1]);
            near(observed[i][0], point[0]);
            near(observed[i][1], point[1]);
        }
        double[] pick = affine.transform(50, 50);
        near(50.15, pick[0]);
        near(49.8, pick[1]);
        try {
            new ThreePointAffine(new double[][] {{0, 0}, {1, 1}, {2, 2}}, observed);
            throw new AssertionError("Collinear points were accepted.");
        }
        catch (IllegalArgumentException expected) {
            // Expected.
        }
        System.out.println("ThreePointAffineTest passed");
    }
}
