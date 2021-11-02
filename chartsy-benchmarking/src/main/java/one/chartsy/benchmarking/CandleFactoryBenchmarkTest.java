package one.chartsy.benchmarking;

import one.chartsy.Candle;
import one.chartsy.SymbolResource;
import one.chartsy.TimeFrame;
import one.chartsy.data.CandleSeries;
import one.chartsy.data.Series;
import one.chartsy.random.RandomWalk;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.profile.StackProfiler;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

public class CandleFactoryBenchmarkTest {

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                // Specify which benchmarks to run.
                // You can be more specific if you'd like to run only one benchmark per test.
                .include(CandleFactoryBenchmarkTest.class.getName() + ".*")
                // Set the following options as needed
                .mode (Mode.AverageTime)
                .timeUnit(TimeUnit.MICROSECONDS)
                .warmupTime(TimeValue.seconds(1))
                .warmupIterations(3)
                .measurementTime(TimeValue.seconds(3))
                .measurementIterations(10)
                .threads(2)
                .forks(1)
                .shouldFailOnError(true)
                .shouldDoGC(true)
                //.jvmArgs("-XX:+UnlockDiagnosticVMOptions", "-XX:+PrintInlining")
                //.addProfiler(WinPerfAsmProfiler.class)
                .addProfiler(StackProfiler.class)
                .build();

        new Runner(opt).run();
    }

    // The JMH samples are the best documentation for how to use it
    // http://hg.openjdk.java.net/code-tools/jmh/file/tip/jmh-samples/src/main/java/org/openjdk/jmh/samples/
    @State(Scope.Thread)
    public static class BenchmarkState
    {
        List<Integer> list;
        ThreadLocalRandom random = ThreadLocalRandom.current();
        List<Series<Candle>> seriesList = new ArrayList<>();
        AtomicLong counter = new AtomicLong();

        @Setup(Level.Trial) public void
        initialize() {
            for (int i = 0; i < 1; i++) {
                List<Candle> candles = RandomWalk.candles(Duration.ofMinutes(15), LocalDateTime.of(1900, 1, 1, 0, 0))
                        .limit(5000000)
                        .collect(Collectors.toList());
                Collections.reverse(candles);
                seriesList.add(CandleSeries.of(SymbolResource.of("RANDOM", TimeFrame.Period.M15), candles));
            }
        }
    }

    @Benchmark
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    @BenchmarkMode(Mode.AverageTime)
    @Measurement(time = 10)
    public Object randomNextDouble3(BenchmarkState state) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        return new UUID(r.nextLong(), r.nextLong()).toString();
    }
}
