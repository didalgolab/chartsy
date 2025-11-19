package one.chartsy.benchmarking.algorithm;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import one.chartsy.hnsw.Hnsw;
import one.chartsy.hnsw.HnswBulkBuilder;
import one.chartsy.hnsw.HnswConfig;
import one.chartsy.hnsw.HnswIndex;
import one.chartsy.hnsw.SearchResult;
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
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

@Warmup(iterations = 1, time = 2, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 3, time = 2, timeUnit = TimeUnit.SECONDS)
@Fork(0)
public class HnswBulkBuildBenchmark2 {

    public static void main(String[] args) throws Exception {
        Options options = new OptionsBuilder()
                .include(HnswBulkBuildBenchmark2.class.getName())
                .build();
        new Runner(options).run();
    }

    // -------------------------
    // TIME BENCHMARKS (unchanged)
    // -------------------------

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

    // -------------------------
    // RECALL BENCHMARK
    // -------------------------

    /**
     * Builds both indexes and computes recall@k against an exact brute-force baseline
     * for a shared set of random unit queries. Single-shot so it won't skew time averages.
     */
    @Benchmark
    @BenchmarkMode(Mode.SingleShotTime)
    @Warmup(iterations = 0)
    @Measurement(iterations = 1)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void recallCheck(BuildState state, Blackhole bh) {
        HnswIndex serial = state.serial();
        HnswIndex bulk = state.bulk();

        double serialRecall = recallAtK(serial, state.queries, state.k, state.efSearch);
        double bulkRecall   = recallAtK(bulk,   state.queries, state.k, state.efSearch);

        System.out.printf(
                "recall@%d (efSearch=%d)  serial=%.4f  bulk=%.4f  [N=%d, dim=%d, Q=%d]%n",
                state.k, state.efSearch, serialRecall, bulkRecall, state.vectorCount, state.dimension, state.numQueries
        );

        bh.consume(serialRecall);
        bh.consume(bulkRecall);
    }

    // -------------------------
    // State & helpers
    // -------------------------

    @State(Scope.Thread)
    public static class BuildState {
        @Param({"20000"})
        int vectorCount;

        @Param({"64"})
        int dimension;

        /** top-k for recall */
        @Param({"10"})
        int k;

        /** number of random queries to evaluate */
        @Param({"100"})
        int numQueries;

        /** efSearch used in approximate queries */
        @Param({"100"})
        int efSearch;

        double[][] dataset;
        long[] ids;
        double[][] queries;
        HnswConfig config;

        @Setup(Level.Iteration)
        public void setup() {
            dataset = HnswPerformanceBenchmark.randomNormalizedVectors(vectorCount, dimension, 42L);
            queries = HnswPerformanceBenchmark.randomNormalizedVectors(numQueries, dimension, 123L);

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

    // -------------------------
    // Recall helpers (cosine)
    // -------------------------

    /**
     * Computes average recall@k for cosine distance (unit-normalized vectors) across all queries.
     * recall@k = | approx_top_k ∩ exact_top_k | / k
     */
    private static double recallAtK(HnswIndex index, double[][] queries, int k, int efSearch) {
        final int K = Math.min(k, index.size());
        double sum = 0.0;

        for (double[] q : queries) {
            Set<Long> truth = new HashSet<>();
            for (SearchResult result : index.nearestNeighborsExact(q, K)) {
                truth.add(result.id());
            }

            Set<Long> approx = new HashSet<>();
            for (SearchResult r : index.nearestNeighbors(q, K, efSearch)) {
                approx.add(r.id());
            }

            int hit = 0;
            for (long id : truth) {
                if (approx.contains(id)) {
                    hit++;
                }
            }
            sum += (double) hit / K;
        }

        return sum / Math.max(1, queries.length);
    }

}
