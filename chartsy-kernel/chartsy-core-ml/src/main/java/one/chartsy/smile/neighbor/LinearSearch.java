/*
 * Copyright (c) -2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package one.chartsy.smile.neighbor;

import java.util.List;

import one.chartsy.smile.math.distance.Distance;
import one.chartsy.smile.sort.HeapSelect;

/**
 * Brute force linear nearest neighbor search. This simplest solution computes
 * the distance from the query point to every other point in the database,
 * keeping track of the "best so far". There are no search data structures to
 * maintain, so linear search has no space complexity beyond the storage of
 * the database. Although it is very simple, naive search outperforms space
 * partitioning approaches (e.g. K-D trees) on higher dimensional spaces.
 * <p>
 * By default, the query object (reference equality) is excluded from the neighborhood.
 * You may change this behavior with <code>setIdenticalExcluded</code>. Note that
 * you may observe weird behavior with String objects. JVM will pool the string literal
 * objects. So the below variables
 * <code>
 *     String a = "ABC";
 *     String b = "ABC";
 *     String c = "AB" + "C";
 * </code>
 * are actually equal in reference test <code>a == b == c</code>. With toy data that you
 * type explicitly in the code, this will cause problems. Fortunately, the data would be
 * read from secondary storage in production.
 * </p>
 *
 * @param <T> the type of data objects.
 *
 * @author Haifeng Li
 */
public class LinearSearch<T> implements NearestNeighborSearch<T,T>, KNNSearch<T,T>, RNNSearch<T,T> {

    /**
     * The dataset of search space.
     */
    private final T[] data;

    /**
     * The distance function used to determine nearest neighbors.
     */
    private final Distance<T> distance;

    /**
     * Whether to exclude query object self from the neighborhood.
     */
    private boolean identicalExcluded = true;

    /**
     * Constructor. By default, query object self will be excluded from search.
     */
    public LinearSearch(T[] dataset, Distance<T> distance) {
        this.data = dataset;
        this.distance = distance;
    }

    @Override
    public String toString() {
        return String.format("Linear Search (%s)", distance);
    }

    /**
     * Set if exclude query object self from the neighborhood.
     */
    public LinearSearch<T> setIdenticalExcluded(boolean excluded) {
        identicalExcluded = excluded;
        return this;
    }

    /**
     * Get whether if query object self be excluded from the neighborhood.
     */
    public boolean isIdenticalExcluded() {
        return identicalExcluded;
    }

    @Override
    public Neighbor<T,T> nearest(T q) {
        T neighbor = null;
        int index = -1;
        double dist = Double.MAX_VALUE;
        for (int i = 0; i < data.length; i++) {
            if (q == data[i] && identicalExcluded) {
                continue;
            }

            double d = distance.d(q, data[i]);

            if (d < dist) {
                neighbor = data[i];
                index = i;
                dist = d;
            }
        }

        return Neighbor.of(neighbor, index, dist);
    }

    @Override
    public Neighbor<T,T>[] knn(T q, int k) {
        if (k <= 0) {
            throw new IllegalArgumentException("Invalid k: " + k);
        }

        if (k > data.length) {
            throw new IllegalArgumentException("Neighbor array length is larger than the dataset size");
        }

        Neighbor<T,T> neighbor = Neighbor.of(null, 0, Double.MAX_VALUE);
        @SuppressWarnings("unchecked")
        Neighbor<T,T>[] neighbors = (Neighbor<T,T>[]) java.lang.reflect.Array.newInstance(neighbor.getClass(), k);
        HeapSelect<Neighbor<T,T>> heap = new HeapSelect<>(neighbors);
        for (int i = 0; i < k; i++) {
            heap.add(neighbor);
            neighbor = Neighbor.of(null, 0, Double.MAX_VALUE);
        }

        for (int i = 0; i < data.length; i++) {
            if (q == data[i] && identicalExcluded) {
                continue;
            }

            double dist = distance.d(q, data[i]);
            Neighbor<T,T> datum = heap.peek();
            if (dist < datum.distance) {
                datum.distance = dist;
                datum.index = i;
                datum.key = data[i];
                datum.value = data[i];
                heap.heapify();
            }
        }

        heap.sort();
        return neighbors;
    }

    @Override
    public void range(T q, double radius, List<Neighbor<T,T>> neighbors) {
        if (radius <= 0.0) {
            throw new IllegalArgumentException("Invalid radius: " + radius);
        }

        for (int i = 0; i < data.length; i++) {
            if (q == data[i] && identicalExcluded) {
                continue;
            }

            double d = distance.d(q, data[i]);

            if (d <= radius) {
                neighbors.add(Neighbor.of(data[i], i, d));
            }
        }
    }
}
