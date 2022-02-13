package one.chartsy.benchmarking;

import one.chartsy.data.Dataset;
import one.chartsy.data.packed.ByteBufferMutableHLCDataset;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

// Beware that it's pretty-much DRAFT only.
@State(Scope.Thread)
public class ByteBufferMutableHLCDatasetBenchmarkTest {

    FileChannel fileChannel;
    ThreadLocalRandom rnd = ThreadLocalRandom.current();
    ByteBufferMutableHLCDataset dataset;
    List<Object> list = new ArrayList<>();
    long time;

    @Benchmark
    @Warmup(iterations = 10, batchSize = 10_000)
    @Measurement(iterations = 10, batchSize = 10_000)
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    @BenchmarkMode(Mode.SingleShotTime)
    public Dataset<?> datasetAppend() throws IOException {
        dataset.add(time++, rnd.nextDouble());
        if (time % 2_000L == 0 && fileChannel != null)
            fileChannel.force(false);
        return dataset;
    }

//    @Benchmark
//    @Warmup(iterations = 10, batchSize = 10_000_000)
//    @Measurement(iterations = 10, batchSize = 10_000_000)
//    @OutputTimeUnit(TimeUnit.NANOSECONDS)
//    @BenchmarkMode(Mode.SingleShotTime)
//    public List<?> listAppend() {
//        list.add(new HLC(rnd.nextLong(), rnd.nextDouble(), rnd.nextDouble(), rnd.nextDouble()));
//        return list;
//    }

    @Setup(Level.Iteration)
    public void setup() throws IOException {
//        if (fileChannel != null && fileChannel.isOpen())
//            fileChannel.close();
//        fileChannel = (FileChannel) Files
//                .newByteChannel(Path.of("C:/Work/tmp/" + UUID.randomUUID()), EnumSet.of(
//                        StandardOpenOption.CREATE,
//                        StandardOpenOption.READ,
//                        StandardOpenOption.WRITE,
//                        StandardOpenOption.TRUNCATE_EXISTING));
//        MappedByteBuffer mappedByteBuffer = fileChannel
//                .map(FileChannel.MapMode.READ_WRITE, 0, 320_000);
//        this.dataset = new ByteBufferMutableHLCDataset(mappedByteBuffer);
        //this.dataset = new ByteBufferMutableHLCDataset(ByteBuffer.allocateDirect(320_000));
        this.dataset = new ByteBufferMutableHLCDataset(ByteBuffer.allocate(256), 1L);
        this.list.clear();
        this.time = 0;
    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                // Specify which benchmarks to run.
                // You can be more specific if you'd like to run only one benchmark per test.
                .include(ByteBufferMutableHLCDatasetBenchmarkTest.class.getName() + ".*")
                // Set the following options as needed
                .timeUnit(TimeUnit.MICROSECONDS)
                .threads(1)
                .forks(1)
                .shouldFailOnError(true)
                .shouldDoGC(true)
                //.jvmArgs("-XX:+UnlockDiagnosticVMOptions", "-XX:+PrintInlining")
                //.addProfiler(WinPerfAsmProfiler.class)
                //.addProfiler(StackProfiler.class)
                .build();

        new Runner(opt).run();
    }
}
