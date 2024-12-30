/*
 * Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.trade.strategy;

import java.util.Collection;
import java.util.Optional;

public interface TradingAlgorithmSet {

    Collection<TradingAlgorithm> findAll();

    Optional<TradingAlgorithm> get(String name);

    <T extends TradingAlgorithm> T newInstance(String name, TradingAlgorithmContext context, TradingAlgorithmFactory<T> factory);
}
