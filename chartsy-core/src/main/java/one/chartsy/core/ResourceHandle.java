/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.core;

import one.chartsy.base.function.ThrowingRunnable;

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

    default boolean isCloseable() {
        return true;
    }

    Cleaner CLEANER = Cleaner.create();

    static <V> ResourceHandle<V> of(V v) {
        return of(v, true);
    }

    static <V> ResourceHandle<V> of(V v, boolean isCloseable) {
        return new Of<>(v, isCloseable);
    }

    record Of<V>(V get, boolean isCloseable) implements ResourceHandle<V> {
        public Of {
            if (get instanceof Closeable c)
                CLEANER.register(this, ThrowingRunnable.unchecked(c::close));
        }

        @Override
        public void close() throws Exception {
            if (isCloseable)
                ResourceHandle.super.close();
            else
                throw new UnsupportedOperationException("Managed/Not closeable reference");
        }
    }
}
