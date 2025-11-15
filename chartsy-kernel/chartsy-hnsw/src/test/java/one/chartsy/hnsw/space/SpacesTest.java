package one.chartsy.hnsw.space;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

import one.chartsy.hnsw.HnswConfig;
import one.chartsy.hnsw.store.AuxStorage;
import one.chartsy.hnsw.store.VectorStorage;

class SpacesTest {

    @Test
    void euclideanSpaceMatchesHandComputedDistances() {
        HnswConfig config = config(3);
        VectorStorage vectorStorage = new VectorStorage(config.dimension, 4);
        AuxStorage auxStorage = new AuxStorage(4);
        Space space = Spaces.euclidean().create(config, vectorStorage, auxStorage);

        space.onInsert(0, new double[]{1.0, 2.0, 2.0});
        space.onInsert(1, new double[]{1.0, 2.0, 7.0});

        double distance = space.distance(space.prepareQuery(new double[]{4.0, 6.0, 2.0}), 0);
        assertThat(distance).isCloseTo(5.0, within(1e-12));

        QueryContext stored = space.prepareQueryForNode(1);
        assertThat(space.distance(stored, 0)).isCloseTo(5.0, within(1e-12));
        assertThat(space.distanceBetweenNodes(0, 1)).isCloseTo(5.0, within(1e-12));
    }

    @Test
    void cosineSpaceNormalizesVectorsAndRespectsCosineSimilarity() {
        HnswConfig config = config(3);
        VectorStorage vectorStorage = new VectorStorage(config.dimension, 4);
        AuxStorage auxStorage = new AuxStorage(4);
        Space space = Spaces.cosineNormalized().create(config, vectorStorage, auxStorage);

        space.onInsert(0, new double[]{3.0, 4.0, 0.0});
        assertThat(vectorStorage.copy(0)).containsExactly(new double[]{0.6, 0.8, 0.0}, within(1e-12));
        assertThat(auxStorage.norm(0)).isCloseTo(5.0, within(1e-12));

        double aligned = space.distance(space.prepareQuery(new double[]{6.0, 8.0, 0.0}), 0);
        assertThat(aligned).isCloseTo(0.0, within(1e-12));

        double orthogonal = space.distance(space.prepareQuery(new double[]{-4.0, 3.0, 0.0}), 0);
        assertThat(orthogonal).isCloseTo(1.0, within(1e-12));

        space.onInsert(1, new double[]{0.0, 0.0, 0.0});
        double constantDistance = space.distance(space.prepareQuery(new double[]{1.0, 0.0, 0.0}), 1);
        assertThat(constantDistance).isCloseTo(1.0, within(1e-12));
    }

    @Test
    void correlationSpaceCentersAndNormalizesVectorsBeforeComputingDistance() {
        HnswConfig config = config(4);
        VectorStorage vectorStorage = new VectorStorage(config.dimension, 4);
        AuxStorage auxStorage = new AuxStorage(4);
        Space space = Spaces.correlationDirect().create(config, vectorStorage, auxStorage);

        space.onInsert(0, new double[]{1.0, 2.0, 3.0, 4.0});
        assertThat(auxStorage.mean(0)).isCloseTo(2.5, within(1e-12));
        assertThat(auxStorage.centeredNorm(0)).isCloseTo(1.0, within(1e-12));
        assertThat(vectorStorage.copy(0)).containsExactly(
                new double[]{-0.6708203932499369, -0.22360679774997896, 0.22360679774997896, 0.6708203932499369},
                within(1e-12));

        space.onInsert(1, new double[]{2.0, 4.0, 6.0, 8.0});
        double perfectCorrelation = space.distance(space.prepareQueryForNode(0), 1);
        assertThat(perfectCorrelation).isCloseTo(0.0, within(1e-12));

        space.onInsert(2, new double[]{5.0, 5.0, 5.0, 5.0});
        double constantVectorDistance = space.distance(space.prepareQuery(new double[]{9.0, 8.0, 7.0, 6.0}), 2);
        assertThat(constantVectorDistance).isCloseTo(1.0, within(1e-12));
    }

    @Test
    void customSpaceDelegatesToProvidedDistance() {
        HnswConfig config = config(2);
        VectorStorage vectorStorage = new VectorStorage(config.dimension, 4);
        AuxStorage auxStorage = new AuxStorage(4);
        VectorDistance manhattan = (a, offsetA, b, offsetB, length) -> {
            double sum = 0.0;
            for (int i = 0; i < length; i++) {
                sum += Math.abs(a[offsetA + i] - b[offsetB + i]);
            }
            return sum;
        };
        Space space = Spaces.custom(manhattan).create(config, vectorStorage, auxStorage);

        space.onInsert(0, new double[]{1.0, 2.0});
        space.onInsert(1, new double[]{3.0, 5.0});

        double queryDistance = space.distance(space.prepareQuery(new double[]{2.0, 4.0}), 0);
        assertThat(queryDistance).isCloseTo(3.0, within(1e-12));
        assertThat(space.distanceBetweenNodes(0, 1)).isCloseTo(5.0, within(1e-12));
    }

    private static HnswConfig config(int dimension) {
        HnswConfig config = new HnswConfig();
        config.dimension = dimension;
        return config;
    }
}
