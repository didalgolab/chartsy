package one.chartsy.hnsw;

import java.util.Objects;

import one.chartsy.hnsw.internal.BulkBuildEngine;

/**
 * Public entry point for bulk-building {@link HnswIndex} instances.
 */
public final class HnswBulkBuilder {
    private final BulkBuildEngine engine;

    public HnswBulkBuilder(HnswConfig config) {
        this(config, new Options());
    }

    public HnswBulkBuilder(HnswConfig config, Options options) {
        this.engine = new BulkBuildEngine(Objects.requireNonNull(config), Objects.requireNonNull(options));
    }

    public HnswIndex build(long[] ids, double[][] vectors) {
        return engine.build(ids, vectors);
    }

    public HnswBulkBuilder addBatch(long[] ids, double[][] vectors) {
        engine.addBatch(ids, vectors);
        return this;
    }

    public HnswIndex build() {
        return engine.build();
    }

    public static final class Options {
        public int concurrency = Runtime.getRuntime().availableProcessors();
        public int blockSizeL0 = 100_000;
        public int blockSizeUpper = 10_000;
        public int seedBlockL0 = 2_048;
        public int seedBlockUpper = 512;
        /**
         * Number of same-block neighbors to inject into the candidate pool while building
         * level 0 blocks. This keeps late blocks from only seeing the seeded core and helps
         * recover direct edges between nodes that are ingested together.
         */
        public int localConnectivitySampleL0 = 32;
        public int efConstruction = -1;
    }
}
