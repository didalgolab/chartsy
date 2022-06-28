/*
 * Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.core.ml;

import java.util.Objects;
import java.util.Optional;

public abstract class AbstractPredictorFunction<I, O, M> implements PredictorFunction<I, O> {

    private final M model;

    protected AbstractPredictorFunction(M model) {
        Objects.requireNonNull(model, "model");
        this.model = model;
    }

    public final M getModel() {
        return model;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> Optional<T> unwrap(Class<T> type) {
        return type.isInstance(model)? Optional.of((T) model) : Optional.empty();
    }
}
