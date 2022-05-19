/*
 * Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.data.function;

import java.util.function.BiConsumer;
import java.util.function.ObjIntConsumer;

/**
 * Represents an operation that accepts an {@code int}-valued and a
 * object-valued argument, and returns no result.  This is the
 * {@code (int, reference)} specialization of {@link BiConsumer} and
 * a mirror of {@link ObjIntConsumer}. Unlike most other functional
 * interfaces, {@code IntObjConsumer} is expected to operate via side-effects.
 *
 * @param <T> the type of the object argument to the operation
 */
@FunctionalInterface
public interface IntObjConsumer<T> {

    /**
     * Performs this operation on the given arguments.
     *
     * @param i the first input argument
     * @param value the second input argument
     */
    void accept(int i, T value);
}
