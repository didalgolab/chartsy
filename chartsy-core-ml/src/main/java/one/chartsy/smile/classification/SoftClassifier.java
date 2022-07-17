/*
 * Copyright (c) 2010 Haifeng Li
 * Copyright (c) -2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.smile.classification;

/**
 * Soft classifiers calculate a posteriori probabilities besides the class
 * label of an instance.
 *
 * @param <T> the type of input object
 *
 * @author Haifeng Li
 */
public interface SoftClassifier<T> extends Classifier<T> {
    /**
     * Predicts the class label of an instance and also calculate a posteriori
     * probabilities. Classifiers may NOT support this method since not all
     * classification algorithms are able to calculate such a posteriori
     * probabilities.
     *
     * @param x the instance to be classified.
     * @param posteriori the array to store a posteriori probabilities on output.
     * @return the predicted class label
     */
    int predict(T x, double[] posteriori);

}
