/* Copyright 2026 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.core.ml.online;

import one.chartsy.core.ml.PredictorFunction;

/** Fits one predictor with multiple output coordinates from completed chronological examples. */
@FunctionalInterface
public interface VectorModelTrainer {
    PredictorFunction<double[], double[]> fit(double[][] features, double[][] targets);
}
