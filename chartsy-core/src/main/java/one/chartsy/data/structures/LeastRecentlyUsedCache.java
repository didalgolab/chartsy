/* Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.data.structures;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class LeastRecentlyUsedCache<K, V> {

    private final int capacity;
    private final LinkedHashMap<K, V> values;

    public LeastRecentlyUsedCache(int capacity) {
        this.capacity = capacity;
        this.values = new LinkedHashMap<>(capacity/2, 4f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                return size() > capacity;
            }
        };
    }

    public int capacity() {
        return capacity;
    }

    public boolean isEmpty() {
        return values.isEmpty();
    }

    public V put(K key, V value) {
        return values.put(key, value);
    }

    public V remove(K key) {
        return values.remove(key);
    }

    public void clear() {
        values.clear();
    }

    public boolean containsKey(K key) {
        return values.containsKey(key);
    }

    public int size() {
        return values.size();
    }

    public V get(Object key) {
        return values.get(key);
    }

    public V get(Object key, V defaultValue) {
        return values.getOrDefault(key, defaultValue);
    }

    public Set<K> keySet() {
        return values.keySet();
    }

    public Collection<V> values() {
        return values.values();
    }
}
