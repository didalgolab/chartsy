/*
 * Copyright (c) -2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package one.chartsy.smile.math.distance;

/**
 * Euclidean distance. Use getInstance() to get the standard unweighted
 * Euclidean distance. Or create an instance with a specified
 * weight vector. For float or double arrays, missing values (i.e. NaN)
 * are also handled. Also support sparse arrays of which zeros are excluded
 * to save space.
 *
 * @author Haifeng Li
 */
public class EuclideanDistance implements Metric<double[]> {

    public static final EuclideanDistance INSTANCE = new EuclideanDistance();

    /**
     * The weights used in weighted distance.
     */
    private final double[] weight;

    /**
     * Constructor. Standard (unweighted) Euclidean distance.
     */
    public EuclideanDistance() {
        this.weight = null;
    }

    /**
     * Constructor with a given weight vector.
     * 
     * @param weight the weight vector.
     */
    public EuclideanDistance(double[] weight) {
        for (double v : weight)
            if (v < 0)
                throw new IllegalArgumentException(String.format("Weight has to be non-negative: %f", v));

        this.weight = weight;
    }

    @Override
    public String toString() {
        if (weight != null)
            return "weighted Euclidean distance";
        else
            return "Euclidean distance";
    }

    /**
     * Euclidean distance between the two arrays of type integer. No missing
     * value handling in this method.
     */
    public double d(int[] x, int[] y) {
        if (x.length != y.length)
            throw new IllegalArgumentException(String.format("Arrays have different length: x[%d], y[%d]", x.length, y.length));

        double dist = 0.0;

        if (weight == null) {
            for (int i = 0; i < x.length; i++) {
                double d = x[i] - y[i];
                dist += d * d;
            }
        } else {
            if (x.length != weight.length)
                throw new IllegalArgumentException(String.format("Input vectors and weight vector have different length: %d, %d", x.length, weight.length));

            for (int i = 0; i < x.length; i++) {
                double d = x[i] - y[i];
                dist += weight[i] * d * d;
            }
        }

        return Math.sqrt(dist);
    }

    /**
     * Euclidean distance between the two arrays of type float.
     * NaN will be treated as missing values and will be excluded from the
     * calculation. Let m be the number nonmissing values, and n be the
     * number of all values. The returned distance is sqrt(n * d / m),
     * where d is the square of distance between nonmissing values.
     */
    public double d(float[] x, float[] y) {
        if (x.length != y.length)
            throw new IllegalArgumentException(String.format("Arrays have different length: x[%d], y[%d]", x.length, y.length));

        int n = x.length;
        int m = 0;
        double dist = 0.0;

        if (weight == null) {
            for (int i = 0; i < n; i++) {
                if (!Float.isNaN(x[i]) && !Float.isNaN(y[i])) {
                    m++;
                    double d = x[i] - y[i];
                    dist += d * d;
                }
            }
        } else {
            if (x.length != weight.length)
                throw new IllegalArgumentException(String.format("Input vectors and weight vector have different length: %d, %d", x.length, weight.length));

            for (int i = 0; i < n; i++) {
                if (!Float.isNaN(x[i]) && !Float.isNaN(y[i])) {
                    m++;
                    double d = x[i] - y[i];
                    dist += weight[i] * d * d;
                }
            }
        }

        if (m == 0)
            dist = Double.NaN;
        else
            dist = n * dist / m;


        return Math.sqrt(dist);
    }

    /**
     * Euclidean distance between the two arrays of type double.
     * NaN will be treated as missing values and will be excluded from the
     * calculation. Let m be the number nonmissing values, and n be the
     * number of all values. The returned distance is sqrt(n * d / m),
     * where d is the square of distance between nonmissing values.
     */
    @Override
    public double d(double[] x, double[] y) {
        if (x.length != y.length)
            throw new IllegalArgumentException(String.format("Arrays have different length: x[%d], y[%d]", x.length, y.length));

        int n = x.length;
        int m = 0;
        double dist = 0.0;

        if (weight == null) {
            for (int i = 0; i < n; i++) {
                if (!Double.isNaN(x[i]) && !Double.isNaN(y[i])) {
                    m++;
                    double d = x[i] - y[i];
                    dist += d * d;
                }
            }
        } else {
            if (x.length != weight.length)
                throw new IllegalArgumentException(String.format("Input vectors and weight vector have different length: %d, %d", x.length, weight.length));

            for (int i = 0; i < n; i++) {
                if (!Double.isNaN(x[i]) && !Double.isNaN(y[i])) {
                    m++;
                    double d = x[i] - y[i];
                    dist += weight[i] * d * d;
                }
            }
        }

        if (m == 0)
            dist = Double.NaN;
        else
            dist = n * dist / m;

        return Math.sqrt(dist);
    }
}
