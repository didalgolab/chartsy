/* Copyright 2025 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.benchmarking.algorithm;

import one.chartsy.data.DataSubscription;
import one.chartsy.data.provider.FlatFileDataProvider;
import one.chartsy.data.provider.file.FlatFileFormat;
import one.chartsy.messaging.MarketEvent;
import one.chartsy.simulation.engine.AlgorithmBacktestRunner;
import one.chartsy.simulation.engine.FlatFileDataMarketSupplier;
import one.chartsy.simulation.engine.MarketSupplierFactory;
import one.chartsy.trade.algorithm.AbstractAlgorithm;
import one.chartsy.trade.algorithm.AlgorithmContext;
import one.chartsy.trade.algorithm.AlgorithmFactory;
import one.chartsy.trade.algorithm.data.InstrumentDataFactory;
import one.chartsy.trade.algorithm.data.SimpleInstrumentPrices;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.ChainedOptionsBuilder;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.SingleShotTime)
@OutputTimeUnit(TimeUnit.SECONDS)
public class MultiStockBacktestBenchmark {

    @State(Scope.Benchmark)
    public static class BenchmarkState {

        // Override from CLI by passing: dataPath=... startDate=...
        @Param({"C:/Users/Mariusz/Downloads/d_us_txt(5).zip"})
        public String dataPath;

        @Param({"2000-01-01"})
        public String startDate;

        FlatFileDataProvider provider;
        DataSubscription subscription;
        AlgorithmFactory<MyAlgorithm> algoFactory;
        MarketSupplierFactory marketFactory;

        @Setup(Level.Trial)
        public void setUp() throws Exception {
            provider = FlatFileFormat.STOOQ.newDataProvider(Path.of(dataPath));
            subscription = DataSubscription.SUBSCRIBED_TO_ALL;
            algoFactory = MyAlgorithm::new;

            LocalDate start = LocalDate.parse(startDate, DateTimeFormatter.ISO_LOCAL_DATE);
            marketFactory = () -> new FlatFileDataMarketSupplier(
                    provider,
                    subscription,
                    start.atStartOfDay().atZone(ZoneOffset.UTC).toInstant()
            );
        }
    }

    @Benchmark
    public void runBacktest(BenchmarkState state, Blackhole bh) throws Exception {
        var result = new AlgorithmBacktestRunner().run(state.algoFactory, state.marketFactory, "ALGO");
        // Consume important bits so JIT cannot optimize the run away.
        bh.consume(result);
        try {
            var eq = result.equitySummary();
            bh.consume(eq.getEndingEquity());
            bh.consume(eq.getAnnualSharpeRatio());
            bh.consume(eq.getDataPoints());
        } catch (Throwable ignore) {
            // If API changes, still consume result; benchmark remains valid.
        }
    }

    // Runnable entry point without needing the shaded JMH jar.
    public static void main(String[] args) throws Exception {
        ChainedOptionsBuilder builder = new OptionsBuilder()
                .include(MultiStockBacktestBenchmark.class.getSimpleName())
                .warmupIterations(1)
                .measurementIterations(10)
                .forks(1)
                .shouldDoGC(true)
                .mode(Mode.SingleShotTime)
                .timeUnit(TimeUnit.SECONDS);

        // Simple arg parsing: pass args like "dataPath=C:/file.zip" "startDate=2011-06-01"
        for (String arg : args) {
            if (arg.startsWith("dataPath=")) {
                builder.param("dataPath", arg.substring("dataPath=".length()));
            } else if (arg.startsWith("startDate=")) {
                builder.param("startDate", arg.substring("startDate=".length()));
            }
        }

        Options opt = builder.build();
        new Runner(opt).run();
    }

    // Your algorithm unchanged.
    static class MyAlgorithm extends AbstractAlgorithm {
        static int dataCount;

        public MyAlgorithm(AlgorithmContext context) {
            super(context);
        }

        @Override
        protected InstrumentDataFactory createInstrumentDataFactory() {
            return SimpleInstrumentPrices::new;
        }

        @Override
        public void open() {
            super.open();
            dataCount = 0;
        }

        @Override
        public void onMarketMessage(MarketEvent event) {
            super.onMarketMessage(event);
            dataCount++;
        }

        @Override
        public void close() {
            super.close();
            System.out.println("Processed data points: " + dataCount);
        }
    }
}
