package one.chartsy.benchmarking.algorithm;

import java.util.SplittableRandom;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import one.chartsy.hnsw.Hnsw;
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
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

/**
 * End-to-end JMH benchmarks for the HNSW index implementation.
 */
@Warmup(iterations = 1, time = 2, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 3, time = 2, timeUnit = TimeUnit.SECONDS)
@Fork(0)
@Threads(1)
public class HnswPerformanceBenchmark {

    private static final int QUERY_SET_SIZE = 2048;
    private static final int SEARCH_EF_CONSTRUCTION = 32;

    @Benchmark
    @BenchmarkMode(Mode.SampleTime)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    public void searchHnswLatency(SearchState state, Blackhole blackhole) {
        blackhole.consume(state.hnswIndex.nearestNeighbors(state.nextQuery(), state.k, state.efSearch));
    }

    @Benchmark
    @BenchmarkMode(Mode.SampleTime)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    public void searchNaiveLatency(SearchState state, Blackhole blackhole) {
        blackhole.consume(state.hnswIndex.nearestNeighborsExact(state.nextQuery(), state.k));
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    public void insertHnswThroughput(InsertState state) {
        int id = state.nextHnswId();
        state.hnswIndex.add(id, state.dataset[id]);
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    public void insertNaiveThroughput(InsertState state) {
        int id = state.nextNaiveId();
        state.naiveIndex.add(id, state.dataset[id]);
    }

    @State(Scope.Benchmark)
    public static class SearchState {
        @Param({"100000"})
        int vectorCount;

        @Param({"128"})
        int dimension;

        @Param({"10"})
        int k;

        @Param({"200"})
        int efSearch;

        double[][] queries;
        int nextQueryIndex;
        HnswIndex hnswIndex;

        @Setup(Level.Trial)
        public void setUp() {
            SharedSearchDataset shared = SharedSearchDataset.forConfig(vectorCount, dimension, efSearch);
            this.queries = shared.queries;
            this.hnswIndex = shared.hnswIndex;
            this.nextQueryIndex = 0;
        }

        double[] nextQuery() {
            double[] query = queries[nextQueryIndex++];
            if (nextQueryIndex == queries.length) {
                nextQueryIndex = 0;
            }
            return query;
        }
    }

    @State(Scope.Benchmark)
    public static class InsertState {
        @Param({"100000"})
        int vectorCount;

        @Param({"128"})
        int dimension;

        double[][] dataset;
        HnswIndex hnswIndex;
        NaiveCosineIndex naiveIndex;
        HnswConfig config;
        int hnswNextId;
        int naiveNextId;

        @Setup(Level.Trial)
        public void setUp() {
            dataset = randomNormalizedVectors(vectorCount, dimension, 9_999L);
            config = HnswConfig.builder()
                    .dimension(dimension)
                    .spaceFactory(Spaces.cosineNormalized())
                    .efConstruction(200)
                    .defaultEfSearch(100)
                    .initialCapacity(vectorCount)
                    .build();
            resetHnswIndex();
            resetNaiveIndex();
        }

        @Setup(Level.Iteration)
        public void restart() {
            resetHnswIndex();
            resetNaiveIndex();
        }

        int nextHnswId() {
            if (hnswNextId >= vectorCount) {
                resetHnswIndex();
            }
            return hnswNextId++;
        }

        int nextNaiveId() {
            if (naiveNextId >= vectorCount) {
                resetNaiveIndex();
            }
            return naiveNextId++;
        }

        private void resetHnswIndex() {
            hnswIndex = Hnsw.build(config);
            hnswNextId = 0;
        }

        private void resetNaiveIndex() {
            naiveIndex = new NaiveCosineIndex(vectorCount, dimension);
            naiveNextId = 0;
        }
    }

    static double[][] randomNormalizedVectors(int count, int dimension, long seed) {
        double[][] vectors = new double[count][];
        SplittableRandom random = new SplittableRandom(seed);
        for (int i = 0; i < count; i++) {
            vectors[i] = randomUnitVector(random, dimension);
        }
        return vectors;
    }

    private static final class SharedSearchDataset {
        private static final ConcurrentHashMap<Key, SharedSearchDataset> CACHE = new ConcurrentHashMap<>();

        private final double[][] queries;
        private final HnswIndex hnswIndex;

        private SharedSearchDataset(double[][] queries, HnswIndex hnswIndex) {
            this.queries = queries;
            this.hnswIndex = hnswIndex;
        }

        static SharedSearchDataset forConfig(int vectorCount, int dimension, int efSearch) {
            Key key = new Key(vectorCount, dimension, efSearch);
            return CACHE.computeIfAbsent(key, SharedSearchDataset::build);
        }

        private static SharedSearchDataset build(Key key) {
            double[][] dataset = randomNormalizedVectors(key.vectorCount, key.dimension, 1_337L);
            double[][] queries = randomNormalizedVectors(QUERY_SET_SIZE, key.dimension, 4_242L);

            HnswConfig config = HnswConfig.builder()
                    .dimension(key.dimension)
                    .spaceFactory(Spaces.cosineNormalized())
                    .efConstruction(Math.min(SEARCH_EF_CONSTRUCTION, key.vectorCount))
                    .defaultEfSearch(key.efSearch)
                    .initialCapacity(key.vectorCount)
                    .build();

            HnswIndex hnswIndex = Hnsw.build(config);
            for (int i = 0; i < dataset.length; i++) {
                hnswIndex.add(i, dataset[i]);
            }
            return new SharedSearchDataset(queries, hnswIndex);
        }

        record Key(int vectorCount, int dimension, int efSearch) {
        }
    }

    private static double[] randomUnitVector(SplittableRandom random, int dimension) {
        double[] values = new double[dimension];
        double normSq = 0.0;
        for (int i = 0; i < dimension; i++) {
            double value = random.nextDouble(-1.0, 1.0);
            values[i] = value;
            normSq += value * value;
        }
        double norm = Math.sqrt(normSq);
        double invNorm = norm > 0.0 ? 1.0 / norm : 1.0;
        for (int i = 0; i < dimension; i++) {
            values[i] *= invNorm;
        }
        return values;
    }

    static final class NaiveCosineIndex {
        private final double[][] storage;
        private final int dimension;
        private int size;

        NaiveCosineIndex(int capacity, int dimension) {
            this.storage = new double[capacity][];
            this.dimension = dimension;
            this.size = 0;
        }

        void add(int id, double[] vector) {
            if (id >= storage.length) {
                throw new IllegalArgumentException("id exceeds capacity");
            }
            storage[id] = java.util.Arrays.copyOf(vector, vector.length);
            if (id + 1 > size) {
                size = id + 1;
            }
        }
    }
}
