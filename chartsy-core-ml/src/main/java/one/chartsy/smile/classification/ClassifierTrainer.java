/*
 * Copyright (c) 2010 Haifeng Li
 * Copyright (c) -2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.smile.classification;

import one.chartsy.smile.data.Attribute;

/**
 * Abstract classifier trainer.
 * 
 * @param <T> the type of input object.
 * 
 * @author Haifeng Li
 */
public abstract class ClassifierTrainer <T> {
    /**
     * The feature attributes. This is optional since most classifiers can only
     * work on real-valued attributes.
     */
    Attribute[] attributes;
    
    /**
     * Constructor.
     */
    public ClassifierTrainer() {
        
    }
    
    /**
     * Constructor.
     * @param attributes the attributes of independent variable.
     */
    public ClassifierTrainer(Attribute[] attributes) {
        this.attributes = attributes;
    }
    
    /**
     * Sets feature attributes. This is optional since most classifiers can only
     * work on real-valued attributes.
     * 
     * @param attributes the feature attributes.
     */
    public void setAttributes(Attribute[] attributes) {
        this.attributes = attributes;
    }
    
    /**
     * Learns a classifier with given training data.
     * 
     * @param x the training instances.
     * @param y the training labels.
     * @return a trained classifier.
     */
    public abstract Classifier<T> train(T[] x, int[] y);
}
