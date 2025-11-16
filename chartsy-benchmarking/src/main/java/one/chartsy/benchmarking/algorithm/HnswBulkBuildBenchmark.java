package one.chartsy.benchmarking.algorithm;

import java.util.concurrent.TimeUnit;

import one.chartsy.hnsw.Hnsw;
import one.chartsy.hnsw.HnswBulkBuilder;
import one.chartsy.hnsw.HnswConfig;
import one.chartsy.hnsw.HnswIndex;
import one.chartsy.hnsw.space.Spaces;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

@Warmup(iterations = 1, time = 2, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 3, time = 2, timeUnit = TimeUnit.SECONDS)
@Fork(0)
public class HnswBulkBuildBenchmark {

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void serialBuild(BuildState state, Blackhole blackhole) {
        blackhole.consume(state.serial());
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void bulkBuild(BuildState state, Blackhole blackhole) {
        blackhole.consume(state.bulk());
    }

    @State(Scope.Thread)
    public static class BuildState {
        @Param({"20000"})
        int vectorCount;

        @Param({"64"})
        int dimension;

        double[][] dataset;
        long[] ids;
        HnswConfig config;

        @Setup(Level.Iteration)
        public void setup() {
            dataset = HnswPerformanceBenchmark.randomNormalizedVectors(vectorCount, dimension, 42L);
            ids = new long[vectorCount];
            for (int i = 0; i < vectorCount; i++) {
                ids[i] = i;
            }
            config = HnswConfig.builder()
                    .dimension(dimension)
                    .spaceFactory(Spaces.cosineNormalized())
                    .efConstruction(200)
                    .initialCapacity(vectorCount)
                    .build();
        }

        HnswIndex serial() {
            HnswIndex index = Hnsw.build(config);
            for (int i = 0; i < vectorCount; i++) {
                index.add(ids[i], dataset[i]);
            }
            return index;
        }

        HnswIndex bulk() {
            return new HnswBulkBuilder(config).build(ids, dataset);
        }
    }
}
