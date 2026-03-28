package org.nightcat.accuratus.client;

import java.util.Scanner;
import java.util.Arrays;
import java.util.ArrayList;

public class TrajectoryCalculator {

    // Initial speed in block per tick
    private static final double SPEED = 3.0;
    // velocity multiplier per tick
    private static final double DRAG = 0.99;
    // vertical acceleration: -0.05 blocks/tick^2
    private static final double GRAVITY = -0.05;
    // pitch with largest range
    private static final double PITCH_MAX_RANGE = -38.5;
    // about 1.8675
    private static final double VY_MAX = SPEED * Math.sin(Math.toRadians(-PITCH_MAX_RANGE));


    // Returns the initial pitch and yaw (Minecraft conventions) and the flight time
    // that bring the arrow as close as possible to the target, respecting
    // pitch > -38.5°.

    public static double[] findAnglesAndTicks(double startX, double startY, double startZ,
                                              double targetX, double targetY, double targetZ) {
        int maxT = 2000;
        double[] pow99 = new double[maxT + 1];
        pow99[0] = 1.0;
        for (int i = 1; i <= maxT; i++) {
            pow99[i] = pow99[i - 1] * DRAG;
        }

        double bestDist = Double.POSITIVE_INFINITY;
        double bestVx = 0, bestVy = 0, bestVz = 0;
        int bestT = -1;

        for (int t = 1; t <= maxT; t++) {
            // factor = 100 * (1 - 0.99^t)
            double factor = 100.0 * (1.0 - pow99[t]);

            // Target vector after accounting for gravity and drag
            double bX = targetX - startX;
            double bZ = targetZ - startZ;
            double bY = targetY - startY + 5.0 * t - 500.0 * (1.0 - pow99[t]);

            // Unconstrained optimal velocity (would hit target exactly)
            double vx_opt = bX / factor;
            double vy_opt = bY / factor;
            double vz_opt = bZ / factor;

            double norm2 = vx_opt * vx_opt + vy_opt * vy_opt + vz_opt * vz_opt;
            double norm = Math.sqrt(norm2);

            double vx, vy, vz;
            if (norm < 1e-12) {
                // v_opt is zero: any direction on the sphere works.
                // Choose a direction that satisfies the pitch constraint if possible.
                if (SPEED <= VY_MAX) {
                    vx = 0; vy = SPEED; vz = 0; // straight up – violates pitch?
                } else {
                    vy = VY_MAX;
                    double r = Math.sqrt(SPEED * SPEED - vy * vy);
                    vx = r; vz = 0; // horizontal component
                }
            } else {
                // Project v_opt onto the sphere of radius SPEED
                double scale = SPEED / norm;
                vx = vx_opt * scale;
                vy = vy_opt * scale;
                vz = vz_opt * scale;
            }

            // Enforce pitch constraint: vy <= VY_MAX
            if (vy > VY_MAX) {
                // The best feasible point lies on the intersection of the sphere
                // and the plane vy = VY_MAX.
                double r = Math.sqrt(SPEED * SPEED - VY_MAX * VY_MAX);
                double horiz = Math.hypot(vx_opt, vz_opt);
                if (horiz < 1e-12) {
                    vx = r;
                    vz = 0;
                } else {
                    vx = r * (vx_opt / horiz);
                    vz = r * (vz_opt / horiz);
                }
                vy = VY_MAX;
            }

            // Compute distance after t ticks
            double dx = factor * vx - bX;
            double dy = factor * vy - bY;
            double dz = factor * vz - bZ;
            double dist = Math.hypot(Math.hypot(dx, dy), dz);

            if (dist < bestDist - 1e-12) {
                bestDist = dist;
                bestVx = vx;
                bestVy = vy;
                bestVz = vz;
                bestT = t;
            }
        }

        // Convert to Minecraft angles
        double pitch = -Math.toDegrees(Math.asin(bestVy / SPEED));
        double yaw = -Math.toDegrees(Math.atan2(bestVx, bestVz));
        return new double[]{yaw, pitch, bestT};
    }


    public static void main(String args[]) {
        Scanner sysin = new Scanner(System.in);
        double px = sysin.nextDouble();
        double py = sysin.nextDouble();
        double pz = sysin.nextDouble();
        double tx = sysin.nextDouble();
        double ty = sysin.nextDouble();
        double tz = sysin.nextDouble();
        long start = System.currentTimeMillis();
        double[] ans = findAnglesAndTicks(px, py, pz, tx, ty, tz);
        long end = System.currentTimeMillis();
        System.out.println("Time taken: " + (end - start) + " ms");
        System.out.println("Yaw angle, Pitch angle, Expected Tick");
        System.out.println(Arrays.toString(ans));
    }
}