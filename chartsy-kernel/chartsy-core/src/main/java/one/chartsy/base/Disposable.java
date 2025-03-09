/* Copyright 2025 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.base;

import java.io.Closeable;

/**
 * A resource or component that can be disposed of or closed, releasing any underlying resources.
 * Represents a foundational interface for resource management. Unlike {@link Closeable}, the
 * {@code close()} method of {@code Disposable} does not throw any checked exceptions.
 */
public interface Disposable extends Closeable {

    /**
     * Closes or disposes the resource. Implementations should release any underlying resources held.
     */
    @Override void close();
}
