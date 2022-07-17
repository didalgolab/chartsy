/*
 * Copyright (c) 2010 Haifeng Li
 * Copyright (c) -2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.smile.neighbor;

/**
 * K-nearest neighbor search identifies the top k nearest neighbors to the
 * query. This technique is commonly used in predictive analytics to
 * estimate or classify a point based on the consensus of its neighbors.
 * K-nearest neighbor graphs are graphs in which every point is connected
 * to its k nearest neighbors.
 *
 * @param <K> the type of keys.
 * @param <V> the type of associated objects.
 *
 * @author Haifeng Li
 */
public interface KNNSearch<K, V> {
    /**
     * Search the k nearest neighbors to the query.
     *
     * @param q the query key.
     * @param k	the number of nearest neighbors to search for.
     */
    Neighbor<K,V>[] knn(K q, int k);
}
