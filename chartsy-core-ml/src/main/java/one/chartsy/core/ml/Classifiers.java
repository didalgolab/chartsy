/*
 * Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.core.ml;

import one.chartsy.data.Dataset;
import one.chartsy.data.DoubleDataset;
import one.chartsy.smile.classification.KNN;
import one.chartsy.smile.math.distance.Distance;
import one.chartsy.smile.math.distance.EuclideanDistance;
import one.chartsy.util.Pair;

import java.util.Map;
import java.util.Optional;

public class Classifiers {

    public static int[] getTargetValues(Dataset<? extends Pair<?, Integer>> trainingDataset) {
        int count = trainingDataset.length();
        int[] y = new int[count];
        for (int i = 0; i < count; i++)
            y[i] = trainingDataset.get(i).getRight();

        return y;
    }

    public static ClassifierFunction<DoubleDataset, Integer> nearestNeighbors(Dataset<Pair<DoubleDataset, Integer>> data, Map<String, Object> properties) {
        double[][] x = Predictors.getInputValues(data);
        int[] y = getTargetValues(data);

        Distance<double[]> distance = (Distance<double[]>) properties.getOrDefault("distanceFunction", EuclideanDistance.INSTANCE);
        int neighborsNumber = Optional.ofNullable((Number) properties.get("neighborsNumber")).orElse(4).intValue();
        KNN<double[]> knn = new KNN.Trainer<>(distance, neighborsNumber).train(x, y);

        return new AbstractClassifierFunction<>(knn) {
            @Override
            public Integer classify(DoubleDataset data) {
                return getModel().predict(data.toArray());
            }
        };
    }
}
