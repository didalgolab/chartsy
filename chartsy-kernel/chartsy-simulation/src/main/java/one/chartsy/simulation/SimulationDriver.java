/*
 * Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.simulation;

import one.chartsy.When;
import one.chartsy.simulation.services.SimulationResultBuilderFactory;
import one.chartsy.time.Chronological;
import one.chartsy.trade.strategy.ExitState;
import org.openide.util.Lookup;

import java.time.LocalDate;
import java.util.Map;

public interface SimulationDriver {
    void initSimulation(SimulationContext context);
    void onTradingDayChange(LocalDate endDate, LocalDate startDate);

    void onData(When when, Chronological next, boolean timeTick);
    void onData(When when, Chronological last);

    default SimulationResult postSimulation(ExitState state) {
        return Lookup.getDefault().lookup(SimulationResultBuilderFactory.class).create(Map.of()).build();
    }
}
