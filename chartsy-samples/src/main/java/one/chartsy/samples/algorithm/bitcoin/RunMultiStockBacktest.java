/* Copyright 2025 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.samples.algorithm.bitcoin;

import one.chartsy.data.DataSubscription;
import one.chartsy.data.provider.FlatFileDataProvider;
import one.chartsy.data.provider.file.FlatFileFormat;
import one.chartsy.simulation.engine.AlgorithmBacktestRunner;
import one.chartsy.simulation.engine.FlatFileDataMarketSupplier;
import one.chartsy.simulation.engine.MarketSupplierFactory;
import one.chartsy.trade.algorithm.AbstractAlgorithm;
import one.chartsy.trade.algorithm.AlgorithmContext;
import one.chartsy.trade.algorithm.AlgorithmFactory;
import one.chartsy.trade.algorithm.data.InstrumentDataFactory;
import one.chartsy.trade.algorithm.data.SimpleInstrumentPrices;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneOffset;

public class RunMultiStockBacktest {

    public static void main(String[] args) throws Exception {

        // Create provider for the BTC file.
        FlatFileDataProvider provider = FlatFileFormat.STOOQ.newDataProvider(Path.of("C:/Users/Mariusz/Downloads/d_us_txt(5).zip"));

        // Subscribe to all symbols in the file (only BTC here).
        DataSubscription subscription = DataSubscription.SUBSCRIBED_TO_ALL;

        // Factories for algorithm and market data.
        AlgorithmFactory<MyAlgorithm> algoFactory = MyAlgorithm::new;
        LocalDate startDate = LocalDate.parse("2011-06-01");
        MarketSupplierFactory marketFactory =
                () -> new FlatFileDataMarketSupplier(provider, subscription,
                        startDate.atStartOfDay().atZone(ZoneOffset.UTC).toInstant());

        // Run the backtest.
        var result = new AlgorithmBacktestRunner().run(algoFactory, marketFactory, "ALGO");
        System.out.println("Backtest result: " + result);
        System.out.println("Sharpe ratio: " + result.equitySummary().getAnnualSharpeRatio());
    }

    static class MyAlgorithm extends AbstractAlgorithm {

        public MyAlgorithm(AlgorithmContext context) {
            super(context);
        }

        @Override
        protected InstrumentDataFactory createInstrumentDataFactory() {
            return SimpleInstrumentPrices::new;
        }
    }
}
