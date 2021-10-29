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
    public final long getTime() {
        return time;
    }

    public V getValue() {
        return value;
    }
}
