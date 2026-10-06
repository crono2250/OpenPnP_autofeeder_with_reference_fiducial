/*
 * Copyright (C) 2026 crono2250
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.openpnp.machine.reference.feeder;

/** A two-dimensional affine transform determined by exactly three point pairs. */
public final class ThreePointAffine {
    private final double m00, m01, m02, m10, m11, m12;

    public ThreePointAffine(double[][] nominal, double[][] measured) {
        if (nominal.length != 3 || measured.length != 3) {
            throw new IllegalArgumentException("Exactly three point pairs are required.");
        }
        for (int i = 0; i < 3; i++) {
            if (nominal[i].length != 2 || measured[i].length != 2) {
                throw new IllegalArgumentException("Each point must have X and Y.");
            }
            for (int j = 0; j < 2; j++) {
                if (!Double.isFinite(nominal[i][j]) || !Double.isFinite(measured[i][j])) {
                    throw new IllegalArgumentException("Fiducial coordinates must be finite.");
                }
            }
        }
        double ax = nominal[1][0] - nominal[0][0];
        double ay = nominal[1][1] - nominal[0][1];
        double bx = nominal[2][0] - nominal[0][0];
        double by = nominal[2][1] - nominal[0][1];
        double det = ax * by - ay * bx;
        if (Math.abs(det) < 1e-6) {
            throw new IllegalArgumentException("Fiducials are collinear or too close together.");
        }
        double ux = measured[1][0] - measured[0][0];
        double uy = measured[1][1] - measured[0][1];
        double vx = measured[2][0] - measured[0][0];
        double vy = measured[2][1] - measured[0][1];
        m00 = (ux * by - vx * ay) / det;
        m01 = (vx * ax - ux * bx) / det;
        m10 = (uy * by - vy * ay) / det;
        m11 = (vy * ax - uy * bx) / det;
        m02 = measured[0][0] - m00 * nominal[0][0] - m01 * nominal[0][1];
        m12 = measured[0][1] - m10 * nominal[0][0] - m11 * nominal[0][1];
    }

    public double[] transform(double x, double y) {
        return new double[] {m00 * x + m01 * y + m02, m10 * x + m11 * y + m12};
    }

    public double transformAngle(double degrees) {
        double radians = Math.toRadians(degrees);
        return Math.toDegrees(Math.atan2(m10 * Math.cos(radians) + m11 * Math.sin(radians),
                m00 * Math.cos(radians) + m01 * Math.sin(radians)));
    }

    /** Maximum fractional change of either basis vector; rejects implausible measurements. */
    public double maximumBasisChange() {
        return Math.max(Math.hypot(m00 - 1, m10), Math.hypot(m01, m11 - 1));
    }
}
