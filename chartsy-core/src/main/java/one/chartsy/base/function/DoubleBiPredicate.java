/*
 * Copyright 2024 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.base.function;

import java.util.function.BiPredicate;

/**
 * Represents a predicate (boolean-valued function) of two {@code double}-valued arguments.
 * This is the {@code double}-consuming primitive type specialization of {@link BiPredicate}.
 *
 * <p>This is a functional interface whose functional method is {@link #test(double, double)}.
 */
@FunctionalInterface
public interface DoubleBiPredicate {

    /**
     * Evaluates this predicate on the given arguments.
     *
     * @param left the first input argument
     * @param right the second input argument
     * @return {@code true} if the input arguments match the predicate, otherwise {@code false}
     */
    boolean test(double left, double right);
}