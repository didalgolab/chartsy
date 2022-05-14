/* Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.benchmarking;

import one.chartsy.Candle;
import one.chartsy.collections.PriorityMap;
import one.chartsy.core.json.JsonFormatter;
import one.chartsy.data.AbstractCandle;
import one.chartsy.data.CandleSupport;
import one.chartsy.data.ExtendedCandle;
import one.chartsy.data.SimpleCandle;
import one.chartsy.naming.SymbolIdentityGenerator;
import one.chartsy.random.RandomWalk;
import one.chartsy.simulation.time.SimulationClock;
import one.chartsy.time.Chronological;
import org.openide.util.Lookup;
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
import java.util.ServiceLoader;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

public class GenericBenchmark {

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                // Specify which benchmarks to run.
                // You can be more specific if you'd like to run only one benchmark per test.
                .include(GenericBenchmark.class.getName() + ".*")
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
        volatile int index;
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

    private static final Semaphore lock = new Semaphore(1);
    private static final ReentrantLock lock2 = new ReentrantLock();

    @Benchmark
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    @BenchmarkMode(Mode.AverageTime)
    @Measurement(time = 10)
    public Object randomNextDouble3(BenchmarkState state) throws InterruptedException {
        //return Lookup.getDefault().lookup(SymbolIdentityGenerator.class);
        int index = state.index++;
        if (index == state.candles.size())
            index = state.index = 0;

        // synchronized: 23 ms
        lock2.lock();
        try {
            Candle c = state.candles.get(index);
            return Candle.of(c.getTime(), c.open(), c.high(), c.low(), c.close(), c.volume());
        } finally {
            lock2.unlock();
        }
    }
}
