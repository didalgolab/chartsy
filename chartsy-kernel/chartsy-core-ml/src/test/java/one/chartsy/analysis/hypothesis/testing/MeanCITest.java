/*
 * Copyright 2024 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.analysis.hypothesis.testing;

import one.chartsy.data.RealVector;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MeanCITest {

    @Test
    void meanCI() {
        RealVector ci = MeanCI.meanCI(RealVector.fromValues(1, 2, 4, 6, 3));

        assertEquals(0.811612, ci.get(0), 0.00001);
        assertEquals(5.58839,  ci.get(1), 0.00001);
    }
}