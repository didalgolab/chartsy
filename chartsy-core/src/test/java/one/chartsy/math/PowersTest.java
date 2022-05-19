/*
 * Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.math;

import org.junit.jupiter.api.Test;

import static one.chartsy.math.Powers.findNextPositivePowerOfTwo;

import static org.junit.jupiter.api.Assertions.*;

class PowersTest {

    /**
     * @author The Netty Project
     */
    @Test
    void testFindNextPositivePowerOfTwo() {
        assertEquals(1, findNextPositivePowerOfTwo(0));
        assertEquals(1, findNextPositivePowerOfTwo(1));
        assertEquals(1024, findNextPositivePowerOfTwo(1000));
        assertEquals(1024, findNextPositivePowerOfTwo(1023));
        assertEquals(2048, findNextPositivePowerOfTwo(2048));
        assertEquals(1 << 30, findNextPositivePowerOfTwo((1 << 30) - 1));
        assertEquals(1, findNextPositivePowerOfTwo(-1));
        assertEquals(1, findNextPositivePowerOfTwo(-10000));
        assertEquals(1 << 30, findNextPositivePowerOfTwo(Integer.MAX_VALUE));
        assertEquals(1 << 30, findNextPositivePowerOfTwo((1 << 30) + 1));
        assertEquals(1, findNextPositivePowerOfTwo(Integer.MIN_VALUE));
        assertEquals(1, findNextPositivePowerOfTwo(Integer.MIN_VALUE + 1));
    }
}