/*
 * Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.core.ml;

import java.util.Objects;

public abstract class AbstractClassifierFunction<I, O, M> implements ClassifierFunction<I, O> {

    private final M model;

    protected AbstractClassifierFunction(M model) {
        Objects.requireNonNull(model, "model");
        this.model = model;
    }

    public final M getModel() {
        return model;
    }
}
