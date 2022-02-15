package one.chartsy.misc;

import one.chartsy.core.ResourceHandle;

import java.io.Closeable;

public class ManagedReference<V> implements AutoCloseable, ResourceHandle<V> {
    private final V value;

    public ManagedReference(V value) {
        this.value = value;
    }

    /**
     * Get a referenced value
     */
    @Override
    public final V get() {
        return value;
    }

    @Override
    public void close() throws Exception {
        if (value instanceof Closeable)
            ((Closeable) value).close();
    }
}
