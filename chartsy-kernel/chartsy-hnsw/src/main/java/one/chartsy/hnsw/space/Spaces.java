package one.chartsy.hnsw.space;

import java.io.DataInput;
import java.io.IOException;

/**
 * Convenience factory methods for built-in {@link Space} implementations.
 */
public final class Spaces {

    private Spaces() {
    }

    public static SpaceFactory euclidean() {
        return new EuclideanSpace.Factory();
    }

    public static SpaceFactory cosineNormalized() {
        return new CosineSpace.Factory();
    }

    public static SpaceFactory correlationDirect() {
        return new CorrelationSpace.Factory();
    }

    public static SpaceFactory custom(VectorDistance distance) {
        return new CustomSpace.Factory(distance);
    }

    public static SpaceFactory fromId(String id, DataInput in) throws IOException {
        return switch (id) {
            case "euclidean" -> new EuclideanSpace.Factory();
            case "cosine" -> new CosineSpace.Factory();
            case "correlation" -> new CorrelationSpace.Factory();
            case "custom" -> throw new IOException("Cannot deserialize custom space factories");
            default -> throw new IOException("Unknown space factory id: " + id);
        };
    }
}
