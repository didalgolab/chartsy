package one.chartsy.hnsw;

/**
 * Heuristic used to prune neighbours when edges need to be capped to the configured degree.
 */
public enum NeighborSelectHeuristic {
    /** Keep the closest neighbours without diversification. */
    SIMPLE,
    /** Diversify the neighbourhood using the standard HNSW occlusion rule. */
    DIVERSIFIED
}
