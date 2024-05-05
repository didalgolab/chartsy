/*
 * Copyright 2024 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.base.function;

import java.util.Objects;
import java.util.function.IntConsumer;

/**
 * Represents an operation that accepts an integer value (int) and its corresponding 
 * index. This is a specialized variation of the {@link IndexedConsumer} interface, 
 * designed for working with sequences of integer values.
 *
 * @see IntConsumer
 * @see IndexedConsumer
 */
@FunctionalInterface
public interface IndexedIntConsumer {

    /**
     * Performs this operation on the given int value and its index.
     *
     * @param value the input int value
     * @param index the index of the input value
     */
    void accept(int value, int index);

    /**
     * Returns a composed {@code IndexedIntConsumer} that performs, in sequence, this
     * operation followed by the {@code after} operation. If performing either
     * operation throws an exception, it is relayed to the caller of the composed
     * operation. If performing this operation throws an exception,
     * the {@code after} operation will not be performed.
     *
     * @param after the operation to perform after this operation
     * @return a composed {@code IndexedIntConsumer} that performs in sequence this
     * operation followed by the {@code after} operation
     * @throws NullPointerException if {@code after} is null
     */
    default IndexedIntConsumer andThen(IndexedIntConsumer after) {
        Objects.requireNonNull(after);
        return (value, index) -> { accept(value, index); after.accept(value, index); };
    }

    /**
     * Returns a composed {@code IndexedIntConsumer} that performs, in sequence, this
     * operation followed by the {@code after} operation using only the value.
     * The index is not passed to the {@code after} operation.
     *
     * @param after the {@code IntConsumer} operation to perform after this operation
     * @return a composed {@code IndexedIntConsumer} that performs in sequence this
     * operation followed by the {@code after} operation
     * @throws NullPointerException if {@code after} is null
     */
    default IndexedIntConsumer andThenIgnoringIndex(IntConsumer after) {
        Objects.requireNonNull(after);
        return (value, index) -> { accept(value, index); after.accept(value); };
    }

    /**
     * Returns an {@code IndexedIntConsumer} that wraps the given {@code IntConsumer},
     * ignoring the index argument. The method provides interoperability between
     * {@code IntConsumer} and {@code IndexedIntConsumer}.
     *
     * @param action the {@code IntConsumer} to wrap
     * @return an {@code IndexedIntConsumer} that ignores the index argument
     * @throws NullPointerException if {@code action} is null
     */
    static IndexedIntConsumer ignoringIndex(IntConsumer action) {
        Objects.requireNonNull(action);
        return (value, index) -> action.accept(value);
    }
}
