package one.chartsy.hnsw;

/**
 * Statistics about the current state of an {@link HnswIndex} instance.
 */
public record HnswStats(
        int size,
        int totalNodes,
        int maxLevel,
        double averageLevel,
        double averageDegreeLevel0,
        double averageDegree,
        long totalEdges,
        long memoryBytes
) {

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private int size;
        private int totalNodes;
        private int maxLevel;
        private double averageLevel;
        private double averageDegreeLevel0;
        private double averageDegree;
        private long totalEdges;
        private long memoryBytes;

        public Builder size(int value) {
            this.size = value;
            return this;
        }

        public Builder totalNodes(int value) {
            this.totalNodes = value;
            return this;
        }

        public Builder maxLevel(int value) {
            this.maxLevel = value;
            return this;
        }

        public Builder averageLevel(double value) {
            this.averageLevel = value;
            return this;
        }

        public Builder averageDegreeLevel0(double value) {
            this.averageDegreeLevel0 = value;
            return this;
        }

        public Builder averageDegree(double value) {
            this.averageDegree = value;
            return this;
        }

        public Builder totalEdges(long value) {
            this.totalEdges = value;
            return this;
        }

        public Builder memoryBytes(long value) {
            this.memoryBytes = value;
            return this;
        }

        public HnswStats build() {
            return new HnswStats(size, totalNodes, maxLevel, averageLevel,
                    averageDegreeLevel0, averageDegree, totalEdges, memoryBytes);
        }
    }
}
