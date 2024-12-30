/*
 * Copyright (c) 2010 Haifeng Li
 * Copyright (c) 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.smile.neighbor;

/**
 * The object encapsulates the results of nearest neighbor search. A returned
 * neighbor for nearest neighbor search contains the key of object (say weight
 * vector of a neuron) and the object itself (say a neuron in neural network,
 * which also contains other information beyond weight vector), an index of
 * object in the dataset, which is often useful, and the distance between
 * the query key to the object key.
 *
 * @param <K> the type of keys.
 * @param <V> the type of associated objects.
 *
 * @author Haifeng Li
 */
public class Neighbor<K, V> implements Comparable<Neighbor<K,V>> {
    /**
     * The key of neighbor.
     */
    public K key;
    /**
     * The data object of neighbor. It may be same as the key object.
     */
    public V value;
    /**
     * The index of neighbor object in the dataset.
     */
    public int index;
    /**
     * The distance between the query and the neighbor.
     */
    public double distance;

    /**
     * Constructor.
     * @param object the neighbor object.
     * @param index the index of neighbor object in the dataset.
     * @param distance the distance between the query and the neighbor.
     */
    public Neighbor(K key, V object, int index, double distance) {
        this.key = key;
        this.value = object;
        this.index = index;
        this.distance = distance;
    }

    @Override
    public int compareTo(Neighbor<K,V> o) {
        if (distance > o.distance)
            return 1;
        else if (distance < o.distance)
            return -1;
        else
            return index - o.index;

//        int d = (int) Math.signum(distance - o.distance);
//        // Sometime, the dataset contains duplicate samples.
//        // If the distances are same, we sort by the sample index.
//        if (d == 0)
//            return index - o.index;
//        else
//            return d;
    }

    public static <T> Neighbor.Simple<T> of(T key, int index, double distance) {
        return new Simple<>(key, index, distance);
    }

    /**
     * The simple neighbor object, in which key and object are the same.
     *
     * @param <T> the type of key/object.
     *
     * @author Haifeng Li
     */
    public static class Simple<T> extends Neighbor<T,T> {
        /**
         * Constructor.
         * @param key the neighbor object.
         * @param index the index of neighbor object in the dataset.
         * @param distance the distance between the query and the neighbor.
         */
        public Simple(T key, int index, double distance) {
            super(key, key, index, distance);
        }
    }
}
