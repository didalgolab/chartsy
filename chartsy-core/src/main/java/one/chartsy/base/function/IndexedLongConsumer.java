/*
 * Copyright 2024 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.base.function;

import java.util.Objects;
import java.util.function.LongConsumer;

/**
 * Represents an operation that accepts a long-valued input and its corresponding 
 * index. This is a specialized variation of the {@link IndexedConsumer} interface,
 * designed for working with sequences of long values.
 *
 * @see LongConsumer
 * @see IndexedConsumer
 */
@FunctionalInterface
public interface IndexedLongConsumer {

    /**
     * Performs this operation on the given long value and its index.
     *
     * @param value the input long value
     * @param index the index of the input value
     */
    void accept(long value, int index);

    /**
     * Returns a composed {@code IndexedLongConsumer} that performs, in sequence, this
     * operation followed by the {@code after} operation. If performing either
     * operation throws an exception, it is relayed to the caller of the composed
     * operation. If performing this operation throws an exception,
     * the {@code after} operation will not be performed.
     *
     * @param after the operation to perform after this operation
     * @return a composed {@code IndexedLongConsumer} that performs in sequence this
     * operation followed by the {@code after} operation
     * @throws NullPointerException if {@code after} is null
     */
    default IndexedLongConsumer andThen(IndexedLongConsumer after) {
        Objects.requireNonNull(after);
        return (value, index) -> { accept(value, index); after.accept(value, index); };
    }

    /**
     * Returns a composed {@code IndexedLongConsumer} that performs, in sequence, this
     * operation followed by the {@code after} operation using only the value.
     * The index is not passed to the {@code after} operation.
     *
     * @param after the {@code LongConsumer} operation to perform after this operation
     * @return a composed {@code IndexedLongConsumer} that performs in sequence this
     * operation followed by the {@code after} operation
     * @throws NullPointerException if {@code after} is null
     */
    default IndexedLongConsumer andThenIgnoringIndex(LongConsumer after) {
        Objects.requireNonNull(after);
        return (value, index) -> { accept(value, index); after.accept(value); };
    }

    /**
     * Returns an {@code IndexedLongConsumer} that wraps the given {@code LongConsumer},
     * ignoring the index argument. The method provides interoperability between
     * {@code LongConsumer} and {@code IndexedLongConsumer}.
     *
     * @param action the {@code LongConsumer} to wrap
     * @return an {@code IndexedLongConsumer} that ignores the index argument
     * @throws NullPointerException if {@code action} is null
     */
    static IndexedLongConsumer ignoringIndex(LongConsumer action) {
        Objects.requireNonNull(action);
        return (value, index) -> action.accept(value);
    }
}
