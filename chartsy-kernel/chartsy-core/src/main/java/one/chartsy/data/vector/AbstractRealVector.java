/*
 * Copyright 2024 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.data.vector;

import one.chartsy.data.RealVector;

import java.util.function.DoubleBinaryOperator;

public abstract class AbstractRealVector implements RealVector {

    @Override
    public double norm() {
        double sumOfSquares = reduce(0.0, (sum, value) -> sum + value * value);
        return Math.sqrt(sumOfSquares);
    }

    @Override
    public double sum() {
        return reduce(0.0, Double::sum);
    }

    @Override
    public double min() {
        return reduce(Double.POSITIVE_INFINITY, Double::min);
    }

    @Override
    public double max() {
        return reduce(Double.NEGATIVE_INFINITY, Double::max);
    }

    @Override
    public double mean() {
        return sum() / size();
    }

    @Override
    public double reduce(double identity, DoubleBinaryOperator function) {
        double result = identity;
        for (int i = 0; i < size(); i++)
            result = function.applyAsDouble(result, get(i));

        return result;
    }
}
