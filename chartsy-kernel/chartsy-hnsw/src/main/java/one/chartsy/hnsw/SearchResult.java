package one.chartsy.hnsw;

/**
 * Result entry returned by HNSW KNN search queries.
 */
public record SearchResult(long id, double distance) implements Comparable<SearchResult> {
    @Override
    public int compareTo(SearchResult other) {
        return Double.compare(this.distance, other.distance);
    }
}
