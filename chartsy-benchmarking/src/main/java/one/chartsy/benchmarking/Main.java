package one.chartsy.benchmarking;

import one.chartsy.Candle;
import org.apache.commons.math3.random.RandomGeneratorFactory;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.random.RandomGenerator;

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

        @Setup(Level.Trial) public void
        initialize() {

            Random rand = new Random();

            list = new ArrayList<>();
            for (int i = 0; i < 1000; i++)
                list.add (rand.nextInt());
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
    public Object randomNextDouble3(BenchmarkState state) {
        RandomGenerator r = state.random;
        double curr = r.nextGaussian(), open = curr / 10.0;
        double min = Math.min(curr, open), max = Math.max(curr, open);

        for (int i = 0; i < 3; i++) {
            curr += r.nextGaussian();
            min = Math.min(min, curr);
            max = Math.max(max, curr);
        }
        Candle c = Candle.of(0L, open, max, min, curr);
           // System.out.println(c);
        return c;
    }
}
