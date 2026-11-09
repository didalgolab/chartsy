package one.chartsy.core.ml.online;

import one.chartsy.core.ml.PredictorFunction;
import one.chartsy.core.ml.SmilePredictors;
import one.chartsy.core.ml.ToDoublePredictorFunction;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Affine least squares for a vector target, using the same design matrix for every output.
 * The unconstrained squared-error objective separates by output, so its coefficient columns
 * can be fitted independently without changing the joint optimum. This equivalence does not
 * apply to models such as forests whose splits depend on multiple outputs.
 */
public final class VectorLinearRegressionTrainer implements VectorModelTrainer {
    @Override public PredictorFunction<double[], double[]> fit(double[][] features, double[][] targets) {
        if (features.length == 0 || features.length != targets.length || features[0].length == 0 || targets[0].length == 0)
            throw new IllegalArgumentException("Nonempty feature and target matrices with equal row counts required");
        int inputs = features[0].length;
        int outputs = targets[0].length;
        validateTrainingRows(features, targets, inputs, outputs);

        var models = new ArrayList<ToDoublePredictorFunction<double[]>>(outputs);
        for (int column = 0; column < outputs; column++)
            models.add(fitOutput(features, targets, column));
        List<ToDoublePredictorFunction<double[]>> fitted = List.copyOf(models);
        return new PredictorFunction<>() {
            @Override public double[] predict(double[] query) {
                if (query.length != inputs)
                    throw new IllegalArgumentException("Wrong query dimension");
                for (double value : query)
                    if (!Double.isFinite(value))
                        throw new IllegalArgumentException("Non-finite query");
                double[] result = new double[fitted.size()];
                for (int i = 0; i < result.length; i++)
                    result[i] = fitted.get(i).predictAsDouble(query);
                return result;
            }
            @Override public <T> Optional<T> unwrap(Class<T> type) {
                return type.isInstance(this) ? Optional.of(type.cast(this)) : Optional.empty();
            }
        };
    }

    private static void validateTrainingRows(double[][] features, double[][] targets, int inputs, int outputs) {
        for (int row = 0; row < features.length; row++) {
            if (features[row].length != inputs || targets[row].length != outputs)
                throw new IllegalArgumentException("Ragged matrix");
            for (double value : features[row])
                if (!Double.isFinite(value))
                    throw new IllegalArgumentException("Non-finite input");
            for (double value : targets[row])
                if (!Double.isFinite(value))
                    throw new IllegalArgumentException("Non-finite target");
        }
    }

    private static ToDoublePredictorFunction<double[]> fitOutput(double[][] features, double[][] targets, int column) {
        double[][] trainingFeatures = new double[features.length][];
        double[] trainingTargets = new double[features.length];
        for (int row = 0; row < features.length; row++) {
            trainingFeatures[row] = features[row].clone();
            trainingTargets[row] = targets[row][column];
        }
        return SmilePredictors.linearRegression(trainingFeatures, trainingTargets);
    }
}
