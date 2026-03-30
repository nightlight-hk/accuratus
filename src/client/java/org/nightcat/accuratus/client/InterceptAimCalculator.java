package org.nightcat.accuratus.client;

import java.util.Arrays;

public final class InterceptAimCalculator {

    private static final int HISTORY_TICKS = 20;
    private static final int PREDICTION_TICKS = 30;
    private static final int TICK_TOLERANCE = 1;
    private static final double MAX_BUDGET_MS = 30.0;

    private InterceptAimCalculator() {
    }

    public static AimSolution findEarliestAimSolution(
            double[] pastX,
            double[] pastY,
            double[] pastZ,
            double startX,
            double startY,
            double startZ,
            double arrowSpeed
    ) {
        return findEarliestAimSolution(
                pastX,
                pastY,
                pastZ,
                startX,
                startY,
                startZ,
                arrowSpeed,
                0
        );
    }

    public static AimSolution findEarliestAimSolution(
            double[] pastX,
            double[] pastY,
            double[] pastZ,
            double startX,
            double startY,
            double startZ,
            double arrowSpeed,
            int launchDelayTicks
    ) {
        validateHistory(pastX, "pastX");
        validateHistory(pastY, "pastY");
        validateHistory(pastZ, "pastZ");
        if (arrowSpeed <= 0.0) {
            throw new IllegalArgumentException("arrowSpeed must be > 0");
        }
        if (launchDelayTicks < 0 || launchDelayTicks > PREDICTION_TICKS) {
            throw new IllegalArgumentException("launchDelayTicks must be in [0, " + PREDICTION_TICKS + "]");
        }

        long beginNs = System.nanoTime();

        double[] predictedX = MotionPredictor.predictNext30(pastX);
        double[] predictedY = MotionPredictor.predictNext30(pastY);
        double[] predictedZ = MotionPredictor.predictNext30(pastZ);

        for (int i = 0; i < PREDICTION_TICKS; i++) {
            int futureTick = i + 1;
            double elapsedMs = (System.nanoTime() - beginNs) / 1_000_000.0;
            if (elapsedMs > MAX_BUDGET_MS) {
                return AimSolution.timeout(elapsedMs);
            }

            double tx = predictedX[i];
            double ty = predictedY[i];
            double tz = predictedZ[i];

            double[] yawPitchTick = TrajectoryCalculator.findAnglesAndTicks(
                    startX,
                    startY,
                    startZ,
                    tx,
                    ty,
                    tz,
                    arrowSpeed
            );

            int shotTick = (int) Math.round(yawPitchTick[2]);
            if (shotTick <= 0) {
                continue;
            }

            if (Math.abs((shotTick + launchDelayTicks) - futureTick) <= TICK_TOLERANCE) {
                elapsedMs = (System.nanoTime() - beginNs) / 1_000_000.0;
                return AimSolution.hit(
                        yawPitchTick[0],
                        yawPitchTick[1],
                        tx,
                        ty,
                        tz,
                        futureTick,
                        shotTick,
                        elapsedMs
                );
            }
        }

        double elapsedMs = (System.nanoTime() - beginNs) / 1_000_000.0;
        return AimSolution.noHit(elapsedMs);
    }

    public static AimSolution findAimSolutionAtFutureTick(
            double[] pastX,
            double[] pastY,
            double[] pastZ,
            double startX,
            double startY,
            double startZ,
            double arrowSpeed,
            int futureTick
    ) {
        return findAimSolutionAtFutureTick(
                pastX,
                pastY,
                pastZ,
                startX,
                startY,
                startZ,
                arrowSpeed,
                futureTick,
                0
        );
    }

    public static AimSolution findAimSolutionAtFutureTick(
            double[] pastX,
            double[] pastY,
            double[] pastZ,
            double startX,
            double startY,
            double startZ,
            double arrowSpeed,
            int futureTick,
            int launchDelayTicks
    ) {
        validateHistory(pastX, "pastX");
        validateHistory(pastY, "pastY");
        validateHistory(pastZ, "pastZ");
        if (arrowSpeed <= 0.0) {
            throw new IllegalArgumentException("arrowSpeed must be > 0");
        }
        if (futureTick < 1 || futureTick > PREDICTION_TICKS) {
            throw new IllegalArgumentException("futureTick must be in [1, " + PREDICTION_TICKS + "]");
        }
        if (launchDelayTicks < 0 || launchDelayTicks > PREDICTION_TICKS) {
            throw new IllegalArgumentException("launchDelayTicks must be in [0, " + PREDICTION_TICKS + "]");
        }

        long beginNs = System.nanoTime();

        double[] predictedX = MotionPredictor.predictNext30(pastX);
        double[] predictedY = MotionPredictor.predictNext30(pastY);
        double[] predictedZ = MotionPredictor.predictNext30(pastZ);

        double elapsedMs = (System.nanoTime() - beginNs) / 1_000_000.0;
        if (elapsedMs > MAX_BUDGET_MS) {
            return AimSolution.timeout(elapsedMs);
        }

        int idx = futureTick - 1;
        double tx = predictedX[idx];
        double ty = predictedY[idx];
        double tz = predictedZ[idx];

        int expectedFlightTick = futureTick - launchDelayTicks;
        if (expectedFlightTick <= 0) {
            return AimSolution.noHit(elapsedMs);
        }

        double[] yawPitchTick = TrajectoryCalculator.findAnglesAndTicksWithin(
                startX,
                startY,
                startZ,
                tx,
                ty,
                tz,
                arrowSpeed,
                Math.max(1, expectedFlightTick - TICK_TOLERANCE),
                expectedFlightTick + TICK_TOLERANCE
        );

        int shotTick = (int) Math.round(yawPitchTick[2]);
        elapsedMs = (System.nanoTime() - beginNs) / 1_000_000.0;
        if (elapsedMs > MAX_BUDGET_MS) {
            return AimSolution.timeout(elapsedMs);
        }

        if (shotTick <= 0 || Math.abs((shotTick + launchDelayTicks) - futureTick) > TICK_TOLERANCE) {
            return AimSolution.noHit(elapsedMs);
        }

        return AimSolution.hit(
                yawPitchTick[0],
                yawPitchTick[1],
                tx,
                ty,
                tz,
                futureTick,
                shotTick,
                elapsedMs
        );
    }

