/* Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.data;

/**
 * An AdjustmentMethod is an option for methods RandomWalk.bootstrap and other
 * similar that specifies what adjustment strategy should be used.
 * 
 * @author Mariusz Bernacki
 *
 */
@FunctionalInterface
public interface AdjustmentFunction {
    
    /**
     * Applies the scaling to the given coordinate point. Extrapolates a {@code y1}
     * based on the specified {@code x1} start point and a given sample vector
     * {@code (x0, y0)}
     * 
     * @param x0
     *            the sample vector start point
     * @param y0
     *            the sample vector end point
     * @param x1
     *            the target vector start point
     * @return the target vector end point
     */
    double calculate(double x0, double y0, double x1);

}
