/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.core;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.function.Function;

/**
 * Represents a function that accepts one argument and returns a value. The function
 * might throw a checked exception.
 *
 * @author Mariusz Bernacki
 */
@FunctionalInterface
public interface ThrowingFunction <P, R, T extends Throwable> {

    R apply(P p) throws T;

    static <P,R> Function<P,R> unchecked(ThrowingFunction<P,R,?> target) {
        return p -> {
            try {
                return target.apply(p);
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
