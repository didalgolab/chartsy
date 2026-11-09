/* Copyright 2026 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.core.ml.online;

import one.chartsy.core.ml.ToDoublePredictorFunction;

/** Fits a scalar predictor from completed examples, in oldest-to-newest order. */
@FunctionalInterface
public interface ModelTrainer {
    ToDoublePredictorFunction<double[]> fit(double[][] features, double[] targets);
}
