/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.benchmarking;

import one.chartsy.Candle;
import one.chartsy.SymbolResource;
import one.chartsy.TimeFrame;
import one.chartsy.data.CandleSeries;
import one.chartsy.data.Series;
import one.chartsy.random.RandomWalk;
import one.chartsy.simulation.SimulationContext;
import one.chartsy.simulation.engine.SimpleSimulationRunner;
import org.openide.util.Lookup;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Main {

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                // Specify which benchmarks to run.
                // You can be more specific if you'd like to run only one benchmark per test.
                .include(Main.class.getName() + ".*")
                // Set the following options as needed
                .mode (Mode.AverageTime)
                .timeUnit(TimeUnit.MICROSECONDS)
                .warmupTime(TimeValue.seconds(1))
                .warmupIterations(2)
                .measurementTime(TimeValue.seconds(1))
                .measurementIterations(3)
                .threads(2)
                .forks(1)
                .shouldFailOnError(true)
                .shouldDoGC(true)
                //.jvmArgs("-XX:+UnlockDiagnosticVMOptions", "-XX:+PrintInlining")
                //.addProfiler(WinPerfAsmProfiler.class)
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
        Series<Candle> series;

        @Setup(Level.Trial) public void
        initialize() {
            List<Candle> candles = RandomWalk.candles(Duration.ofMinutes(15), LocalDateTime.of(1900, 1, 1, 0, 0))
                    .limit(1000_000)
                    .collect(Collectors.toList());
            Collections.reverse(candles);
            series = CandleSeries.of(SymbolResource.of("RANDOM", TimeFrame.Period.M15), candles);
        }
    }

//    @Benchmark
//    public void
//    benchmark1 (BenchmarkState state, Blackhole bh) {
//
//        List<Integer> list = state.list;
//
//        for (int i = 0; i < 1000; i++)
//            bh.consume (list.get (i));
//    }

    @Benchmark
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    @BenchmarkMode(Mode.AverageTime)
    public double randomNextDouble(BenchmarkState state) {
        return state.random.nextDouble(10.0);
    }

    @Benchmark
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    @BenchmarkMode(Mode.AverageTime)
    public double randomNextDoubleRound(BenchmarkState state) {
        double v = state.random.nextDouble(10.0);
        v = Math.rint(v * 100.0) / 100.0;
        return v;
    }

    @Benchmark
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    @BenchmarkMode(Mode.AverageTime)
    public double randomNextDoubleRound2(BenchmarkState state) {
        double v = state.random.nextDouble(10.0);
        v = Math.round(v * 100.0) / 100.0;
        return v;
    }

//    @Benchmark
//    @OutputTimeUnit(TimeUnit.NANOSECONDS)
//    @BenchmarkMode(Mode.AverageTime)
    public double randomNextDouble2() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        double min = 0.0, max = 0.0, curr = 0.0;
        for (int i = 0; i < 16; i++) {
            curr += r.nextDouble();
            min = Math.min(min, curr);
            max = Math.max(max, curr);
        }
        return max - min;
    }

        @Benchmark
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    @BenchmarkMode(Mode.AverageTime)
        @Measurement(time = 10)
    public Object randomNextDouble3(BenchmarkState state) {
            SimulationContext context = Lookup.getDefault().lookup(SimulationContext.class);
            return Stream.of(new SimpleSimulationRunner(context));
    }
}
