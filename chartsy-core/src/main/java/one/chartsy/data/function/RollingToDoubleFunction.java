/* Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.data.function;

public interface RollingToDoubleFunction {

    /**
     * Calculates the next value of the function for the specified index.
     *
     * @param index
     *            the index of the value to calculate
     * @return the function result
     */
    double calculateNext(int index);
}
