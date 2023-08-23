/*
 * Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.benchmarking;

import one.chartsy.Candle;
import one.chartsy.SymbolResource;
import one.chartsy.TimeFrame;
import one.chartsy.When;
import one.chartsy.data.CandleSeries;
import one.chartsy.data.Series;
import one.chartsy.random.RandomWalk;
import one.chartsy.simulation.SimulationContext;
import one.chartsy.simulation.SimulationResult;
import one.chartsy.simulation.SimulationRunner;
import one.chartsy.simulation.TradingSimulator;
import one.chartsy.simulation.engine.SimpleSimulationRunner;
import one.chartsy.time.Chronological;
import one.chartsy.trade.strategy.Strategy;
import org.openide.util.Lookup;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.DoubleAdder;
import java.util.stream.Collectors;

public class Main3 {

    public static void main(String[] args) throws IOException {
        List<Series<Candle>> seriesList = new ArrayList<>();

        for (int i = 0; i < 1; i++) {
            List<Candle> candles = RandomWalk.candles(Duration.ofMinutes(5), LocalDateTime.of(1900, 1, 1, 0, 0))
                    .limit(10_000_000)
                    .collect(Collectors.toList());
            Collections.reverse(candles);
            seriesList.add(CandleSeries.of(SymbolResource.of("RANDOM", TimeFrame.Period.M15), candles));
        }

        SimulationContext context = Lookup.getDefault().lookup(SimulationContext.class);
        SimulationRunner runner = new SimpleSimulationRunner(context);
        AtomicLong cnt = new AtomicLong();
        DoubleAdder cnt2 = new DoubleAdder();
//        SimulationDriver driver = new SimulationDriver() {
//            @Override public void initSimulation(SimulationContext context) { }
//            @Override public void onTradingDayStart(LocalDate date) { }
//            @Override public void onTradingDayEnd(LocalDate date) { }
//            @Override public void onData(When when, Chronological next, boolean timeTick) { }
//
//            @Override
//            public void onData(When when, Chronological last) {
//                cnt.addAndGet(last.getTime());
//                cnt2.add(((Candle) last).close());
//            }
//        };
        class MyStrategy extends Strategy<Candle> {
            @Override
            public void entryOrders(When when, Chronological data) {
                cnt.addAndGet(data.getTime());
                //cnt2.add(((Candle) data).close());
                //System.out.println(when.current());
            }
        }
        //System.in.read();
        //for (int i = 0; i < 100; i++)
        SimulationResult result = runner.run(seriesList, new TradingSimulator(MyStrategy::new));
        System.out.println(result.testDays());
    }
}
