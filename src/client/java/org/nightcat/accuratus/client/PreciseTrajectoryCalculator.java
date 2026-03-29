package org.nightcat.accuratus.client;

import java.util.Scanner;
import java.util.Arrays;
import java.util.ArrayList;

public class PreciseTrajectoryCalculator {
    private static final double DRAG = 0.99;          // velocity multiplier per tick
    private static final double GRAVITY = 0.05;       // downward acceleration per tick
    private static final double F = 1 - DRAG;         // = 0.01
    private static final double G_OVER_F = GRAVITY / F;
    private static final double LN_DRAG = Math.log(DRAG);

    public static double[] calculate(double startX, double startY, double startZ,
                                     double targetX, double targetY, double targetZ,
                                     double speed) {
        double dx = targetX - startX;
        double dz = targetZ - startZ;
        double d = Math.hypot(dx, dz);                // horizontal distance
        double dy = targetY - startY;

        // Yaw angle (Minecraft standard: 0 = +Z, -90 = +X, 90 = -X, ±180 = -Z)
        if (d < 1e-8) {
            // No horizontal displacement -> cannot use the trajectory formula
            return new double[]{-1, -1, -1};
        }
        double yawRad = -Math.atan2(dx, dz);
        double yawDeg = Math.toDegrees(yawRad);

        // Maximum possible horizontal range (when cosθ = 1)
        if (d > speed / F) {
            return new double[]{-1, -1, -1};
        }

        // Feasible pitch range (θ = elevation above horizontal, positive up)
        double thetaMax = Math.acos(d * F / speed);
        double theta0 = Math.atan2(dy, d);            // line‑of‑sight angle
        if (theta0 > thetaMax) theta0 = thetaMax;
        if (theta0 < -thetaMax) theta0 = -thetaMax;

        // Function that returns vertical displacement after traveling horizontal distance d
        java.util.function.DoubleUnaryOperator yAtDist = (theta) -> {
            double cosTheta = Math.cos(theta);
            double sinTheta = Math.sin(theta);
            double A = speed * cosTheta;
            if (A <= 0) return Double.NEGATIVE_INFINITY;
            double arg = 1 - (d * F) / A;
            if (arg <= 0) return Double.NEGATIVE_INFINITY;
            double n = Math.log(arg) / LN_DRAG;
            return (speed * sinTheta + G_OVER_F) * (d / A) - (GRAVITY * n) / F;
        };

        // Binary lifting: start from line‑of‑sight and increase θ (decrease pitch)
        double step = Math.toRadians(1.0);            // at most 5° per step, here 1° for safety
        double theta = theta0;
        double yCurr = yAtDist.applyAsDouble(theta);
        if (Double.isInfinite(yCurr) || Double.isNaN(yCurr)) {
            return new double[]{-1, -1, -1};
        }

        // Already accurate enough
        if (Math.abs(yCurr - dy) < 1e-4) {
            double time = Math.log(1 - (d * F) / (speed * Math.cos(theta))) / LN_DRAG;
            return new double[]{yawDeg, -Math.toDegrees(theta), time};
        }

        boolean needHigherY = yCurr < dy;
        double prevTheta = theta;
        double prevY = yCurr;
        boolean found = false;
        double solutionTheta = 0;

        while (theta <= thetaMax + 1e-8) {
            theta += step;
            if (theta > thetaMax) theta = thetaMax;
            yCurr = yAtDist.applyAsDouble(theta);
            if (Double.isInfinite(yCurr) || Double.isNaN(yCurr)) break;

            // Check for crossing the target vertical displacement
            if ((needHigherY && yCurr >= dy) || (!needHigherY && yCurr <= dy)) {
                // Binary search within [prevTheta, theta]
                double low = prevTheta, high = theta;
                for (int iter = 0; iter < 60; iter++) {
                    double mid = (low + high) / 2;
                    double yMid = yAtDist.applyAsDouble(mid);
                    if (Double.isInfinite(yMid)) yMid = Double.NEGATIVE_INFINITY;
                    if (yMid < dy) low = mid;
                    else high = mid;
                }
                solutionTheta = (low + high) / 2;
                found = true;
                break;
            }
            prevTheta = theta;
            prevY = yCurr;
            if (theta >= thetaMax) break;
        }

        if (!found) {
            return new double[]{-1, -1, -1};
        }

        // Pitch angle (positive down) = -θ
        double pitchDeg = -Math.toDegrees(solutionTheta);
        double time = Math.log(1 - (d * F) / (speed * Math.cos(solutionTheta))) / LN_DRAG;
        return new double[]{yawDeg, pitchDeg, time};
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
        double[] ans = calculate(px, py, pz, tx, ty, tz, 3);
        long end = System.currentTimeMillis();
        System.out.println("Time taken: " + (end - start) + " ms");
        System.out.println(Arrays.toString(ans));
    }
}
