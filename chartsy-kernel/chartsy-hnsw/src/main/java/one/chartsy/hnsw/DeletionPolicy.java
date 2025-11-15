package one.chartsy.hnsw;

/**
 * Deletion strategy controlling whether local repair of the neighbourhood graph is performed.
 */
public enum DeletionPolicy {
    /** Only mark nodes as deleted without repairing edges. */
    LAZY_ONLY,
    /** Perform lazy deletion combined with neighbourhood repair to maintain quality. */
    LAZY_WITH_REPAIR
}
