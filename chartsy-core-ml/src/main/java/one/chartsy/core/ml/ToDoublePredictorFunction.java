/*
 * Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.core.ml;

public interface ToDoublePredictorFunction<I> extends PredictorFunction<I, Double> {

    double predictAsDouble(I data);

    @Override
    default Double predict(I data) {
        return predictAsDouble(data);
    }
}
