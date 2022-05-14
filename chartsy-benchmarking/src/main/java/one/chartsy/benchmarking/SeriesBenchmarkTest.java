/* Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.benchmarking;

//import jdk.incubator.foreign.*;
import one.chartsy.Candle;
import one.chartsy.SymbolResource;
import one.chartsy.TimeFrame;
import one.chartsy.collections.ByteBufferBackedDoubleTimeSeries;
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

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class SeriesBenchmarkTest {

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                // Specify which benchmarks to run.
                // You can be more specific if you'd like to run only one benchmark per test.
                .include(SeriesBenchmarkTest.class.getName() + ".*")
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
        double[] array = new double[10_000_000];
        static final VarHandle arrHandle = MethodHandles.arrayElementVarHandle(double[].class);
        //ByteBuffer buf = ByteBuffer.allocateDirect(8 * 10_000_000);
//        MemorySegment mem = MemorySegment.allocateNative(8 * 10_000_000, ResourceScope.globalScope());
//        MemoryAddress memAddr;
        VarHandle memHandle;
        ByteBufferBackedDoubleTimeSeries bbbdts = new ByteBufferBackedDoubleTimeSeries(ByteBuffer.allocate(8 * 10_000_000));

        @Setup(Level.Trial) public void
        initialize() {
//            memAddr = mem.address();

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
//        for (int i = 0; i < state.buf.limit(); i+=8)
//            state.buf.putDouble(i, r.nextDouble());
//        return state.buf;

//        for (int i = 0; i < state.mem.byteSize(); i+=8)
//            //MemoryAccess.setDoubleAtOffset(state.mem, i, r.nextDouble());
//            memHandle.set(state.mem, i, r.nextDouble());

//        ByteBufferBackedDoubleTimeSeries bb = new ByteBufferBackedDoubleTimeSeries(ByteBuffer.allocate(8 * 10_000_000));
//        for (int i = 0; i < 10_000_000; i++)
//            bb.add(r.nextDouble());

//        for (int i = 0; i < 10_000_000; i++)
//            state.array[i] = r.nextDouble();
        VarHandle arrHandle = MethodHandles.arrayElementVarHandle(double[].class);
        for (int i = 0; i < 10_000_000; i++)
            state.arrHandle.set(state.array, i, r.nextDouble());

        return state.array;
    }

//    private static final VarHandle memHandle = MemoryHandles.varHandle(double.class, ByteOrder.nativeOrder());


    public static class Main {
        public static void main(String[] args) {
            ByteBuffer buf = ByteBuffer.allocate(160_000_000);
            System.out.println(buf.limit());
        }
    }
}
