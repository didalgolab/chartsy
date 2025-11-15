package one.chartsy.benchmarking.algorithm;

import java.util.Arrays;
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

        double serialRecall = recallAtKCosine(serial, state.dataset, state.ids, state.queries, state.k, state.efSearch);
        double bulkRecall   = recallAtKCosine(bulk,   state.dataset, state.ids, state.queries, state.k, state.efSearch);

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
    private static double recallAtKCosine(
            HnswIndex index,
            double[][] data, long[] ids,
            double[][] queries,
            int k, int efSearch
    ) {
        final int K = Math.min(k, ids.length);
        double sum = 0.0;

        for (double[] q : queries) {
            // exact top-k by cosine = top-k by highest dot (smallest (1 - dot))
            long[] gt = exactTopKCosine(ids, data, q, K);

            // approx results from index
            Set<Long> approx = new HashSet<>();
            for (SearchResult r : index.nearestNeighbors(q, K, efSearch)) {
                approx.add(r.id());
            }

            // compute intersection size / k
            int hit = 0;
            for (long id : gt) {
                if (approx.contains(id)) {
                    hit++;
                }
            }
            sum += (double) hit / K;
        }

        return sum / Math.max(1, queries.length);
    }

    /**
     * Exact top-k by cosine for a single query. Assumes data[i] are unit-normalized.
     * Returns the IDs (unsorted) of the top-k nearest neighbors.
     */
    private static long[] exactTopKCosine(long[] ids, double[][] data, double[] q, int k) {
        final int K = Math.min(k, ids.length);
        final long[] bestIds   = new long[K];
        final double[] bestDst = new double[K];
        Arrays.fill(bestDst, Double.POSITIVE_INFINITY);

        int worstIdx = 0;

        for (int i = 0; i < data.length; i++) {
            // cosine distance for unit vectors = 1 - dot
            double dist = 1.0 - dot(data[i], q);
            if (dist < bestDst[worstIdx]) {
                bestDst[worstIdx] = dist;
                bestIds[worstIdx] = ids[i];
                // recompute current worst in O(K) – K is small (e.g., 10)
                worstIdx = indexOfWorst(bestDst);
            }
        }
        return bestIds;
    }

    private static int indexOfWorst(double[] arr) {
        int idx = 0;
        double val = arr[0];
        for (int i = 1; i < arr.length; i++) {
            if (arr[i] > val) {
                val = arr[i];
                idx = i;
            }
        }
        return idx;
    }

    private static double dot(double[] a, double[] b) {
        double s0 = 0.0, s1 = 0.0, s2 = 0.0, s3 = 0.0;
        int i = 0;
        int n = a.length;
        int limit = n - (n % 4);
        for (; i < limit; i += 4) {
            s0 += a[i] * b[i];
            s1 += a[i + 1] * b[i + 1];
            s2 += a[i + 2] * b[i + 2];
            s3 += a[i + 3] * b[i + 3];
        }
        double sum = (s0 + s1) + (s2 + s3);
        for (; i < n; i++) {
            sum += a[i] * b[i];
        }
        return sum;
    }
}
