/*
 * Copyright 2024 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.data.vector;

/**
 * Defines a metric for computing the distance between two generic
 * instances. This interface provides a base contract for implementing
 * various types of distance metrics, such as Euclidean, Manhattan,
 * and others, providing flexibility in calculations based on the
 * specific requirements of the application.
 *
 * <p>Implementations of this interface should focus on accuracy,
 * efficiency, and numerical stability.</p>
 *
 * @author Mariusz Bernacki
 */
@FunctionalInterface
public interface DistanceFunction<T> {

    /**
     * Gives the distance between two objects.
     * The method of distance calculation depends on the specific
     * implementation of this interface.
     *
     * @param v1 the first vector
     * @param v2 the second vector
     * @return the calculated distance between the two vectors
     * @throws IllegalArgumentException if the object are incompatible
     *         in a way that calculating a distance is impossible
     */
    double distance(T v1, T v2);

}
