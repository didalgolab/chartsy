/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.data.function;

public interface RollingFunction<T> {

    /**
     * Calculates the next value of the function for the specified index.
     *
     * @param index
     *            the index of the value to calculate
     * @return the function result
     */
    T calculateNext(int index);
}
