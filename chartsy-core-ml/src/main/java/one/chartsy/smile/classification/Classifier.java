/*
 * Copyright (c) 2010 Haifeng Li
 * Copyright (c) -2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.smile.classification;

import java.io.Serializable;

/**
 * A classifier assigns an input object into one of a given number of categories.
 * The input object is formally termed an instance, and the categories are
 * termed classes. The instance is usually described by a vector of features,
 * which together constitute a description of all known characteristics of the
 * instance.
 * <p>
 * Classification normally refers to a supervised procedure, i.e. a procedure
 * that produces an inferred function to predict the output value of new
 * instances based on a training set of pairs consisting of an input object
 * and a desired output value. The inferred function is called a classifier
 * if the output is discrete or a regression function if the output is
 * continuous.
 * 
 * @param <T> the type of input object
 * 
 * @author Haifeng Li
 */
public interface Classifier<T> extends Serializable {
    /**
     * Predicts the class label of an instance.
     * 
     * @param x the instance to be classified.
     * @return the predicted class label.
     */
    int predict(T x);

    /**
     * Predicts the class labels of an array of instances.
     *
     * @param x the instances to be classified.
     * @return the predicted class labels.
     */
    default int[] predict(T[] x) {
        int[] y = new int[x.length];
        for (int i = 0; i < y.length; i++) {
            y[i] = predict(x[i]);
        }
        return y;
    }
}