    private static void validateHistory(double[] history, String name) {
        if (history == null || history.length != HISTORY_TICKS) {
            throw new IllegalArgumentException(name + " length must be " + HISTORY_TICKS);
        }
    }

    public static final class AimSolution {
        public final boolean found;
        public final boolean timedOut;
        public final double yaw;
        public final double pitch;
        public final double aimX;
        public final double aimY;
        public final double aimZ;
        public final int targetFutureTick;
        public final int arrowFlightTick;
        public final double computeMs;

        private AimSolution(
                boolean found,
                boolean timedOut,
                double yaw,
                double pitch,
                double aimX,
                double aimY,
                double aimZ,
                int targetFutureTick,
                int arrowFlightTick,
                double computeMs
        ) {
            this.found = found;
            this.timedOut = timedOut;
            this.yaw = yaw;
            this.pitch = pitch;
            this.aimX = aimX;
            this.aimY = aimY;
            this.aimZ = aimZ;
            this.targetFutureTick = targetFutureTick;
            this.arrowFlightTick = arrowFlightTick;
            this.computeMs = computeMs;
        }

        private static AimSolution hit(
                double yaw,
                double pitch,
                double aimX,
                double aimY,
                double aimZ,
                int targetFutureTick,
                int arrowFlightTick,
                double computeMs
        ) {
            return new AimSolution(
                    true,
                    false,
                    yaw,
                    pitch,
                    aimX,
                    aimY,
                    aimZ,
                    targetFutureTick,
                    arrowFlightTick,
                    computeMs
            );
        }

        private static AimSolution noHit(double computeMs) {
            return new AimSolution(
                    false,
                    false,
                    Double.NaN,
                    Double.NaN,
                    Double.NaN,
                    Double.NaN,
                    Double.NaN,
                    -1,
                    -1,
                    computeMs
            );
        }

        private static AimSolution timeout(double computeMs) {
            return new AimSolution(
                    false,
                    true,
                    Double.NaN,
                    Double.NaN,
                    Double.NaN,
                    Double.NaN,
                    Double.NaN,
                    -1,
                    -1,
                    computeMs
            );
        }

        @Override
        public String toString() {
            if (timedOut) {
                return String.format("AimSolution{timedOut=true, computeMs=%.3f}", computeMs);
            }
            if (!found) {
                return String.format("AimSolution{found=false, computeMs=%.3f}", computeMs);
            }
            return String.format(
                    "AimSolution{yaw=%.3f, pitch=%.3f, aim=(%.3f, %.3f, %.3f), targetTick=%d, arrowTick=%d, computeMs=%.3f}",
                    yaw,
                    pitch,
                    aimX,
                    aimY,
                    aimZ,
                    targetFutureTick,
                    arrowFlightTick,
                    computeMs
            );
        }
    }

    public static void main(String[] args) {
        double[] x = new double[HISTORY_TICKS];
        double[] y = new double[HISTORY_TICKS];
        double[] z = new double[HISTORY_TICKS];

        // Tiny benchmark input: target moves steadily on X/Z.
        for (int i = 0; i < HISTORY_TICKS; i++) {
            x[i] = 20.0 + i * 0.25;
            y[i] = 65.0;
            z[i] = 20.0 + i * 0.1;
        }

        long t0 = System.nanoTime();
        AimSolution result = findEarliestAimSolution(x, y, z, 0.0, 66.52, 0.0, 3.0);
        long t1 = System.nanoTime();

        System.out.println(result);
        System.out.println("End-to-end time (ms): " + ((t1 - t0) / 1_000_000.0));
        System.out.println("x20=" + Arrays.toString(x));
    }
}

