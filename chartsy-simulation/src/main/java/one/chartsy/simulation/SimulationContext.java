/*
 * Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.simulation;

import one.chartsy.data.Series;
import one.chartsy.data.structures.IntMap;
import one.chartsy.trade.strategy.TradingAlgorithmContext;
import org.immutables.value.Value;

@Value.Immutable
public interface SimulationContext extends TradingAlgorithmContext {

    SimulationContext withPartitionSeries(IntMap<Series<?>> seriesMap);
}
