/*
 * Copyright 2026 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.core.ml.online.forests;

import one.chartsy.core.ml.PredictorFunction;
import one.chartsy.core.ml.online.VectorModelTrainer;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Optional;
import java.util.SplittableRandom;

/**
 * A regression forest whose trees learn all target coordinates together.
 *
 * <p>The historical convention follows the mathematical behavior of Shark's
 * RFTrainer at revision 1f3733898a1dac4a23439c4063cecf17537d936d: samples without
 * replacement, observed-value thresholds, parent-size stopping, and optionally
 * child-count-weighted squared error. This is an independent Java implementation,
 * not the original C++ trainer. Its local random generator, stable row ordering,
 * and arithmetic grouping do not reproduce the original RNG or
 * floating-point instruction sequence.</p>
 *
 * <p>Each fit resets its own generator to the declared seed. This makes repeated
 * fits and concurrent use reproducible; it is not recovered author RNG behavior.
 * No global random or executor settings are changed. See the reproduction
 * research's forests/independent/BACKEND-AUDIT.md for source provenance.</p>
 */
public final class RandomForestTrainer implements VectorModelTrainer {
    public enum SplitCriterion {
        /** Historical Shark: (leftCount * leftSSE + rightCount * rightSSE) / count. */
        HISTORICAL_SHARK_WEIGHTED_SSE,
        /** Ordinary total child squared error; all other forest conventions remain the same. */
        TOTAL_SSE
    }

    /** candidateFeatures=0 resolves to ceil(inputDimension/3). */
    public record Options(int trees, int candidateFeatures, int nodeSize,
                          double sampleFraction, long seed, SplitCriterion splitCriterion) {
        public Options {
            if (trees < 1 || candidateFeatures < 0 || nodeSize < 1)
                throw new IllegalArgumentException("Positive tree/node counts and nonnegative candidate count required");
            if (!Double.isFinite(sampleFraction) || sampleFraction <= 0 || sampleFraction > 1)
                throw new IllegalArgumentException("Sample fraction must be in (0,1]");
            if (splitCriterion == null) throw new IllegalArgumentException("Split criterion required");
        }

        public static Options historicalShark(long seed) {
            return new Options(100, 0, 5, 0.66, seed, SplitCriterion.HISTORICAL_SHARK_WEIGHTED_SSE);
        }
    }

    public record Diagnostics(int treeCount, int sampleSize, int candidateFeatures,
                              int leafCount, int minimumLeafSize, int maximumLeafSize) { }

    private final Options options;

    public RandomForestTrainer(Options options) {
        if (options == null) throw new IllegalArgumentException("Forest options required");
        this.options = options;
    }

    public Options options() { return options; }

    @Override
    public FittedModel fit(double[][] features, double[][] targets) {
        double[][] trainingFeatures = copyMatrix(features, "Features");
        double[][] trainingTargets = copyMatrix(targets, "Targets");
        if (trainingFeatures.length != trainingTargets.length) throw new IllegalArgumentException("Feature and target row counts must agree");
        int candidates = options.candidateFeatures() == 0
                ? (trainingFeatures[0].length + 2) / 3 : options.candidateFeatures();
        if (candidates > trainingFeatures[0].length)
            throw new IllegalArgumentException("Candidate count exceeds input dimension");
        int sampleSize = (int) (trainingFeatures.length * options.sampleFraction());
        if (sampleSize < 1)
            throw new IllegalArgumentException("Sample fraction selects no rows");

        var builder = new TreeBuilder(trainingFeatures, trainingTargets, candidates, options);
        var random = new SplittableRandom(options.seed());
        var trees = new Node[options.trees()];
        for (int i = 0; i < trees.length; i++) {
            var treeRandom = random.split();
            int[] rows = sample(trainingFeatures.length, sampleSize, treeRandom);
            trees[i] = builder.build(rows, treeRandom);
        }
        return new FittedModel(trees, trainingFeatures[0].length, trainingTargets[0].length, sampleSize, candidates, options);
    }

    private static int[] sample(int size, int count, SplittableRandom random) {
        int[] rows = new int[size];
        for (int i = 0; i < size; i++) rows[i] = i;
        for (int i = size - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            int old = rows[i];
            rows[i] = rows[j];
            rows[j] = old;
        }
        return Arrays.copyOf(rows, count);
    }

    private static double[][] copyMatrix(double[][] matrix, String name) {
        if (matrix == null || matrix.length == 0 || matrix[0] == null || matrix[0].length == 0)
            throw new IllegalArgumentException(name + " must have positive dimensions");
        int dimension = matrix[0].length;
        double[][] copy = new double[matrix.length][];
        for (int i = 0; i < matrix.length; i++) {
            validateVector(matrix[i], dimension, name);
            copy[i] = matrix[i].clone();
        }
        return copy;
    }

    private static void validateVector(double[] values, int dimension, String name) {
        if (values == null || values.length != dimension)
            throw new IllegalArgumentException(name + " dimensions must agree");
        for (double value : values)
            if (!Double.isFinite(value)) throw new IllegalArgumentException(name + " must be finite");
    }

    private static void requireFinite(double value) {
        if (!Double.isFinite(value)) throw new ArithmeticException("Forest arithmetic overflow");
    }

    private record Node(int feature, double threshold, Node left, Node right, double[] mean, int size) {
        static Node leaf(double[] mean, int size) { return new Node(-1, 0, null, null, mean, size); }
        boolean isLeaf() { return feature < 0; }
    }

    private record Split(int feature, double threshold, double score) { }

    private static final class TreeBuilder {
        private final double[][] features;
        private final double[][] targets;
        private final int candidates;
        private final Options options;

