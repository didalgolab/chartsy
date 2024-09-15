package one.chartsy.benchmarking.indicators;

import one.chartsy.SymbolResource;
import one.chartsy.TimeFrame;
import one.chartsy.data.CandleSeries;
import one.chartsy.data.provider.DataProvider;
import one.chartsy.data.provider.DataProviders;
import one.chartsy.data.provider.file.FlatFileFormat;
import one.chartsy.finance.FinancialIndicators;
import one.chartsy.financial.ValueIndicatorSupport;
import one.chartsy.financial.indicators.FractalDimension;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

public class FractalDimensionBenchmark {

    static DataProvider dataProvider;
    static {
        try {
            dataProvider = FlatFileFormat.FOREXTESTER.newDataProvider(Path.of("C:/Data/forextester.com/EURUSD.zip"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @State(Scope.Benchmark)
    public static class BenchmarkState {
        public CandleSeries series;

        @Setup(Level.Trial)
        public void setup() {
            this.series = DataProviders.getHistoricalCandles(dataProvider, SymbolResource.of("EURUSD", TimeFrame.Period.M1));
            System.out.println("SERIES LENGTH: " + this.series.length());
        }
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.SECONDS)
    public void benchmarkFdi(BenchmarkState state, Blackhole blackhole) {
        var result = FinancialIndicators.fdi(state.series.closes(), 30);
        blackhole.consume(result);
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.SECONDS)
    public void benchmarkFdiFast(BenchmarkState state, Blackhole blackhole) {
        var result = FinancialIndicators.fastFdi(state.series.closes(), 30);
        blackhole.consume(result);
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.SECONDS)
    public void benchmarkNewAsSeries(BenchmarkState state, Blackhole blackhole) {
        double[] result = new double[state.series.length()];

        var indicator = new FractalDimension(30);
        for (int i = state.series.length() - 1; i >= 0; i--) {
            indicator.accept(state.series.get(i).close());
            result[i] = indicator.getLast();
        }

        blackhole.consume(result);
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.SECONDS)
    public void benchmarkWithValueIndicatorSupportWithValueFunction(BenchmarkState state, Blackhole blackhole) {
        blackhole.consume(ValueIndicatorSupport.calculate(state.series.closes(), new FractalDimension(30), FractalDimension::getLast));
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.SECONDS)
    public void benchmarkWithValueIndicatorSupportWithoutValueFunction(BenchmarkState state, Blackhole blackhole) {
        blackhole.consume(ValueIndicatorSupport.calculate(state.series.closes(), new FractalDimension(30)));
    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                // Specify which benchmarks to run.
                // You can be more specific if you'd like to run only one benchmark per test.
                .include(FractalDimensionBenchmark.class.getName() + ".*")
                // Set the following options as needed
                .warmupTime(TimeValue.seconds(1))
                .warmupIterations(5)
                .measurementTime(TimeValue.seconds(10))
                .measurementIterations(10)
                .threads(1)
                .forks(1)
                .shouldFailOnError(true)
                .shouldDoGC(true)
                //.addProfiler(JavaFlightRecorderProfiler.class)
                .build();
        new Runner(opt).run();
    }
}