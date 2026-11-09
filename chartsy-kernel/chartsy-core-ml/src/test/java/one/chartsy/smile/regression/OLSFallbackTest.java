package one.chartsy.smile.regression;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class OLSFallbackTest {
    @Test
    void singular_design_preserves_inputs_and_fits_original_affine_targets() {
        double[][] features = {{0, 0}, {0, 1}, {0, 2}, {0, 3}, {0, 4}, {0, 5}};
        double[][] original = new double[features.length][];
        for (int i = 0; i < features.length; i++)
            original[i] = features[i].clone();
        double[][] targets = {{3, 5, 7, 9, 11, 13}, {-1, -5, -9, -13, -17, -21}};
        double[] expected = {7, -9};
        double[] actual = new double[2];
        for (int head = 0; head < targets.length; head++) {
            double[] savedTarget = targets[head].clone();
            var model = new OLS(features, targets[head]);
            actual[head] = model.predict(new double[]{0, 2});
            for (int row = 0; row < features.length; row++) {
                assertEquals(savedTarget[row], model.predict(features[row]), 1e-12);
                assertArrayEquals(original[row], features[row]);
            }
            assertArrayEquals(savedTarget, targets[head]);
        }
        assertArrayEquals(expected, actual, 1e-12);
    }
}
