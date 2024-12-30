/*
 * Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.data.structures;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Provides an unmodifiable view of an {@code IntMap}.
 *
 * An unmodifiable map may still change, since it's only a view on a modifiable map, and changes in the backing map will
 * be visible through the unmodifiable map. The unmodifiable map prevents modifications of an underlying map through the
 * view throwing {@code UnsupportedOperationException} instead.
 *
 * @param <V> the value type stored in the map.
 */
public class UnmodifiableIntMap<V> implements IntMap<V> {

    private Iterable<PrimitiveEntry<V>> entriesView;
    private Set<Entry<Integer, V>> entrySetView;
    private Collection<V> valuesView;
    private Set<Integer> keySetView;

    protected final IntMap<V> m;


    public static <V> UnmodifiableIntMap<V> of(IntMap<V> m) {
        if (m instanceof UnmodifiableIntMap<V> unmodifiable)
            return unmodifiable;
        return new UnmodifiableIntMap<>(m);
    }

    private UnmodifiableIntMap(IntMap<V> m) {
        this.m = m;
    }

    @Override public int size()                          { return m.size(); }
    @Override public V get(int key)                      { return m.get(key); }
    @Override public boolean containsKey(int key)        { return m.containsKey(key); }
    @Override public boolean isEmpty()                   { return m.isEmpty(); }
    @Override public boolean containsKey(Object key)     { return m.containsKey(key); }
    @Override public boolean containsValue(Object value) { return m.containsValue(value); }
    @Override public V get(Object key)                   { return m.get(key); }

    @Override
    public void forEach(BiConsumer<? super Integer, ? super V> action) {
        m.forEach(action);
    }

    @Override
    public V put(int key, V value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public V remove(int key) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Iterable<PrimitiveEntry<V>> entries() {
        if (entriesView == null)
            entriesView = PrimitiveIterator::new;
        return entriesView;
    }

    @Override
    public V put(Integer key, V value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public V remove(Object key) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void putAll(Map<? extends Integer, ? extends V> m) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void clear() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Set<Integer> keySet() {
        if (keySetView == null)
            keySetView = Collections.unmodifiableSet(m.keySet());
        return keySetView;
    }

    @Override
    public Collection<V> values() {
        if (valuesView == null)
            valuesView = Collections.unmodifiableCollection(m.values());
        return valuesView;
    }

    @Override
    public Set<Entry<Integer, V>> entrySet() {
        if (entrySetView == null)
            entrySetView = Collections.unmodifiableSet(m.entrySet());
        return entrySetView;
    }

    @Override
    public void replaceAll(BiFunction<? super Integer, ? super V, ? extends V> function) {
        throw new UnsupportedOperationException();
    }

    @Override
    public V putIfAbsent(Integer key, V value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean remove(Object key, Object value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean replace(Integer key, V oldValue, V newValue) {
        throw new UnsupportedOperationException();
    }

    @Override
    public V replace(Integer key, V value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public V computeIfAbsent(Integer key, Function<? super Integer, ? extends V> mappingFunction) {
        throw new UnsupportedOperationException();
    }

    @Override
    public V computeIfPresent(Integer key, BiFunction<? super Integer, ? super V, ? extends V> remappingFunction) {
        throw new UnsupportedOperationException();
    }

    @Override
    public V compute(Integer key, BiFunction<? super Integer, ? super V, ? extends V> remappingFunction) {
        throw new UnsupportedOperationException();
    }

    @Override
    public V merge(Integer key, V value, BiFunction<? super V, ? super V, ? extends V> remappingFunction) {
        throw new UnsupportedOperationException();
    }

    private final class PrimitiveIterator implements Iterator<PrimitiveEntry<V>>, PrimitiveEntry<V> {
        private final Iterator<? extends PrimitiveEntry<V>> iter = m.entries().iterator();
        private PrimitiveEntry<V> last;

        @Override
        public boolean hasNext() {
            return iter.hasNext();
        }

        @Override
        public PrimitiveEntry<V> next() {
            last = iter.next();
            return this;
        }

        @Override
        public int key() {
            return last.key();
        }

        @Override
        public V value() {
            return last.value();
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setValue(V value) {
            throw new UnsupportedOperationException();
        }
    }
}
