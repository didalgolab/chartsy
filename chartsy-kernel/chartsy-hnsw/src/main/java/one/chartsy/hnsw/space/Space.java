package one.chartsy.hnsw.space;

/**
 * Abstraction over the distance space used by the HNSW graph.
 */
public interface Space {

    int dimension();

    /**
     * Invoked when a vector is inserted into the index. Implementations may mutate the stored
     * vector (e.g. to normalise it) and populate auxiliary statistics.
     */
    void onInsert(int nodeId, double[] vector);

    /**
     * Invoked when a vector is removed. Gives the space a chance to clean auxiliary storage.
     */
    void onRemove(int nodeId);

    /** Clears all state associated with the space. */
    void onClear();

    /** Prepares a query context for the provided query vector. */
    QueryContext prepareQuery(double[] queryVector);

    /**
     * Prepares a query context backed by the vector stored at {@code nodeId}. Used internally when
     * inserting new nodes.
     */
    QueryContext prepareQueryForNode(int nodeId);

    /** Computes the distance between the given query context and the node. */
    double distance(QueryContext query, int nodeId);

    /** Computes the distance between two stored nodes. */
    double distanceBetweenNodes(int nodeA, int nodeB);
}
