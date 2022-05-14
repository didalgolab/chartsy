/* Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.core;

import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * Represents an instance intended to be executed by a thread. The function
 * might throw a checked exception.
 *
 * @author Mariusz Bernacki
 */
@FunctionalInterface
public interface ThrowingRunnable<T extends Throwable> {

    void run() throws T;

    static Runnable unchecked(ThrowingRunnable<?> target) {
        return () -> {
            try {
                target.run();
            } catch (RuntimeException | Error e) {
                throw e;
            } catch (Throwable x) {
                throw (x instanceof IOException)? new UncheckedIOException((IOException) x) : new UncheckedException(x);
            }
        };
    }

    class UncheckedException extends RuntimeException {
        UncheckedException(Throwable cause) {
            super(cause.toString(), cause);
        }
    }
}
