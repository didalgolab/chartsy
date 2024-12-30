/*
 * Copyright 2024 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.base.function;

import java.util.Objects;
import java.util.function.Function;

/**
 * Represents a function that accepts an object of a specific type and its
 * corresponding index within a sequence, and produces a result. This interface
 * is a specialized extension of the {@link java.util.function.Function} interface.
 *
 * @param <T> the type of the input to the function
 * @param <R> the type of the result of the function
 * @see java.util.function.Function
 */
@FunctionalInterface
public interface IndexedFunction<T, R> {

    /**
     * Applies this function to the given object and its index.
     *
     * @param value the input object
     * @param index the index of the input object
     * @return the result of the function
     */
    R apply(T value, int index);

    /**
     * Returns a composed function that first applies the {@code before}
     * function to its input, and then applies this function to the result.
     * If evaluation of either function throws an exception, it is relayed to
     * the caller of the composed function.
     *
     * @param <V> the type of input to the {@code before} function, and to the
     *            composed function
     * @param before the function to apply before this function is applied
     * @return a composed function that first applies the {@code before}
     * function and then applies this function
     * @throws NullPointerException if before is null
     */
    default <V> IndexedFunction<V, R> compose(IndexedFunction<? super V, ? extends T> before) {
        Objects.requireNonNull(before);
        return (V v, int index) -> apply(before.apply(v, index), index);
    }

    /**
     * Returns a composed function that first applies the {@code before}
     * function to its input (ignoring the index), and then applies this function to the result.
     * If evaluation of either function throws an exception, it is relayed to
     * the caller of the composed function.
     *
     * @param <V> the type of input to the {@code before} function, and to the
     *            composed function
     * @param before the function to apply before this function is applied
     * @return a composed function that first applies the {@code before}
     * function and then applies this function
     * @throws NullPointerException if before is null
     */
    default <V> IndexedFunction<V, R> composeIgnoringIndex(Function<? super V, ? extends T> before) {
        Objects.requireNonNull(before);
        return (V v, int index) -> apply(before.apply(v), index);
    }

    /**
     * Returns a composed function that first applies this function to
     * its input, and then applies the {@code after} function to the result.
     * If evaluation of either function throws an exception, it is relayed to
     * the caller of the composed function.
     *
     * @param <V> the type of output of the {@code after} function, and of the
     *            composed function
     * @param after the function to apply after this function is applied
     * @return a composed function that first applies this function and then
     * applies the {@code after} function
     * @throws NullPointerException if after is null
     */
    default <V> IndexedFunction<T, V> andThen(IndexedFunction<? super R, ? extends V> after) {
        Objects.requireNonNull(after);
        return (T t, int index) -> after.apply(apply(t, index), index);
    }

    /**
     * Returns a composed function that first applies this function to
     * its input, and then applies the {@code after} function to the result (ignoring the index).
     * If evaluation of either function throws an exception, it is relayed to
     * the caller of the composed function.
     *
     * @param <V> the type of output of the {@code after} function, and of the
     *            composed function
     * @param after the function to apply after this function is applied
     * @return a composed function that first applies this function and then
     * applies the {@code after} function
     * @throws NullPointerException if after is null
     */
    default <V> IndexedFunction<T, V> andThenIgnoringIndex(Function<? super R, ? extends V> after) {
        Objects.requireNonNull(after);
        return (T t, int index) -> after.apply(apply(t, index));
    }

    /**
     * Returns a function that always returns its input argument.
     *
     * @param <T> the type of the input and output objects to the function
     * @return a function that always returns its input argument
     */
    static <T> IndexedFunction<T, T> identity() {
        return (t, index) -> t;
    }
}