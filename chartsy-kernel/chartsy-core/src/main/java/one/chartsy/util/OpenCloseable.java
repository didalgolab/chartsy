/* Copyright 2025 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.util;

/**
 * A resource with an explicit open/close lifecycle.
 * <p>
 * This interface models resources that must be explicitly {@link #open()}-ed
 * before use and reliably {@link #close()}-d after use. It extends
 * {@link AutoCloseable} so instances integrate with Java's
 * try-with-resources construct.
 *
 * <h2>Lifecycle and states</h2>
 * <pre>
 *   NEW --open()--> OPEN --close()--> CLOSED
 * </pre>
 * Implementations MAY permit re-opening after {@code close()} or treat
 * {@code close()} as terminal; they MUST document which behavior they choose.
 * The default expectation for many I/O-style resources is that {@code close()}
 * is terminal (compare {@code java.nio.channels.Channel}).
 *
 * <h2>Idempotence</h2>
 * Implementations aren't required to make the {@link #open()} idempotent.
 * While {@link AutoCloseable#close()} is not required by the JDK to be
 * idempotent, it is strongly encouraged by its specification. The designer
 * acknowledge that making {@code open()} idempotent may be difficult for some
 * resources, but it is strongly recommended to make {@code close()} idempotent.
 *
 * <h2>Exception behavior</h2>
 * Both {@link #open()} and {@link #close()} may throw checked exceptions
 * depending on the resource. Implementations are encouraged to declare
 * narrower exception types (for example, {@code IOException}) where
 * appropriate. If {@code open()} fails, implementations should leave the
 * instance in the CLOSED state; {@code close()} must be safe to call after a
 * failed {@code open()}.
 * <p>
 * When used with try-with-resources, any exception thrown by {@code close()}
 * will be suppressed if another exception is already being thrown from the
 * try block, including the one from the unsuccessful {@code open()}. Callers
 * can retrieve suppressed exceptions via {@link Throwable#getSuppressed()}.
 *
 * <h2>Thread safety</h2>
 * Thread-safety is implementation-specific. Unless stated otherwise,
 * instances should be treated as not thread-safe. Implementations that are
 * thread-safe must document their guarantees around concurrent open/close
 * and operation calls.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * try (OpenCloseable r = resourceFactory.create()) {
 *     r.open();
 *     // use r
 * } // close() is invoked automatically
 * }</pre>
 *
 * @see AutoCloseable
 */
public interface OpenCloseable extends AutoCloseable {

    /**
     * Acquire underlying resources and transition the instance to the OPEN state.
     */
    void open();

    /**
     * Release underlying resources and transition the instance to the CLOSED state.
     * <p>
     * This method MUST be idempotent: invoking it when already closed must have no effect.
     */
    @Override void close();
}
