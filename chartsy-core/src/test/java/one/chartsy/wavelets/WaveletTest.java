/*
 * Copyright 2024 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.wavelets;

import one.chartsy.data.RealVector;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class WaveletTest {

    @Test
    void test() {
        var dwt = new DiscreteWaveletTransform(new HaarWavelet());
        var values = dwt.transform(RealVector.from(new double[] { 57, 41, 9, 25, 49, 49, 41, 17 }));
        System.out.println(values.toString());
        //values[0] = 0;
        //System.out.println(Arrays.toString(dwt.inverseTransform(values)));
        // [98.99494936611669, -8.485281374238575, 32.0, 20.000000000000004, 11.31370849898476, -11.313708498984763, 0.0, 16.970562748477143]
    }
}