        TreeBuilder(double[][] features, double[][] targets, int candidates, Options options) {
            this.features = features;
            this.targets = targets;
            this.candidates = candidates;
            this.options = options;
        }

        Node build(int[] rows, SplittableRandom random) {
            if (rows.length <= options.nodeSize()) return leaf(rows);
            Split split = bestSplit(rows, random);
            if (split == null) return leaf(rows);
            int leftCount = 0;
            for (int row : rows) if (features[row][split.feature()] <= split.threshold()) leftCount++;
            int[] left = new int[leftCount];
            int[] right = new int[rows.length - leftCount];
            int leftIndex = 0;
            int rightIndex = 0;
            for (int row : rows) {
                if (features[row][split.feature()] <= split.threshold())
                    left[leftIndex++] = row;
                else
                    right[rightIndex++] = row;
            }
            // Children may contain fewer rows than nodeSize. That setting stops a parent.
            return new Node(split.feature(), split.threshold(), build(left, random),
                    build(right, random), null, rows.length);
        }

        private Node leaf(int[] rows) {
            double[] mean = new double[targets[0].length];
            for (int row : rows)
                for (int d = 0; d < mean.length; d++) mean[d] += targets[row][d] / rows.length;
            for (double value : mean) requireFinite(value);
            return Node.leaf(mean, rows.length);
        }

        private boolean[] selectCandidateFeatures(SplittableRandom random) {
            boolean[] selected = new boolean[features[0].length];
            int count = 0;
            while (count < candidates) {
                int feature = random.nextInt(selected.length);
                if (!selected[feature]) {
                    selected[feature] = true;
                    count++;
                }
            }
            return selected;
        }

        private Split bestSplit(int[] rows, SplittableRandom random) {
            boolean[] selected = selectCandidateFeatures(random);
            Split best = null;
            for (int feature = 0; feature < selected.length; feature++) {
                if (!selected[feature]) continue;
                final int column = feature;
                Integer[] ordered = Arrays.stream(rows).boxed().toArray(Integer[]::new);
                Arrays.sort(ordered, Comparator.<Integer>comparingDouble(row -> features[row][column])
                        .thenComparingInt(row -> row));
                for (int leftCount = 1; leftCount < rows.length; leftCount++) {
                    double threshold = features[ordered[leftCount - 1]][feature];
                    if (threshold == features[ordered[leftCount]][feature]) continue;
                    int rightCount = rows.length - leftCount;
                    double leftError = squaredError(ordered, 0, leftCount);
                    double rightError = squaredError(ordered, leftCount, rows.length);
                    double score = switch (options.splitCriterion()) {
                        case TOTAL_SSE -> leftError + rightError;
                        case HISTORICAL_SHARK_WEIGHTED_SSE ->
                                (leftCount * leftError + rightCount * rightError) / rows.length;
                    };
                    requireFinite(score);
                    if (best == null || score < best.score()) best = new Split(feature, threshold, score);
                }
            }
            return best;
        }

        /** Direct residuals preserve exact split-score ties in small training windows. */
        private double squaredError(Integer[] ordered, int from, int to) {
            double[] mean = new double[targets[0].length];
            for (int i = from; i < to; i++)
                for (int d = 0; d < mean.length; d++) mean[d] += targets[ordered[i]][d];
            for (int d = 0; d < mean.length; d++) {
                mean[d] /= to - from;
                requireFinite(mean[d]);
            }
            double error = 0;
            for (int i = from; i < to; i++)
                for (int d = 0; d < mean.length; d++) {
                    double residual = targets[ordered[i]][d] - mean[d];
                    error += residual * residual;
                }
            requireFinite(error);
            return error;
        }
    }

    /** Immutable fitted trees; neither target arrays nor mutable tree internals are exposed. */
    public static final class FittedModel implements PredictorFunction<double[], double[]> {
        private final Node[] trees;
        private final int inputDimension, outputDimension;
        private final Options options;
        private final Diagnostics diagnostics;

        private FittedModel(Node[] trees, int inputDimension, int outputDimension,
                            int sampleSize, int candidates, Options options) {
            this.trees = trees;
            this.inputDimension = inputDimension;
            this.outputDimension = outputDimension;
            this.options = options;
            var leaves = new LeafStatistics();
            for (Node tree : trees)
                leaves.include(tree);
            diagnostics = new Diagnostics(trees.length, sampleSize, candidates,
                    leaves.count, leaves.minimumSize, leaves.maximumSize);
        }

        public Options options() { return options; }
        public Diagnostics diagnostics() { return diagnostics; }

        @Override
        public double[] predict(double[] features) {
            validateVector(features, inputDimension, "Prediction features");
            double[] prediction = new double[outputDimension];
            for (Node tree : trees) {
                Node leaf = tree;
                while (!leaf.isLeaf())
                    leaf = features[leaf.feature()] <= leaf.threshold() ? leaf.left() : leaf.right();
                for (int d = 0; d < outputDimension; d++) prediction[d] += leaf.mean()[d] / trees.length;
            }
            for (double value : prediction) requireFinite(value);
            return prediction;
        }

        @Override
        public <T> Optional<T> unwrap(Class<T> type) {
            return type.isInstance(this) ? Optional.of(type.cast(this)) : Optional.empty();
        }

        private static final class LeafStatistics {
            private int count;
            private int minimumSize = Integer.MAX_VALUE;
            private int maximumSize;

            private void include(Node node) {
                if (node.isLeaf()) {
                    count++;
                    minimumSize = Math.min(minimumSize, node.size());
                    maximumSize = Math.max(maximumSize, node.size());
                } else {
                    include(node.left());
                    include(node.right());
                }
            }
        }
    }
}
