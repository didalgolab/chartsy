/*
 * Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.data.provider.file;

import one.chartsy.*;
import one.chartsy.data.*;
import one.chartsy.data.provider.DataProviders;
import one.chartsy.data.provider.FlatFileDataProvider;
import one.chartsy.simulation.SimulationContext;
import one.chartsy.simulation.SimulationResult;
import one.chartsy.simulation.SimulationRunner;
import one.chartsy.simulation.TradingSimulator;
import one.chartsy.simulation.engine.SimpleSimulationRunner;
import one.chartsy.time.Chronological;
import one.chartsy.trade.*;
import one.chartsy.trade.strategy.HierarchicalTradingAlgorithm;
import one.chartsy.trade.strategy.Strategy;
import org.openide.util.Lookup;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class StooqFlatFileDataBasedStrategy {

    public static void main(String[] args) throws IOException {
        FlatFileDataProvider dataProvider = FlatFileFormat.STOOQ
                .newDataProvider(Path.of("C:/Downloads/d_pl_txt(6).zip"));

        List<SymbolIdentity> stocks = dataProvider.listSymbols(new SymbolGroup("/data/daily/pl/wse stocks"));
        List<CandleSeries> seriesList = DataProviders.getHistoricalCandles(dataProvider, TimeFrame.Period.DAILY, stocks);
        System.out.println(seriesList.size());


        AtomicInteger strategyInstanceCount = new AtomicInteger();
        AtomicInteger dataPointCount = new AtomicInteger();
        class MyStrategy extends Strategy<Candle> {
            private HierarchicalTradingAlgorithm parent = lookup(HierarchicalTradingAlgorithm.class);
            private final Set<LocalDate> dates = globalVariable("dates", HashSet::new);

            MyStrategy() {
                strategyInstanceCount.incrementAndGet();
            }

            @Override
            public void entryOrders(When when, Chronological data) {
                dataPointCount.incrementAndGet();

            }

            @Override
            public void onExecution(Execution execution) {
                System.out.println(":: EXECUTION: " + execution);
                super.onExecution(execution);
            }

            private final AtomicBoolean flag = new AtomicBoolean();

            @Override
            public void entryOrderFilled(Order order) {
                System.out.println(":: ENTRY ORDER FILLED: " + order);
                super.entryOrderFilled(order);
            }

            @Override
            public void onTradingDayStart(LocalDate date) {
                super.onTradingDayStart(date);
//                if (!flag.get() && dates.add(date) && parent.getMarketUniverse().activeSymbols().size() >= parent./3) {
//                    //System.out.println(date + " " + metaStrategy.activeSymbolCount() + " of " + metaStrategy.totalSymbolCount());
//                    for (Series<?> series : context.partitionSeries().values())
//                        if (!series.isEmpty() && series.getFirst().getDate().equals(date.minusDays(1))) {
//                            System.out.println(">> " + series.getResource());
//                            submitOrder(new Order(series.getResource().symbol(), OrderType.MARKET, Order.Side.BUY, 1.0));
//                        }
//
//                    flag.set(true);
//                }
            }
        }
        TradingSimulator simulator = new TradingSimulator(MyStrategy::new);
        SimulationContext context = Lookup.getDefault().lookup(SimulationContext.class);
        SimulationRunner runner = new SimpleSimulationRunner(context);
        SimulationResult result = runner.run(seriesList, simulator);

        System.out.println("Strategies:" + strategyInstanceCount);
        System.out.println("DataPoints:" + dataPointCount);
        System.out.println("Test Days: " + result.testDays());
        System.out.println("Transactions: " + result.transactions());
        System.out.println("Balance: " + simulator.getMainAccount().getEquity());
        System.out.println("Remaining orders: " + result.remainingOrders());
        System.out.println("Remaining order count: " + result.remainingOrderCount());
    }
}
