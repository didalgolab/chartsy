/*
 * Copyright 2024 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.data.vector;

import one.chartsy.data.DimensionMismatchException;
import one.chartsy.data.RealVector;

public class DistanceMetrics {

    public static DistanceFunction<RealVector> SQUARED_EUCLIDEAN_DISTANCE = (v1, v2) -> {
        checkDimensions(v1, v2);
        double dist = 0.0;
        for (int i = 0; i < v1.size(); i++) {
            double diff = v1.get(i) - v2.get(i);
            dist += diff * diff;
        }
        return dist;
    };

    public static DistanceFunction<RealVector> EUCLIDEAN_DISTANCE = (v1, v2) -> Math.sqrt(SQUARED_EUCLIDEAN_DISTANCE.distance(v1, v2));

    public static DistanceFunction<RealVector> MANHATTAN_DISTANCE = (v1, v2) -> {
        checkDimensions(v1, v2);
        double dist = 0.0;
        for (int i = 0; i < v1.size(); i++) {
            dist += Math.abs(v1.get(i) - v2.get(i));
        }
        return dist;
    };

    public static DistanceFunction<RealVector> CHESSBOARD_DISTANCE = (v1, v2) -> {
        checkDimensions(v1, v2);
        double dist = 0.0;
        for (int i = 0; i < v1.size(); i++) {
            dist = Math.max(dist, Math.abs(v1.get(i) - v2.get(i)));
        }
        return dist;
    };

    public static double euclideanDistance(RealVector v1, RealVector v2) {
        return EUCLIDEAN_DISTANCE.distance(v1, v2);
    }

    public static double squaredEuclideanDistance(RealVector v1, RealVector v2) {
        return SQUARED_EUCLIDEAN_DISTANCE.distance(v1, v2);
    }

    public static double manhattanDistance(RealVector v1, RealVector v2) {
        return MANHATTAN_DISTANCE.distance(v1, v2);
    }

    public static double chessboardDistance(RealVector v1, RealVector v2) {
        return CHESSBOARD_DISTANCE.distance(v1, v2);
    }

    /**
     * Checks if the dimension of the given vector matches the dimension of this vector.
     *
     * @param other the vector to compare dimensions with
     * @throws DimensionMismatchException if the dimensions do not match
     */
    protected static void checkDimensions(RealVector first, RealVector other) throws DimensionMismatchException {
        if (other.size() != first.size()) {
            throw new DimensionMismatchException("Vector dimensions do not match: "
                    + first.size() + " and " + other.size());
        }
    }
}
