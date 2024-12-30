/*
 * Copyright 2024 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.wavelets;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

class BinomialTest {

    @Test
    void of_throwsIllegalArgumentException_whenNIsNegative() {
        assertThrows(IllegalArgumentException.class, () -> Binomial.of(-1, 0));
    }

    @Test
    void of_throwsIllegalArgumentException_whenKIsNegative() {
        assertThrows(IllegalArgumentException.class, () -> Binomial.of(0, -1));
    }

    @Test
    void of_throwsIllegalArgumentException_whenNIsLessThanK() {
        assertThrows(IllegalArgumentException.class, () -> Binomial.of(1, 2));
    }

    @ParameterizedTest
    @CsvSource({
            "0, 0, 1",
            "1, 0, 1",
            "1, 1, 1",
            "5, 2, 10",
            "10, 5, 252",
            "20, 10, 184756"
    })
    void of_calculatesCorrectBinomialCoefficient(int n, int k, long expected) {
        assertEquals(expected, Binomial.of(n, k));
    }

    @Test
    void of_handlesSymmetryProperty() {
        assertEquals(Binomial.of(5, 2), Binomial.of(5, 3));
    }

    @Test
    void of_throwsArithmeticException_onOverflow() {
        assertThrows(ArithmeticException.class, () -> Binomial.of(Integer.MAX_VALUE, 3));
    }
}