package one.chartsy.hnsw;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import one.chartsy.hnsw.space.Spaces;

class HnswIndexTest {

    @Test
    void shouldAddAndSearch() {
        HnswConfig config = new HnswConfig();
        config.dimension = 2;
        config.spaceFactory = Spaces.euclidean();
        config.initialCapacity = 8;
        config.M = 4;
        config.maxM0 = 6;
        config.levelLambda = 1.0;

        HnswIndex index = Hnsw.build(config);
        index.add(1L, new double[]{0.0, 0.0});
        index.add(2L, new double[]{1.0, 0.0});
        index.add(3L, new double[]{0.0, 1.0});

        List<SearchResult> results = index.searchKnn(new double[]{0.1, 0.1}, 2);
        assertThat(results).hasSize(2);
        assertThat(results.getFirst().id()).isEqualTo(1L);
    }

    @Test
    void shouldRespectDuplicatePolicy() {
        HnswConfig config = new HnswConfig();
        config.dimension = 3;
        config.spaceFactory = Spaces.euclidean();
        config.duplicatePolicy = DuplicatePolicy.REJECT;

        HnswIndex index = Hnsw.build(config);
        index.add(1L, new double[]{0.0, 0.0, 0.0});
        assertThatThrownBy(() -> index.add(1L, new double[]{1.0, 0.0, 0.0}))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRemoveVectors() {
        HnswConfig config = new HnswConfig();
        config.dimension = 2;
        config.spaceFactory = Spaces.euclidean();
        HnswIndex index = Hnsw.build(config);
        index.add(1L, new double[]{0.0, 0.0});
        index.add(2L, new double[]{2.0, 2.0});
        assertThat(index.remove(1L)).isTrue();
        assertThat(index.contains(1L)).isFalse();
        List<SearchResult> results = index.searchKnn(new double[]{0.0, 0.0}, 1);
        assertThat(results).extracting(SearchResult::id).doesNotContain(1L);
    }

    @Test
    void shouldSaveAndLoadIndex() throws IOException {
        HnswConfig config = new HnswConfig();
        config.dimension = 2;
        config.spaceFactory = Spaces.euclidean();
        config.initialCapacity = 4;
        HnswIndex index = Hnsw.build(config);
        index.add(1L, new double[]{0.0, 0.0});
        index.add(2L, new double[]{1.0, 1.0});

        Path path = Files.createTempFile("hnsw-index", ".bin");
        index.save(path);

        HnswIndex loaded = Hnsw.load(path);
        List<SearchResult> results = loaded.searchKnn(new double[]{0.0, 0.0}, 1);
        assertThat(results).hasSize(1);
        assertThat(results.getFirst().id()).isEqualTo(1L);
    }

    @Test
    void cosineSpaceShouldNormaliseVectors() {
        HnswConfig config = new HnswConfig();
        config.dimension = 3;
        config.spaceFactory = Spaces.cosineNormalized();
        HnswIndex index = Hnsw.build(config);
        index.add(1L, new double[]{1.0, 0.0, 0.0});
        index.add(2L, new double[]{0.0, 1.0, 0.0});

        List<SearchResult> results = index.searchKnn(new double[]{1.0, 0.0, 0.0}, 1);
        assertThat(results).extracting(SearchResult::id).containsExactly(1L);
    }

    @Test
    void correlationSpaceHandlesConstantVectors() {
        HnswConfig config = new HnswConfig();
        config.dimension = 3;
        config.spaceFactory = Spaces.correlationDirect();
        HnswIndex index = Hnsw.build(config);
        index.add(1L, new double[]{2.0, 2.0, 2.0});
        index.add(2L, new double[]{1.0, 2.0, 3.0});

        List<SearchResult> results = index.searchKnn(new double[]{1.0, 2.0, 3.0}, 1);
        assertThat(results).extracting(SearchResult::id).containsExactly(2L);
    }
}
