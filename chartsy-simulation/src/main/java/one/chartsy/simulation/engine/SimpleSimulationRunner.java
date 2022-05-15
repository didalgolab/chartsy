/*
 * Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.simulation.engine;

import one.chartsy.collections.PriorityMap;
import one.chartsy.data.ChronologicalIterator;
import one.chartsy.data.Series;
import one.chartsy.simulation.SimulationContext;
import one.chartsy.simulation.SimulationDriver;
import one.chartsy.simulation.SimulationResult;
import one.chartsy.simulation.SimulationRunner;
import one.chartsy.simulation.data.TradingData;
import one.chartsy.time.Chronological;
import one.chartsy.trade.strategy.ExitState;

import java.time.LocalDate;
import java.util.List;

public class SimpleSimulationRunner implements SimulationRunner {
    private final SimulationContext context;

    public SimpleSimulationRunner(SimulationContext context) {
        this.context = context;
    }

    @Override
    public SimulationResult run(List<? extends Series<?>> datasets, SimulationDriver strategy) {
        SimulationContext context = this.context.withDataSeries(datasets);
        strategy.initSimulation(context);
        PriorityMap<Chronological, ChronologicalIterator<?>> map = TradingData.priorityMap(context, datasets);

        if (!map.isEmpty()) {
            LocalDate currDate = map.peekKey().getDate();
            strategy.onTradingDayChange(null, currDate);
            long nextDayTime = Chronological.toEpochMicros(currDate.plusDays(1).atStartOfDay());
            long eventTime = 0;
            while (!map.isEmpty()) {
                // This is a forehand quote, not yet reflected in ChronologicalIterator
                Chronological event = map.peekKey();

                // Obtain When pointer for the Symbol
                ChronologicalIterator<?> when = map.remove();

                // e.g. Update Equity for the current bar, before shifting When
                long et = eventTime;
                eventTime = event.getTime();
                if (when.current() != null)
                    strategy.onData(when, event, (eventTime > et));

                // Check for a trading day boundary
                if (eventTime > nextDayTime) {
                    strategy.onTradingDayChange(currDate, currDate = event.getDate());
                    nextDayTime = Chronological.toEpochMicros(currDate.plusDays(1).atStartOfDay());
                }

                // Shift When pointer
                when.next();
                // Current bar scripts
                strategy.onData(when, event);
                // Obtain next event and put in the queue
                if (when.hasNext())
                    map.put(when.peek(), when);
            }
            // Generate the last end-of-day event
            strategy.onTradingDayChange(currDate, null);
        }
        return strategy.postSimulation(ExitState.COMPLETED);
    }
}
