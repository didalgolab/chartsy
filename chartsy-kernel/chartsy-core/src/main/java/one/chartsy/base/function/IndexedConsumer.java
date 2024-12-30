/*
 * Copyright 2024 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.base.function;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Represents an operation that accepts an object of a specific type and its
 * corresponding index within a sequence. This interface is a specialized
 * extension of the {@link java.util.function.Consumer} interface.
 *
 * @param <T> the type of elements being consumed
 * @see Consumer
 */
@FunctionalInterface
public interface IndexedConsumer<T> {

    /**
     * Performs this operation on the given object and its index.
     *
     * @param value the input object
     * @param index the index of the input object
     */
    void accept(T value, int index);

    /**
     * Returns a composed {@code IndexedConsumer} that performs, in sequence, this
     * operation followed by the {@code after} operation. If performing either
     * operation throws an exception, it is relayed to the caller of the composed
     * operation. If performing this operation throws an exception,
     * the {@code after} operation will not be performed.
     *
     * @param after the operation to perform after this operation
     * @return a composed {@code IndexedConsumer} that performs in sequence this
     * operation followed by the {@code after} operation
     * @throws NullPointerException if {@code after} is null
     */
    default IndexedConsumer<T> andThen(IndexedConsumer<? super T> after) {
        Objects.requireNonNull(after);
        return (value, index) -> { accept(value, index); after.accept(value, index); };
    }

    /**
     * Returns a composed {@code IndexedConsumer} that performs, in sequence, this
     * operation followed by the {@code after} operation using only the value.
     * The index is not passed to the {@code after} operation.
     *
     * @param after the {@code Consumer} operation to perform after this operation
     * @return a composed {@code IndexedConsumer} that performs in sequence this
     * operation followed by the {@code after} operation
     * @throws NullPointerException if {@code after} is null
     */
    default IndexedConsumer<T> andThenIgnoringIndex(Consumer<? super T> after) {
        Objects.requireNonNull(after);
        return (value, index) -> { accept(value, index); after.accept(value); };
    }

    /**
     * Returns an {@code IndexedConsumer} that wraps the given {@code Consumer},
     * ignoring the index argument. The method provides interoperability between
     * {@code Consumer} and {@code IndexedConsumer}.
     *
     * @param action the {@code Consumer} to wrap
     * @param <T> the type of the input value
     * @return an {@code IndexedConsumer} that ignores the index argument
     * @throws NullPointerException if {@code action} is null
     */
    static <T> IndexedConsumer<T> ignoringIndex(Consumer<? super T> action) {
        Objects.requireNonNull(action);
        return (value, index) -> action.accept(value);
    }
}
