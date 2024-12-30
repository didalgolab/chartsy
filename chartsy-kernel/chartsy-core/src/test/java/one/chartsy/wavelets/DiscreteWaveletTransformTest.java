/*
 * Copyright 2024 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.wavelets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import org.junit.jupiter.api.Test;
import one.chartsy.data.RealVector; // Ensure this import is correct

class DiscreteWaveletTransformTest {

    private static final double DELTA = 1e-13;

    @Test
    void testHaarTransformAndInverseTransform() {
        testWaveletTransform(new HaarWavelet(), new double[] {1, 2, 3, 4});
    }

    @Test
    void testDaubechiesTransformAndInverseTransform() {
        testWaveletTransform(new DaubechiesWavelet(4), new double[] {1, 2, 3, 4, 5, 6, 7, 8});
    }

    @Test
    void testBiorthogonalSplineTransformAndInverseTransform() {
        testWaveletTransform(new BiorthogonalSplineWavelet(5, 3), new double[] {1, 2, 3, 4, 5, 6, 7, 8});
    }

    @Test
    void testSymletTransformAndInverseTransform() {
        testWaveletTransform(new SymletWavelet(4), new double[] {1, 2, 3, 4, 5, 6, 7, 8});
    }

    @Test
    void testCoifletTransformAndInverseTransform() {
        testWaveletTransform(new CoifletWavelet(3), new double[] {1, 2, 3, 4, 5, 6, 7, 8});
    }

    private void testWaveletTransform(Wavelet wavelet, double[] data) {
        var dwt = new DiscreteWaveletTransform(wavelet);
        var dataVector = RealVector.from(data);

        var transformed = dwt.transform(dataVector);
        var restored = dwt.inverseTransform(transformed);

        assertRealVectorEquals(dataVector, restored, DELTA);
    }

    private static void assertRealVectorEquals(RealVector expected, RealVector actual, double delta) {
        assertArrayEquals(expected.values(), actual.values(), delta);
    }
}