/*
 * Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.core.ml;

import java.util.Optional;

public interface PredictorFunction<P, R> {

    R predict(P data);

    <T> Optional<T> unwrap(Class<T> type);
}
