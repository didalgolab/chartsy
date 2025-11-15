package one.chartsy.hnsw;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Public API for a mutable Hierarchical Navigable Small World (HNSW) index.
 */
public interface HnswIndex {

    // Build / mutate -----------------------------------------------------

    /**
     * Adds (or updates depending on {@link HnswConfig#duplicatePolicy}) a vector with the
     * provided identifier.
     *
     * @param key     external identifier of the vector
     * @param vector vector values, expected length is {@link #dimension()}
     */
    void add(long key, double[] vector);

    /**
     * Removes the vector with the given identifier.
     *
     * @param key identifier to remove
     * @return {@code true} if the vector existed and has been removed
     */
    boolean remove(long key);

    /**
     * Checks whether the index currently contains a vector with the provided identifier.
     */
    boolean contains(long key);

    // Query --------------------------------------------------------------

    /**
     * Executes a KNN search using the configured default {@code efSearch}.
     *
     * @param query query vector
     * @param k     number of nearest neighbours to retrieve
     * @return the {@code k} closest vectors according to the configured distance
     */
    List<SearchResult> nearestNeighbors(double[] query, int k);

    /**
     * Executes a KNN search using an explicit {@code efSearch} beam width.
     */
    List<SearchResult> nearestNeighbors(double[] query, int k, int efSearch);

    List<SearchResult> nearestNeighborsExact(double[] query, int k);

    // Info ---------------------------------------------------------------

    /** Returns the number of non-deleted vectors stored in the index. */
    int size();

    /** Returns the dimensionality of vectors stored in the index. */
    int dimension();

    /** Returns statistics describing the current state of the graph. */
    HnswStats stats();

    // Lifecycle ----------------------------------------------------------

    /** Clears the index removing all vectors and edges. */
    void clear();

    /**
     * Saves the current index to the provided path.
     *
     * @throws IOException if any I/O error occurs during writing
     */
    void save(Path path) throws IOException;

    /**
     * Loads an index previously saved through {@link #save(Path)}.
     */
    static HnswIndex load(Path path) throws IOException {
        return Hnsw.load(path);
    }
}
