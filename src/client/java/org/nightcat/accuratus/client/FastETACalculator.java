package org.nightcat.accuratus.client;

import java.util.Scanner;
import java.util.Arrays;
import java.util.ArrayList;

public class FastETACalculator {

    private static final double DRAG = 0.99;
    private static final double GRAVITY = 0.05;               // magnitude of gravity
    private static final double ONE_MINUS_DRAG = 1.0 - DRAG;  // = 0.01
    private static final double G_PRIME = GRAVITY / ONE_MINUS_DRAG; // = 5.0

    /**
     * Computes the earliest time (in ticks) at which an arrow, shot with the given
     * initial speed, can hit the target. The arrow's motion follows the discrete
     * updates: position += velocity; velocity = (0.99*vx, 0.99*vy - 0.05, 0.99*vz)
     * each tick. The method considers continuous (fractional) time as well.
     *
     * @param x0    initial x coordinate
     * @param y0    initial y coordinate
     * @param z0    initial z coordinate
     * @param xt    target x coordinate
     * @param yt    target y coordinate
     * @param zt    target z coordinate
     * @param speed initial speed of the arrow (must be > 0)
     * @return the smallest time t in [0,20] for which the arrow can reach the target,
     *         or -1.0 if no such time exists within 20 ticks
     */
    public static double estimateTimeOfArrival(double x0, double y0, double z0,
                                               double xt, double yt, double zt,
                                               double speed) {
        // Trivial case: arrow already at target
        if (Math.abs(x0 - xt) < 1e-9 && Math.abs(y0 - yt) < 1e-9 && Math.abs(z0 - zt) < 1e-9) {
            return 0.0;
        }
        if (speed <= 0.0) {
            return -1.0;
        }

        double dx = xt - x0;
        double dy = yt - y0;
        double dz = zt - z0;
        double D = dx * dx + dz * dz;   // squared horizontal distance

        // Precompute powers of DRAG: rPow[n] = DRAG^n for n = 0..20
        double[] rPow = new double[21];
        rPow[0] = 1.0;
        for (int i = 1; i <= 20; i++) {
            rPow[i] = rPow[i - 1] * DRAG;
        }

        // ---- n = 0 : first tick (0 <= t < 1) ----
        // For n=0, 1-r^n = 0, so B(t) = t, C(t)=0.
        double dist0 = Math.sqrt(D + dy * dy);
        double tau0 = dist0 / speed;
        if (tau0 > 1e-12 && tau0 < 1.0 - 1e-12) {
            return tau0;   // t = tau0
        }

        // ---- n = 1 .. 19 : both integer and fractional possibilities ----
        for (int n = 1; n <= 19; n++) {
            double rn = rPow[n];
            double A = 100.0 * (1.0 - rn);            // part of B(t) independent of τ
            double B = rn;                            // coefficient of τ in B(t)
            double C0 = G_PRIME * (100.0 * (1.0 - rn) - n); // constant part of C(t)

            // ---- integer tick (τ = 0) ----
            double lhsInt = D + (dy - C0) * (dy - C0);
            double rhsInt = speed * speed * A * A;
            if (Math.abs(lhsInt - rhsInt) < 1e-9) {
                return n;
            }

            // ---- fractional tick (0 < τ < 1) ----
            double C1 = -G_PRIME * (1.0 - rn);        // coefficient of τ in C(t)
            // Quadratic coefficients: a τ² + b τ + c = 0
            double a = C1 * C1 - speed * speed * B * B;
            double b = -2.0 * (dy - C0) * C1 - 2.0 * speed * speed * A * B;
            double c = D + (dy - C0) * (dy - C0) - speed * speed * A * A;

            if (Math.abs(a) < 1e-12) {
                // linear case
                if (Math.abs(b) > 1e-12) {
                    double tau = -c / b;
                    if (tau > 1e-12 && tau < 1.0 - 1e-12) {
                        return n + tau;
                    }
                }
            } else {
                double disc = b * b - 4.0 * a * c;
                if (disc >= -1e-12) {
                    if (disc < 0.0) disc = 0.0;
                    double sqrtDisc = Math.sqrt(disc);
                    double tau1 = (-b - sqrtDisc) / (2.0 * a);
                    double tau2 = (-b + sqrtDisc) / (2.0 * a);
                    double bestTau = Double.POSITIVE_INFINITY;
                    if (tau1 > 1e-12 && tau1 < 1.0 - 1e-12) bestTau = Math.min(bestTau, tau1);
                    if (tau2 > 1e-12 && tau2 < 1.0 - 1e-12) bestTau = Math.min(bestTau, tau2);
                    if (bestTau < Double.POSITIVE_INFINITY) {
                        return n + bestTau;
                    }
                }
            }
        }

        // ---- n = 20 : only integer tick possible ----
        double r20 = rPow[20];
        double A20 = 100.0 * (1.0 - r20);
        double C20 = G_PRIME * (100.0 * (1.0 - r20) - 20);
        double lhs20 = D + (dy - C20) * (dy - C20);
        double rhs20 = speed * speed * A20 * A20;
        if (Math.abs(lhs20 - rhs20) < 1e-9) {
            return 20.0;
        }

        // No solution found within 20 ticks
        return -1.0;
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
        double ans = estimateTimeOfArrival(px, py, pz, tx, ty, tz, 3);
        long end = System.currentTimeMillis();
        System.out.println("Time taken: " + (end - start) + " ms");
        System.out.println(String.valueOf(ans));
    }
}