package org.nightcat.accuratus.client;

import java.util.Scanner;

public class MotionPredictor {

    public static double[] predictNext30(double[] x) {
        if (x == null || x.length != 20) {
            throw new IllegalArgumentException("Array length must be 20");
        }

        // calculate speed & accel
        double[] v = new double[19];
        // first derivative of displacement
        for (int i = 0; i < 19; i++) {
            v[i] = x[i + 1] - x[i];
        }
        double[] a = new double[18];
        // second derivative of displacement
        for (int i = 0; i < 18; i++) {
            a[i] = v[i + 1] - v[i];
        }

        // calculate reliability
        SuffixStats best = null;
        // type 0: displacement
        SuffixStats displacementBest = bestSuffixStats(x, 0);
        if (displacementBest != null) best = displacementBest;

        // type 1: speed
        SuffixStats velocityBest = bestSuffixStats(v, 1);
        if (velocityBest != null && (best == null || velocityBest.reliability > best.reliability)) {
            best = velocityBest;
        }

        // accelaration
        SuffixStats accelerationBest = bestSuffixStats(a, 2); // type 2:加速度
        if (accelerationBest != null && (best == null || accelerationBest.reliability > best.reliability)) {
            best = accelerationBest;
        }

        return predict(best, x, v, a);
    }

    private static SuffixStats bestSuffixStats(double[] arr, int type) {
        int n = arr.length;
        double sum = 0, sumSq = 0;
        SuffixStats best = null;

        for (int i = n - 1; i >= 0; i--) {
            double val = arr[i];
            sum += val;
            sumSq += val * val;
            int len = n - i;
            if (len < 3) continue; // use at last 3 data points
            // sample SD
            double mean = sum / len;
            double variance = (sumSq - sum * mean) / (len - 1);
            double sd = Math.sqrt(variance);
            double reliability;
            if (sd < 1e-12) {
                reliability = Double.POSITIVE_INFINITY;
            } else {
                reliability = Math.sqrt(len) / sd / sd;
            }
            // keep highest reliability
            if (best == null || reliability > best.reliability) {
                best = new SuffixStats(type, i, len, mean, sd, reliability);
            }
        }
        return best;
    }


    private static double[] predict(SuffixStats best, double[] x, double[] v, double[] a) {
        double[] result = new double[30];
        if (best == null) {
            double avg = mean(x);
            for (int i = 0; i < 20; i++) result[i] = avg;
            return result;
        }

        switch (best.type) {
            case 0:
                // position suffix: stable
                double constVal = best.mean;
                for (int i = 0; i < 30; i++) result[i] = constVal;
                break;
            case 1:
                // speed suffix: stable speed
                double avgV = best.mean;
                double lastX = x[19];
                for (int i = 0; i < 30; i++) {
                    result[i] = lastX + (i + 1) * avgV;
                }
                break;
            case 2:
                // stable accel
                double avgA = best.mean;
                double lastAvgV = x[19] - x[18];
                double v0 = lastAvgV + avgA / 2.0;
                double x0 = x[19];
                for (int i = 0; i < 30; i++) {
                    double dt = i + 1;
                    result[i] = x0 + v0 * dt + 0.5 * avgA * dt * dt;
                }
                break;
        }
        return result;
    }

    private static double mean(double[] arr) {
        double s = 0;
        for (double v : arr) s += v;
        return s / arr.length;
    }

    private static class SuffixStats {
        int type;
        int startIdx;
        int length;
        double mean;
        double sd;
        double reliability;
        SuffixStats(int type, int startIdx, int length, double mean, double sd, double reliability) {
            this.type = type;
            this.startIdx = startIdx;
            this.length = length;
            this.mean = mean;
            this.sd = sd;
            this.reliability = reliability;
        }
    }

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        double[] positions = new double[20];

        for (int i = 0; i < 20; i++) {
            positions[i] = scanner.nextDouble();
        }

        long start = System.currentTimeMillis();
        double[] predictions = predictNext30(positions);
        long end = System.currentTimeMillis();
        System.out.println("Time taken: " + (end - start) + " ms");

        for (int i = 0; i < 30; i++) {
            System.out.printf("t=%2d : %10.4f\n", 20 + i, predictions[i]);
        }
        scanner.close();
    }
}


// no motion: 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0
// Answer: 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0

// stable speed: 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17 18 19 20
// Answer: 21 22 23 24 25 26 27 28 29 30 31 32 33 34 35 36 37 38 39 40

// stable accelaration: 0.5 2.0 4.5 8.0 12.5 18.0 24.5 32.0 40.5 50.0 60.5 72.0 84.5 98.0 112.5 128.0 144.5 162.0 180.5 200.0 
// Answer: 220.5 242.0 264.5 288.0 312.5 338.0 364.5 392.0 420.5 450.0 480.5 512.0 544.5 578.0 612.5 648.0 684.5 722.0 760.5 800.0


// no motion -> stable speed: 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 1 2 3 4 5
// Answer: 6 7 8 9 10 11 12 13 14 15 16 17 18 19 20 21 22 23 24 25

// stable speed -> suddenly stops: 15 14 13 12 11 10 9 8 7 6 5 4 3 2 1 0 0 0 0 0
// Answer: 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0

// stable speed -> suddenly accelerates: 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 17 20 24 29 35
// Answer: 42 50 59 69 80 92 105 119 ....


// No motion -> stable speed (WITH ERROR)
// 0 0 0 0 0 0 0 0 0 0 0 0 0 0 1.02 1.99 2.97 4 4.95 6.01
// Answer: 7 8 9 10 11 12 ...


// stable speed -> suddenly decelerates (WITH ERROR)
// 0 10 20 30 40.01 50 59.97 70 80 90.03 100 110 119.98 130 138.99 147.01 154 160 164.98 169.02
// Answer: 172 174 175 175 174 172 ....