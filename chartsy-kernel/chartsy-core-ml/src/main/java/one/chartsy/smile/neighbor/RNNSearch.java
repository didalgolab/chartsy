/*
 * Copyright (c) -2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package one.chartsy.smile.neighbor;

import java.util.List;

/**
 * A range nearest neighbor search retrieves the nearest neighbors to a query
 * in a range. It is a natural generalization of point and continuous nearest
 * neighbor queries and has many applications.
 *
 * @param <K> the type of keys.
 * @param <V> the type of associated objects.
 *
 * @author Haifeng Li
 */
public interface RNNSearch<K, V> {
    /**
     * Search the neighbors in the given radius of query object, i.e.
     * d(q, v) &le; radius.
     *
     * @param q the query key.
     * @param radius the radius of search range from target.
     * @param neighbors the list to store found neighbors in the given range on output.
     */
    void range(K q, double radius, List<Neighbor<K,V>> neighbors);
}
