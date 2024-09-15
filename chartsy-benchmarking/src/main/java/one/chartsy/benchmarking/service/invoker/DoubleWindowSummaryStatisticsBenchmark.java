package one.chartsy.benchmarking.service.invoker;

import one.chartsy.core.event.AbstractInvoker;
import one.chartsy.data.structures.DoubleWindowSummaryStatistics;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.profile.JavaFlightRecorderProfiler;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

public class DoubleWindowSummaryStatisticsBenchmark {

    @State(Scope.Benchmark)
    public static class BenchmarkState {
        public ThreadLocalRandom random;
        public DoubleWindowSummaryStatistics stats;

        @Setup(Level.Trial)
        public void setup() {
            this.random = ThreadLocalRandom.current();
            this.stats = new DoubleWindowSummaryStatistics(30);
        }
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    public void benchmarkAddAndGetAverage(BenchmarkState state, Blackhole blackhole) {
        state.stats.add(state.random.nextDouble());
        blackhole.consume(state.stats.getAverage());
    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                // Specify which benchmarks to run.
                // You can be more specific if you'd like to run only one benchmark per test.
                .include(DoubleWindowSummaryStatisticsBenchmark.class.getName() + ".*")
                // Set the following options as needed
                .warmupTime(TimeValue.seconds(1))
                .warmupIterations(3)
                .measurementTime(TimeValue.seconds(20))
                .measurementIterations(20)
                .threads(1)
                .forks(1)
                .shouldFailOnError(true)
                .shouldDoGC(true)
                .addProfiler(JavaFlightRecorderProfiler.class)
                .build();
        new Runner(opt).run();
    }
}