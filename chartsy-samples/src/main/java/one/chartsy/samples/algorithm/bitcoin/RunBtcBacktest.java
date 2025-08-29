/* Copyright 2025 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.samples.algorithm.bitcoin;

import one.chartsy.data.DataSubscription;
import one.chartsy.data.provider.FlatFileDataProvider;
import one.chartsy.data.provider.file.FlatFileFormat;
import one.chartsy.data.provider.file.SimpleCandleLineMapper;
import one.chartsy.messaging.MarketEvent;
import one.chartsy.simulation.engine.AlgorithmBacktestRunner;
import one.chartsy.simulation.engine.FlatFileDataMarketSupplier;
import one.chartsy.simulation.engine.MarketSupplierFactory;
import one.chartsy.trade.Order;
import one.chartsy.trade.algorithm.AbstractAlgorithm;
import one.chartsy.trade.algorithm.AlgorithmContext;
import one.chartsy.trade.algorithm.AlgorithmFactory;
import one.chartsy.trade.algorithm.data.InstrumentDataFactory;
import one.chartsy.trade.algorithm.data.SimpleInstrumentPrices;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static one.chartsy.core.io.ResourcePaths.pathToResource;

public class RunBtcBacktest {

    public static void main(String[] args) throws Exception {

        // Describe the CSV layout (skip header, comma delimiter, yyyy-MM-dd date format).
        FlatFileFormat format = FlatFileFormat.builder()
                .skipFirstLines(1)
                .lineMapper(new SimpleCandleLineMapper.Type(
                        ',', List.of("DATE", "OPEN", "HIGH", "LOW", "CLOSE", "VOLUME"),
                        DateTimeFormatter.ofPattern("yyyy-MM-dd")))
                .build();

        // Create provider for the BTC file.
        FlatFileDataProvider provider = new FlatFileDataProvider(format, pathToResource("BTC_DAILY.zip"));

        // Subscribe to all symbols in the file (only BTC here).
        DataSubscription subscription = DataSubscription.SUBSCRIBED_TO_ALL;

        // Factories for algorithm and market data.
        AlgorithmFactory<MyAlgorithm> algoFactory = MyAlgorithm::new;
        LocalDate startDate = LocalDate.parse("2011-06-01");
        MarketSupplierFactory marketFactory =
                () -> new FlatFileDataMarketSupplier(provider, subscription,
                        startDate.atStartOfDay().atZone(ZoneOffset.UTC).toInstant());

        // Run the backtest.
        var backtestResult = new AlgorithmBacktestRunner().run(algoFactory, marketFactory, "ALGO");
        System.out.println("Backtest Result: " + backtestResult);
        System.out.println("Sharpe ratio: " + backtestResult.equitySummary().getAnnualSharpeRatio());

        // Bootstrap the results.
        /*
        var bootstrap = new BootstrappedEstimator<EquitySummaryStatistics>();
        marketFactory = new BootstrappedTradeBarFactory(marketFactory);
        for (int i = 0; i <= 10_000; i++) {
            var log = i % 1_000 == 0;
            if (log) System.out.println("=== RUN " + (i+1) + " ===");
            var bootstrapResult = new AlgorithmBacktestRunner().run(algoFactory, marketFactory, "ALGO");
            bootstrap.accept(bootstrapResult.equitySummary());
            //System.out.println("Backtest backtestResult: " + backtestResult);
            if (log) System.out.println("Bootstrap Sharpe: " + bootstrapResult.equitySummary().getAnnualSharpeRatio());
            if (log) System.out.println("Bootstrap z-Score: " +
                            bootstrap.estimate(backtestResult.equitySummary()).get("annualSharpeRatio"));
        }
        */
    }

    static class MyAlgorithm extends AbstractAlgorithm {

        public MyAlgorithm(AlgorithmContext context) {
            super(context);
        }

        @Override
        protected InstrumentDataFactory createInstrumentDataFactory() {
            return SimpleInstrumentPrices::new;
        }

        private final AtomicBoolean doOnce = new AtomicBoolean();

        @Override
        public void onMarketMessage(MarketEvent event) {
            super.onMarketMessage(event);
            //System.out.println(event);
            if (doOnce.compareAndSet(false, true)) {
                //System.out.println("First event received, placing test order");

                Order.Builder marketOrder = makeMarketOrder(Order.Side.BUY, 1.0, event.symbol());
                marketOrder.destinationId("SIMULATOR");
                submitOrder(marketOrder.toNewOrder());
            }
        }

        @Override
        public void onOrderFilled(Order.Filled fill) {
            super.onOrderFilled(fill);
            //System.out.println("Order filled: " + fill);
        }
    }
}
