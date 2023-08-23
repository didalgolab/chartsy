/*
 * Copyright (c) -2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package one.chartsy.smile.math.distance;

/**
 * A metric function defines a distance between elements of a set. Besides
 * non-negativity, isolation, and symmetry, it also has to satisfy triangular
 * inequality.
 * <ul>
 * <li> non-negativity: d(x, y) &ge; 0
 * <li> isolation: d(x, y) = 0 if and only if x = y
 * <li> symmetry: d(x, y) = d(x, y)
 * <li> triangular inequality: d(x, y) + d(y, z) &ge; d(x, z).
 * </ul>.
 *
 * @author Haifeng Li
 */
public interface Metric<T> extends Distance<T> {

}
