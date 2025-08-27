/* Copyright 2024 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy;

/**
 * A resource that must be explicitly opened, then can be closed.
 * Implementations should make {@code close()} idempotent.
 */
public interface Openable extends AutoCloseable {

    /**
     * Acquire the underlying resource. Calling {@code open()} more than once
     * should be a no-op or throw {@code IllegalStateException}.
     */
    void open();

    @Override
    void close();
}