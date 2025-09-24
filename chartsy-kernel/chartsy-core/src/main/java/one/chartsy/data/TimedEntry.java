/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.data;

import one.chartsy.time.Chronological;

/**
 * Represents a time-indexed data.
 *
 * @param <V> type of encapsulated object
 */
public class TimedEntry<V> implements Chronological {

    private final long time;
    private final V value;


    public TimedEntry(long time, V value) {
        this.time = time;
        this.value = value;
    }

    @Override
    public final long time() {
        return time;
    }

    public V getValue() {
        return value;
    }
}
