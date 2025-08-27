/* Copyright 2025 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.math;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

public class EqualTolerantMathTest {

    EqualTolerantMath m = new EqualTolerantMath(1e-6);
    EqualTolerantMath m0 = new EqualTolerantMath(0.0);

    @Test
    void EqualTolerantMath() {
        assertDoesNotThrow(() -> new EqualTolerantMath(0.0));
        assertDoesNotThrow(() -> new EqualTolerantMath(1e-6));
        assertThrows(IllegalArgumentException.class, () -> new EqualTolerantMath(-1e-9));
        assertThrows(IllegalArgumentException.class, () -> new EqualTolerantMath(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> new EqualTolerantMath(Double.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class, () -> new EqualTolerantMath(Double.NEGATIVE_INFINITY));
    }

    @Test
    void isZero() {
        assertAll(
                () -> assertTrue(m.isZero(0.0)),
                () -> assertTrue(m.isZero(-0.0)),
                () -> assertTrue(m.isZero(5e-7)),
                () -> assertTrue(m.isZero(-5e-7)),
                () -> assertTrue(m.isZero(1e-6)),
                () -> assertFalse(m.isZero(2e-6)),
                () -> assertFalse(m.isZero(Double.NaN)),
                () -> assertFalse(m.isZero(Double.POSITIVE_INFINITY)),
                () -> assertFalse(m.isZero(Double.NEGATIVE_INFINITY))
        );
    }

    @Test
    void isNotZero() {
        assertAll(
                () -> assertFalse(m.isNotZero(0.0)),
                () -> assertFalse(m.isNotZero(1e-6)),
                () -> assertTrue(m.isNotZero(2e-6)),
                () -> assertTrue(m.isNotZero(-2e-6)),
                () -> assertTrue(m.isNotZero(Double.POSITIVE_INFINITY)),
                () -> assertTrue(m.isNotZero(Double.NEGATIVE_INFINITY)),
                () -> assertTrue(m.isNotZero(Double.NaN))
        );
    }

    @Test
    void isPositive() {
        assertAll(
                () -> assertFalse(m.isPositive(0.0)),
                () -> assertFalse(m.isPositive(5e-7)),
                () -> assertFalse(m.isPositive(1e-6)),
                () -> assertTrue(m.isPositive(2e-6)),
                () -> assertFalse(m.isPositive(-1.0)),
                () -> assertTrue(m.isPositive(Double.POSITIVE_INFINITY)),
                () -> assertFalse(m.isPositive(Double.NaN))
        );
    }

    @Test
    void isNegative() {
        assertAll(
                () -> assertFalse(m.isNegative(0.0)),
                () -> assertFalse(m.isNegative(-5e-7)),
                () -> assertFalse(m.isNegative(-1e-6)),
                () -> assertTrue(m.isNegative(-2e-6)),
                () -> assertFalse(m.isNegative(1.0)),
                () -> assertTrue(m.isNegative(Double.NEGATIVE_INFINITY)),
                () -> assertFalse(m.isNegative(Double.NaN))
        );
    }

    @Test
    void isEqual() {
        assertAll(
                () -> assertTrue(m.isEqual(1.0, 1.0)),
                () -> assertTrue(m.isEqual(1.0, 1.0 + 5e-7)),
                () -> assertTrue(m.isEqual(1.0, 1.0 + 1e-6)),
                () -> assertFalse(m.isEqual(1.0, 1.0 + 2e-6)),
                () -> assertTrue(m.isEqual(0.0, -0.0)),
                () -> assertTrue(m.isEqual(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY)),
                () -> assertTrue(m.isEqual(Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY)),
                () -> assertFalse(m.isEqual(Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)),
                () -> assertFalse(m.isEqual(Double.POSITIVE_INFINITY, 1.0)),
                () -> assertFalse(m.isEqual(Double.NaN, 1.0)),
                () -> assertFalse(m.isEqual(1.0, Double.NaN)),
                () -> assertTrue(m0.isEqual(2.0, 2.0)),
                () -> assertFalse(m0.isEqual(2.0, 2.0 + 1e-12))
        );
    }

    @Test
    void isNotEqual() {
        assertAll(
                () -> assertFalse(m.isNotEqual(1.0, 1.0)),
                () -> assertFalse(m.isNotEqual(1.0, 1.0 + 5e-7)),
                () -> assertFalse(m.isNotEqual(1.0, 1.0 + 1e-6)),
                () -> assertTrue(m.isNotEqual(1.0, 1.0 + 2e-6)),
                () -> assertTrue(m.isNotEqual(Double.POSITIVE_INFINITY, 1.0)),
                () -> assertTrue(m.isNotEqual(Double.NaN, 1.0)),
                () -> assertTrue(m.isNotEqual(1.0, Double.NaN))
        );
    }

    @Test
    void isLessThan() {
        assertAll(
                () -> assertFalse(m.isLessThan(1.0, 1.0)),
                () -> assertFalse(m.isLessThan(1.0, 1.0 + 5e-7)),
                () -> assertFalse(m.isLessThan(1.0, 1.0 + 1e-6)),
                () -> assertTrue(m.isLessThan(1.0, 1.0 + 2e-6)),
                () -> assertFalse(m.isLessThan(2.0, 1.0)),
                () -> assertTrue(m.isLessThan(-1.0, 1.0)),
                () -> assertTrue(m.isLessThan(1.0, Double.POSITIVE_INFINITY)),
                () -> assertTrue(m.isLessThan(Double.NEGATIVE_INFINITY, 0.0)),
                () -> assertFalse(m.isLessThan(Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)),
                () -> assertFalse(m.isLessThan(Double.NaN, 0.0)),
                () -> assertFalse(m.isLessThan(0.0, Double.NaN))
        );
    }

    @Test
    void isLessThanOrEqual() {
        assertAll(
                () -> assertTrue(m.isLessThanOrEqual(1.0, 1.0)),
                () -> assertTrue(m.isLessThanOrEqual(1.0, 1.0 + 5e-7)),
                () -> assertTrue(m.isLessThanOrEqual(1.0, 1.0 + 1e-6)),
                () -> assertTrue(m.isLessThanOrEqual(1.0, 1.0 + 2e-6)),
                () -> assertTrue(m.isLessThanOrEqual(1.0 + 5e-7, 1.0)),
                () -> assertFalse(m.isLessThanOrEqual(1.0 + 2e-6, 1.0)),
                () -> assertTrue(m.isLessThanOrEqual(-1.0, 1.0)),
                () -> assertTrue(m.isLessThanOrEqual(Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY)),
                () -> assertTrue(m.isLessThanOrEqual(1.0, Double.POSITIVE_INFINITY)),
                () -> assertFalse(m.isLessThanOrEqual(Double.POSITIVE_INFINITY, 1.0)),
                () -> assertFalse(m.isLessThanOrEqual(Double.NaN, 0.0)),
                () -> assertFalse(m.isLessThanOrEqual(0.0, Double.NaN)),
                () -> assertTrue(m0.isLessThanOrEqual(1.0, 1.0)),
                () -> assertFalse(m0.isLessThanOrEqual(1.0 + 1e-12, 1.0))
        );
    }

    @Test
    void isGreaterThan() {
        assertAll(
                () -> assertFalse(m.isGreaterThan(1.0, 1.0)),
                () -> assertFalse(m.isGreaterThan(1.0 + 5e-7, 1.0)),
                () -> assertFalse(m.isGreaterThan(1.0 + 1e-6, 1.0)),
                () -> assertTrue(m.isGreaterThan(1.0 + 2e-6, 1.0)),
                () -> assertFalse(m.isGreaterThan(1.0, 2.0)),
                () -> assertTrue(m.isGreaterThan(1.0, -1.0)),
                () -> assertTrue(m.isGreaterThan(Double.POSITIVE_INFINITY, 0.0)),
                () -> assertFalse(m.isGreaterThan(Double.NEGATIVE_INFINITY, 0.0)),
                () -> assertFalse(m.isGreaterThan(Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY)),
                () -> assertFalse(m.isGreaterThan(Double.NaN, 0.0)),
                () -> assertFalse(m.isGreaterThan(0.0, Double.NaN))
        );
    }

    @Test
    void isGreaterThanOrEqual() {
        assertAll(
                () -> assertTrue(m.isGreaterThanOrEqual(1.0, 1.0)),
                () -> assertTrue(m.isGreaterThanOrEqual(1.0 + 5e-7, 1.0)),
                () -> assertTrue(m.isGreaterThanOrEqual(1.0 + 1e-6, 1.0)),
                () -> assertTrue(m.isGreaterThanOrEqual(1.0 + 2e-6, 1.0)),
                () -> assertTrue(m.isGreaterThanOrEqual(1.0, 1.0 + 5e-7)),
                () -> assertFalse(m.isGreaterThanOrEqual(1.0, 1.0 + 2e-6)),
                () -> assertTrue(m.isGreaterThanOrEqual(1.0, -1.0)),
                () -> assertTrue(m.isGreaterThanOrEqual(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY)),
                () -> assertTrue(m.isGreaterThanOrEqual(Double.POSITIVE_INFINITY, 1.0)),
                () -> assertFalse(m.isGreaterThanOrEqual(1.0, Double.POSITIVE_INFINITY)),
                () -> assertFalse(m.isGreaterThanOrEqual(Double.NaN, 0.0)),
                () -> assertFalse(m.isGreaterThanOrEqual(0.0, Double.NaN)),
                () -> assertTrue(m0.isGreaterThanOrEqual(1.0, 1.0)),
                () -> assertFalse(m0.isGreaterThanOrEqual(1.0, 1.0 + 1e-12))
        );
    }
}
