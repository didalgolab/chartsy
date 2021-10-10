package one.chartsy.core;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.function.Supplier;

/**
 * Represents a function that accepts zero arguments and returns a value. The function
 * might throw a checked exception.
 *
 * @author Mariusz Bernacki
 */
@FunctionalInterface
public interface ThrowingSupplier<V, T extends Throwable> {

    V get() throws T;

    static <V> Supplier<V> unchecked(ThrowingSupplier<V,?> target) {
        return () -> {
            try {
                return target.get();
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
