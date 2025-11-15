package one.chartsy.hnsw.space;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.io.Serializable;

import one.chartsy.hnsw.HnswConfig;
import one.chartsy.hnsw.store.AuxStorage;
import one.chartsy.hnsw.store.VectorStorage;

/**
 * Factory responsible for creating {@link Space} instances.
 */
public interface SpaceFactory extends Serializable {

    Space create(HnswConfig config, VectorStorage vectorStorage, AuxStorage auxStorage);

    /** Identifier used during serialisation. */
    default String typeId() {
        return getClass().getName();
    }

    /** Serialises factory-specific configuration, if any. */
    default void write(DataOutput out) throws IOException {
    }

    /** Reads factory-specific configuration after the type id has been resolved. */
    default SpaceFactory read(DataInput in) throws IOException {
        return this;
    }
}
