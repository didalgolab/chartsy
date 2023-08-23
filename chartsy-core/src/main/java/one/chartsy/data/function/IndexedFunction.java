/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.data.function;

@FunctionalInterface
public interface IndexedFunction<T, R> {

    /**
     * Applies this function to the given arguments.
     *
     * @param value
     *            the function object argument
     * @param index
     *            the function index argument
     * @return the function result
     */
    R apply(T value, int index);
}
