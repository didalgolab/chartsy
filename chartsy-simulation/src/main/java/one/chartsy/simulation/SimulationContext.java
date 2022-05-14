/* Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.simulation;

import one.chartsy.data.Series;
import one.chartsy.trade.strategy.TradingAlgorithmContext;
import org.immutables.value.Value;

import java.util.List;

@Value.Immutable
public interface SimulationContext extends TradingAlgorithmContext {

    SimulationContext withDataSeries(List<? extends Series<?>> datasets);
}
