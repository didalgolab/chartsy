package one.chartsy.core.ml.online;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VectorLinearRegressionTrainerTest {
    @Test void fits_both_affine_outputs_and_owns_training_and_prediction_arrays() {
        double[][] x = {{0,0},{1,0},{0,1},{1,1},{2,1},{1,2}};
        double[][] y = new double[x.length][2];
        for (int i=0; i<x.length; i++) {
            y[i][0] = 3 + 2*x[i][0] - x[i][1];
            y[i][1] = -1 - 4*x[i][0] + 3*x[i][1];
        }
        var fit = new VectorLinearRegressionTrainer().fit(x,y);
        assertArrayEquals(new double[]{6,-6}, fit.predict(new double[]{2,1}), 1e-12);
        x[0][0]=999; y[0][0]=999;
        var output=fit.predict(new double[]{2,1}); output[0]=999;
        assertArrayEquals(new double[]{6,-6}, fit.predict(new double[]{2,1}), 1e-12);
        assertThrows(IllegalArgumentException.class, () -> fit.predict(new double[]{1}));
        assertThrows(IllegalArgumentException.class, () -> fit.predict(new double[]{Double.NaN,1}));
    }
    @Test void rejects_unaligned_or_nonfinite_training_matrices() {
        var trainer=new VectorLinearRegressionTrainer();
        assertThrows(IllegalArgumentException.class, () -> trainer.fit(new double[][]{{1}}, new double[][]{}));
        assertThrows(IllegalArgumentException.class, () -> trainer.fit(new double[][]{{1},{2,3}}, new double[][]{{1},{2}}));
        assertThrows(IllegalArgumentException.class, () -> trainer.fit(new double[][]{{1},{2}}, new double[][]{{1},{Double.NaN}}));
    }

    @Test void singular_design_preserves_inputs_and_predicts_both_original_targets() {
        double[][] x = {{0,0},{0,1},{0,2},{0,3},{0,4},{0,5}};
        double[][] y = {{3,-1},{5,-5},{7,-9},{9,-13},{11,-17},{13,-21}};
        double[][] savedX = new double[x.length][], savedY = new double[y.length][];
        for (int i = 0; i < x.length; i++) {
            savedX[i] = x[i].clone();
            savedY[i] = y[i].clone();
        }
        var model = new VectorLinearRegressionTrainer().fit(x,y);
        double[] query = {0,2};
        assertArrayEquals(new double[]{7,-9}, model.predict(query), 1e-12);
        assertArrayEquals(new double[]{0,2}, query);
        for (int i = 0; i < x.length; i++) {
            assertArrayEquals(savedX[i], x[i]);
            assertArrayEquals(savedY[i], y[i]);
            assertArrayEquals(savedY[i], model.predict(x[i]), 1e-12);
        }
    }
}
