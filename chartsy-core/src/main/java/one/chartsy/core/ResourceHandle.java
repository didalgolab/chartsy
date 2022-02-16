package one.chartsy.core;

import java.io.Closeable;
import java.lang.ref.Cleaner;

public interface ResourceHandle<V> extends AutoCloseable {

    V get();

    @Override
    default void close() throws Exception {
        if (get() instanceof Closeable c)
            c.close();
        else
            throw new UnsupportedOperationException("ResourceHandle is not Closeable");
    }

    Cleaner CLEANER = Cleaner.create();

    static <V> ResourceHandle<V> of(V v) {
        return new Of<>(v);
    }

    record Of<V>(V get) implements ResourceHandle<V> {
        public Of {
            if (get instanceof Closeable c)
                CLEANER.register(this, ThrowingRunnable.unchecked(c::close));
        }
    }
}
