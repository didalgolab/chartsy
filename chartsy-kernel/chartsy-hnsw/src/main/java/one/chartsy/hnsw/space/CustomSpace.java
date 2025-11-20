package one.chartsy.hnsw.space;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

import one.chartsy.hnsw.HnswConfig;
import one.chartsy.hnsw.store.AuxStorage;
import one.chartsy.hnsw.store.VectorStorage;

/**
 * Space delegating distance computations to a user provided {@link VectorDistance}.
 */
public final class CustomSpace implements Space {

    private final int dimension;
    private final VectorStorage vectorStorage;
    private final VectorDistance distance;

    CustomSpace(HnswConfig config, VectorStorage vectorStorage, VectorDistance distance) {
        this.dimension = config.dimension;
        this.vectorStorage = vectorStorage;
        this.distance = distance;
    }

    @Override
    public int dimension() {
        return dimension;
    }

    @Override
    public void preallocate(int capacity) {
        vectorStorage.ensureCapacity(capacity);
    }

    @Override
    public void onInsert(int nodeId, double[] vector) {
        requireDimension(vector);
        vectorStorage.set(nodeId, vector);
    }

    @Override
    public void onRemove(int nodeId) {
        vectorStorage.clear(nodeId);
    }

    @Override
    public void onClear() {
    }

    @Override
    public QueryContext prepareQuery(double[] queryVector) {
        requireDimension(queryVector);
        return new ArrayQueryContext(queryVector);
    }

    @Override
    public QueryContext prepareQueryForNode(int nodeId) {
        return new StoredNodeQueryContext(nodeId);
    }

    @Override
    public double distance(QueryContext query, int nodeId) {
        return switch (query) {
            case ArrayQueryContext array -> distance.distance(vectorStorage.raw(), vectorStorage.offset(nodeId), array.vector(), 0, dimension);
            case StoredNodeQueryContext stored -> distanceBetweenNodes(stored.nodeId(), nodeId);
            default -> throw new IllegalStateException("Unknown query context: " + query);
        };
    }

    @Override
    public double distanceBetweenNodes(int nodeA, int nodeB) {
        return distance.distance(vectorStorage.raw(), vectorStorage.offset(nodeA),
                vectorStorage.raw(), vectorStorage.offset(nodeB), dimension);
    }

    private void requireDimension(double[] vector) {
        if (vector.length != dimension) {
            throw new IllegalArgumentException("Expected vector dimension " + dimension + " but was " + vector.length);
        }
    }

    record ArrayQueryContext(double[] vector) implements QueryContext {
    }

    public static final class Factory implements SpaceFactory {
        private final VectorDistance distance;

        public Factory(VectorDistance distance) {
            this.distance = distance;
        }

        @Override
        public void write(DataOutput out) throws IOException {
            if (!(distance instanceof Serializable)) {
                throw new IOException("Custom VectorDistance must be Serializable for persistence");
            }
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
                oos.writeObject(distance);
            }
            byte[] bytes = baos.toByteArray();
            out.writeInt(bytes.length);
            out.write(bytes);
        }

        @Override
        public SpaceFactory read(DataInput in) throws IOException {
            return readFactory(in);
        }

        @Override
        public Space create(HnswConfig config, VectorStorage vectorStorage, AuxStorage auxStorage) {
            return new CustomSpace(config, vectorStorage, distance);
        }

        @Override
        public String typeId() {
            return "custom";
        }

        static SpaceFactory readFactory(DataInput in) throws IOException {
            int len = in.readInt();
            if (len < 0) {
                throw new IOException("Invalid custom VectorDistance length: " + len);
            }
            byte[] data = new byte[len];
            in.readFully(data);
            try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(data))) {
                Object obj = ois.readObject();
                if (obj instanceof VectorDistance distance) {
                    return new Factory(distance);
                }
                throw new IOException("Deserialized object is not a VectorDistance: " + obj);
            } catch (ClassNotFoundException e) {
                throw new IOException("Failed to deserialize custom VectorDistance", e);
            }
        }
    }
}
