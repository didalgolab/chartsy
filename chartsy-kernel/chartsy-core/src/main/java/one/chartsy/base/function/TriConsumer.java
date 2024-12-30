package one.chartsy.base.function;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * An operation that accepts three input arguments and returns no result.
 * This is the three-arity specialization of {@link Consumer}.
 *
 * <p>This is a functional interface whose functional method is {@link #accept(Object, Object, Object)}.
 *
 * @param <T> the type of the first argument to the operation
 * @param <U> the type of the second argument to the operation
 * @param <V> the type of the third argument to the operation
 *
 * @see Consumer
 * @see BiConsumer
 */
@FunctionalInterface
public interface TriConsumer<T, U, V> {

    /**
     * Performs this operation on the given arguments.
     *
     * @param t the first input argument
     * @param u the second input argument
     * @param v the third input argument
     */
    void accept(T t, U u, V v);
}