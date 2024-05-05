package one.chartsy.benchmarking;

import org.openjdk.jmh.annotations.*;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

public class IndexOfBenchmarkTest {

    @State(Scope.Thread)
    public static class BenchmarkState {
        long nextWrite = ThreadLocalRandom.current().nextLong();
        int offset = ThreadLocalRandom.current().nextInt();
        long maskLong = (1L << ThreadLocalRandom.current().nextInt(30)) - 1;
        int maskInt = (int) maskLong;
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    public int testIndexOf1(BenchmarkState state) {
        return (int) ((state.nextWrite - state.offset - 1) & state.maskLong);
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    public int testIndexOf2(BenchmarkState state) {
        return ((int) state.nextWrite - state.offset - 1) & state.maskInt;
    }

    public static void main(String[] args) throws Exception {
        org.openjdk.jmh.runner.options.Options opt = new org.openjdk.jmh.runner.options.OptionsBuilder()
                .include(IndexOfBenchmarkTest.class.getSimpleName())
                .mode(org.openjdk.jmh.annotations.Mode.AverageTime)
                .timeUnit(TimeUnit.NANOSECONDS)
                .warmupIterations(1)
                .measurementIterations(5)
                .forks(1)
                .build();

        new org.openjdk.jmh.runner.Runner(opt).run();
    }
}