/*
 * Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.trade.strategy;

import one.chartsy.data.Series;
import one.chartsy.scheduling.EventScheduler;
import one.chartsy.time.Clock;
import one.chartsy.trade.OrderContext;
import one.chartsy.trade.TradingOptions;
import one.chartsy.trade.TradingService;
import org.immutables.value.Value;
import org.openide.util.Lookup;

import java.util.List;
import java.util.Optional;

/**
 * A context containing trading algorithms' name and id, and providing a various container services to the algorithm.
 *
 */
@Value.Immutable
public interface TradingAlgorithmContext extends Lookup.Provider, OrderContext {

    String name();

    Clock clock();

    EventScheduler scheduler();

    TradingService tradingService();

    TradingOptions options();

    List<? extends Series<?>> dataSeries();

    StrategyConfiguration configuration();

    Optional<Object> strategyPartition();

    TradingAlgorithmSet tradingAlgorithms();

}
