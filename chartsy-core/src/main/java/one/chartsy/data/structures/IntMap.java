/*
 * Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.data.structures;

import java.util.Iterator;
import java.util.Map;

/**
 * Interface for a primitive map that uses {@code int}s as keys.
 *
 * <p>Modifications:</p>
 * <ul>
 * <li>[MB] 2022-05-17: Repackaged from {@code io.netty.util.collection}</li>
 * <li>[MB] 2022-05-17: Renamed from {@code IntObjectMap}</li>
 * </ul>
 *
 * @param <V> the value type stored in the map.
 */
public interface IntMap<V> extends Map<Integer, V> {

    /**
     * A primitive entry in the map, provided by the iterator from {@link #entries()}
     *
     * @param <V> the value type stored in the map.
     */
    interface PrimitiveEntry<V> {
        /**
         * Gets the key for this entry.
         */
        int key();

        /**
         * Gets the value for this entry.
         */
        V value();

        /**
         * Sets the value for this entry.
         */
        void setValue(V value);
    }

    /**
     * Gets the value in the map with the specified key.
     *
     * @param key the key whose associated value is to be returned.
     * @return the value or {@code null} if the key was not found in the map.
     */
    V get(int key);

    /**
     * Puts the given entry into the map.
     *
     * @param key the key of the entry.
     * @param value the value of the entry.
     * @return the previous value for this key or {@code null} if there was no previous mapping.
     */
    V put(int key, V value);

    /**
     * Removes the entry with the specified key.
     *
     * @param key the key for the entry to be removed from this map.
     * @return the previous value for the key, or {@code null} if there was no mapping.
     */
    V remove(int key);

    /**
     * Gets an iterable to traverse over the primitive entries contained in this map. As an optimization,
     * the {@link PrimitiveEntry}s returned by the {@link Iterator} may change as the {@link Iterator}
     * progresses. The caller should not rely on {@link PrimitiveEntry} key/value stability.
     */
    Iterable<PrimitiveEntry<V>> entries();

    /**
     * Indicates whether this map contains a value for the specified key.
     */
    boolean containsKey(int key);

}
