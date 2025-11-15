package one.chartsy.hnsw;

import java.util.Objects;

import one.chartsy.hnsw.space.SpaceFactory;

/**
 * Configuration builder for {@link HnswIndex} instances.
 */
public final class HnswConfig {

    public int dimension;
    public SpaceFactory spaceFactory;
    public int M = 16;
    public int maxM0 = 32;
    public int efConstruction = 200;
    public int defaultEfSearch = 50;
    public double levelLambda = 1.0;
    public long randomSeed = 42L;
    public DuplicatePolicy duplicatePolicy = DuplicatePolicy.UPSERT;
    public DeletionPolicy deletionPolicy = DeletionPolicy.LAZY_WITH_REPAIR;
    public NeighborSelectHeuristic neighborHeuristic = NeighborSelectHeuristic.DIVERSIFIED;
    public int initialCapacity = 16_384;
    public int efRepair = 40;

    public HnswConfig() {
    }

    public HnswConfig(HnswConfig other) {
        this.dimension = other.dimension;
        this.spaceFactory = other.spaceFactory;
        this.M = other.M;
        this.maxM0 = other.maxM0;
        this.efConstruction = other.efConstruction;
        this.defaultEfSearch = other.defaultEfSearch;
        this.levelLambda = other.levelLambda;
        this.randomSeed = other.randomSeed;
        this.duplicatePolicy = other.duplicatePolicy;
        this.deletionPolicy = other.deletionPolicy;
        this.neighborHeuristic = other.neighborHeuristic;
        this.initialCapacity = other.initialCapacity;
        this.efRepair = other.efRepair;
    }

    public static Builder builder() {
        return new Builder();
    }

    public void validate() {
        if (dimension <= 0) {
            throw new IllegalArgumentException("dimension must be positive");
        }
        Objects.requireNonNull(spaceFactory, "spaceFactory");
        if (M <= 0) {
            throw new IllegalArgumentException("M must be positive");
        }
        if (maxM0 <= 0) {
            throw new IllegalArgumentException("maxM0 must be positive");
        }
        if (maxM0 < M) {
            throw new IllegalArgumentException("maxM0 must be >= M");
        }
        if (efConstruction < M) {
            throw new IllegalArgumentException("efConstruction must be >= M");
        }
        if (defaultEfSearch < 1) {
            throw new IllegalArgumentException("defaultEfSearch must be >= 1");
        }
        if (levelLambda <= 0.0) {
            throw new IllegalArgumentException("levelLambda must be > 0");
        }
        if (initialCapacity <= 0) {
            throw new IllegalArgumentException("initialCapacity must be positive");
        }
        if (efRepair < 0) {
            throw new IllegalArgumentException("efRepair must be >= 0");
        }
    }

    public static final class Builder {
        private final HnswConfig config = new HnswConfig();

        public Builder dimension(int value) {
            config.dimension = value;
            return this;
        }

        public Builder spaceFactory(SpaceFactory factory) {
            config.spaceFactory = factory;
            return this;
        }

        public Builder M(int value) {
            config.M = value;
            return this;
        }

        public Builder maxM0(int value) {
            config.maxM0 = value;
            return this;
        }

        public Builder efConstruction(int value) {
            config.efConstruction = value;
            return this;
        }

        public Builder defaultEfSearch(int value) {
            config.defaultEfSearch = value;
            return this;
        }

        public Builder levelLambda(double value) {
            config.levelLambda = value;
            return this;
        }

        public Builder randomSeed(long value) {
            config.randomSeed = value;
            return this;
        }

        public Builder duplicatePolicy(DuplicatePolicy value) {
            config.duplicatePolicy = value;
            return this;
        }

        public Builder deletionPolicy(DeletionPolicy value) {
            config.deletionPolicy = value;
            return this;
        }

        public Builder neighborHeuristic(NeighborSelectHeuristic value) {
            config.neighborHeuristic = value;
            return this;
        }

        public Builder initialCapacity(int value) {
            config.initialCapacity = value;
            return this;
        }

        public Builder efRepair(int value) {
            config.efRepair = value;
            return this;
        }

        public HnswConfig build() {
            config.validate();
            return new HnswConfig(config);
        }
    }
}
