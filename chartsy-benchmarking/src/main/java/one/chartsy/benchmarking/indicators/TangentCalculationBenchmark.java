package one.chartsy.benchmarking.indicators;

import one.chartsy.SymbolResource;
import one.chartsy.TimeFrame;
import one.chartsy.data.CandleSeries;
import one.chartsy.data.provider.DataProvider;
import one.chartsy.data.provider.DataProviders;
import one.chartsy.data.provider.file.FlatFileFormat;
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
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

public class TangentCalculationBenchmark {

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    public double benchmarkTangentCalculation() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        return Math.tan(random.nextDouble(0, 100.0));
    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                // Specify which benchmarks to run.
                .include(TangentCalculationBenchmark.class.getName() + ".*")
                // Set the following options as needed
                .warmupTime(TimeValue.seconds(1))
                .warmupIterations(3)
                .measurementTime(TimeValue.seconds(10))
                .measurementIterations(10)
                .threads(1)
                .forks(1)
                .shouldFailOnError(true)
                .shouldDoGC(true)
                .build();
        new Runner(opt).run();
    }
}