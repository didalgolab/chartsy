/*
 * Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.benchmarking;

import one.chartsy.Candle;
import one.chartsy.data.structures.PriorityMap;
import one.chartsy.data.*;
import one.chartsy.random.RandomWalk;
import one.chartsy.simulation.time.SimulationClock;
import one.chartsy.time.Chronological;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.profile.StackProfiler;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class Main4 {

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                // Specify which benchmarks to run.
                // You can be more specific if you'd like to run only one benchmark per test.
                .include(Main4.class.getName() + ".*")
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
        List<Candle> candles = new ArrayList<>();
        PriorityMap<Chronological, Candle> map = new PriorityMap<>();
        PriorityQueue<Chronological> queue = new PriorityQueue<>();
        int index;
        SimulationClock simClock = new SimulationClock(ZoneId.systemDefault(), Chronological.now());
        Class[] classes = new Class[] {Chronological.class, Candle.class, ExtendedCandle.class, SimpleCandle.class, AbstractCandle.class};

        @Setup(Level.Trial) public void
        initialize() {
            candles = RandomWalk.candles(Duration.ofMinutes(15), LocalDateTime.of(1900, 1, 1, 0, 0))
                    .limit(10_000_000)
                    .map(c -> ThreadLocalRandom.current().nextInt(4)==0? new ExtendedCandle(c, c.getTime(), 0, 0) : c)
                    //.map(c -> new ExtendedCandle(c, c.getTime(), 0, 0))
                    .collect(Collectors.toList());
            for (int i = 0; i < 10; i++) {
                Candle c = candles.get(i);
                map.put(c, c);
                queue.add(c);
                index++;
            }
        }
    }

    @Benchmark
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    @BenchmarkMode(Mode.AverageTime)
    @Measurement(time = 10)
    public boolean randomNextDouble3(BenchmarkState state) {
//        Candle c1 = state.map.remove();
//        Candle c2 = state.candles.get(state.index++);
//        state.map.put(c2, c2);
////        Chronological c1 = state.queue.remove();
////        Candle c2 = state.candles.get(state.index++);
////        state.queue.add(c2);
//
//        state.simClock.setTime(state.candles.get(state.index++));
//        if (state.index >= 10_000_000)
//            state.index = 0;
////        return c1;
//        return state.simClock;
        int index = state.index++;
        if (state.index >= 10_000_000)
            state.index = 0;
//        return state.candles.get(index);
        return state.candles.get(index).getClass().isAssignableFrom(state.classes[ThreadLocalRandom.current().nextInt(state.classes.length)]);
    }
}
