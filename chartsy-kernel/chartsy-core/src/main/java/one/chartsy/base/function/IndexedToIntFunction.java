/*
 * Copyright 2024 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.base.function;

import java.util.function.ToIntFunction;

/**
 * Represents a function that accepts an object of a specific type and its
 * corresponding index within a sequence, and produces an integer result.
 * This is a specialized variation of the {@link IndexedFunction} interface,
 * designed for working with functions that return integer values.
 *
 * @param <T> the type of the input to the function
 * @see IndexedFunction
 * @see ToIntFunction
 */
@FunctionalInterface
public interface IndexedToIntFunction<T> {

    /**
     * Applies this function to the given object and its index.
     *
     * @param value the input object
     * @param index the index of the input object
     * @return the integer result of the function
     */
    int applyAsInt(T value, int index);
}