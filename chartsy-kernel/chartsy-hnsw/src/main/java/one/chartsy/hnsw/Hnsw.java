package one.chartsy.hnsw;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

import one.chartsy.hnsw.internal.DefaultHnswIndex;

/**
 * Entry point factory for creating HNSW indices.
 */
public final class Hnsw {

    private Hnsw() {
    }

    public static HnswIndex build(HnswConfig config) {
        HnswConfig validated = new HnswConfig(Objects.requireNonNull(config));
        validated.validate();
        return new DefaultHnswIndex(validated);
    }

    public static HnswIndex load(Path path) throws IOException {
        return DefaultHnswIndex.load(path);
    }
}
