package one.chartsy.benchmarking;

import org.openjdk.jmh.annotations.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadLocalRandom;

@State(Scope.Benchmark)
public class IndexOfBenchmark {

    private long nextWrite;
    private int offset;

    private static final long mask = (1 << 19) - 1;
    private static final int mask2 = (1 << 19) - 1;

    @Setup(Level.Iteration)
    public void setUp() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        nextWrite = random.nextLong();
        offset = random.nextInt(100);
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    public int testIndexOf1() {
        return indexOf1(nextWrite, offset);
    }

    private static int indexOf1(long nextWrite, int offset) {
        return (int) ((nextWrite - offset - 1) & mask);
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    public int testIndexOf2() {
        return indexOf2(nextWrite, offset);
    }

    private static int indexOf2(long nextWrite, int offset) {
        return ((int)nextWrite - offset - 1) & mask2;
    }

    public static void main(String[] args) throws Exception {
        org.openjdk.jmh.runner.options.Options opt = new org.openjdk.jmh.runner.options.OptionsBuilder()
                .include(IndexOfBenchmark.class.getSimpleName())
                .forks(1)
                .build();

        new org.openjdk.jmh.runner.Runner(opt).run();
    }
}