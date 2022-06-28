/*
 * Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.core.ml;

import one.chartsy.data.Dataset;
import one.chartsy.data.DoubleDataset;
import one.chartsy.util.Pair;

public final class Predictors {
    private Predictors() { }

    public static double[][] getInputValues(Dataset<? extends Pair<DoubleDataset, ?>> trainingDataset) {
        int count = trainingDataset.length();
        double[][] x = new double[count][];
        for (int i = 0; i < count; i++)
            x[i] = trainingDataset.get(i).getLeft().toArray();
        return x;
    }

    public static double[] getTargetValues(Dataset<? extends Pair<?, Double>> trainingDataset) {
        int count = trainingDataset.length();
        double[] y = new double[count];
        for (int i = 0; i < count; i++)
            y[i] = trainingDataset.get(i).getRight();

        return y;
    }
}
