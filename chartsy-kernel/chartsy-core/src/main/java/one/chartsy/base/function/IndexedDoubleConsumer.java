/*
 * Copyright 2024 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.base.function;

import java.util.Objects;
import java.util.function.DoubleConsumer;

/**
 * Represents an operation that accepts a double-valued input and its corresponding 
 * index. This is a specialized variation of the {@link IndexedConsumer} interface,
 * designed for working with sequences of double values.
 *
 * @see java.util.function.DoubleConsumer
 */
@FunctionalInterface
public interface IndexedDoubleConsumer {

    /**
     * Performs this operation on the given double value and its index.
     *
     * @param value the input double value
     * @param index the index of the input value
     */
    void accept(double value, int index);

    /**
     * Returns a composed {@code IndexedDoubleConsumer} that performs, in sequence, this
     * operation followed by the {@code after} operation. If performing either
     * operation throws an exception, it is relayed to the caller of the composed
     * operation. If performing this operation throws an exception,
     * the {@code after} operation will not be performed.
     *
     * @param after the operation to perform after this operation
     * @return a composed {@code IndexedDoubleConsumer} that performs in sequence this
     * operation followed by the {@code after} operation
     * @throws NullPointerException if {@code after} is null
     */
    default IndexedDoubleConsumer andThen(IndexedDoubleConsumer after) {
        Objects.requireNonNull(after);
        return (value, index) -> { accept(value, index); after.accept(value, index); };
    }

    /**
     * Returns a composed {@code IndexedDoubleConsumer} that performs, in sequence, this
     * operation followed by the {@code after} operation using only the value.
     * The index is not passed to the {@code after} operation.
     *
     * @param after the {@code DoubleConsumer} operation to perform after this operation
     * @return a composed {@code IndexedDoubleConsumer} that performs in sequence this
     * operation followed by the {@code after} operation
     * @throws NullPointerException if {@code after} is null
     */
    default IndexedDoubleConsumer andThenIgnoringIndex(DoubleConsumer after) {
        Objects.requireNonNull(after);
        return (value, index) -> { accept(value, index); after.accept(value); };
    }

    /**
     * Returns an {@code IndexedDoubleConsumer} that wraps the given {@code DoubleConsumer},
     * ignoring the index argument. The method provides interoperability between
     * {@code DoubleConsumer} and {@code IndexedDoubleConsumer}.
     *
     * @param action the {@code DoubleConsumer} to wrap
     * @return an {@code IndexedDoubleConsumer} that ignores the index argument
     * @throws NullPointerException if {@code action} is null
     */
    static IndexedDoubleConsumer ignoringIndex(DoubleConsumer action) {
        Objects.requireNonNull(action);
        return (value, index) -> action.accept(value);
    }
}
