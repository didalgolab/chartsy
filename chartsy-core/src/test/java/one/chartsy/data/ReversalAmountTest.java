/* Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.data;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

class ReversalAmountTest {

    @Test
    void disallows_negative_reversal_amounts() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> ReversalAmount.of(-0.001));
        Assertions.assertThrows(IllegalArgumentException.class, () -> ReversalAmount.ofPercentage(-0.001));
    }

    @ParameterizedTest
    @EnumSource
    void allows_zero_amounts(ReversalType type) {
        var aPrice = Math.random();
        Assertions.assertEquals(aPrice, ReversalAmount.of(0).getTurnaroundPrice(aPrice, type));
    }

    @Test
    void getTurnaroundPrice() {
        var USD_1_CHANGE = ReversalAmount.of(1);
        assertThat( USD_1_CHANGE.getTurnaroundPrice(0.5, ReversalType.BULLISH) )
                .isEqualTo(1.5);
        assertThat( USD_1_CHANGE.getTurnaroundPrice(0.5, ReversalType.BEARISH) )
                .isEqualTo(-0.5);

        var TWICE_CHANGE = ReversalAmount.ofPercentage(100);
        assertThat( TWICE_CHANGE.getTurnaroundPrice(0.5, ReversalType.BULLISH) )
                .isEqualTo(1.0);
        assertThat( TWICE_CHANGE.getTurnaroundPrice(0.5, ReversalType.BEARISH) )
                .isEqualTo(0.25);
    }

    @Test
    void isFixed() {
        assertThat( ReversalAmount.of(1) )
                .extracting("fixed").isEqualTo(true);
        assertThat( ReversalAmount.ofPercentage(1) )
                .extracting("fixed").isEqualTo(true);
    }

    @Test
    void isAlgorithmic() {
        assertThat( ReversalAmount.of(1) )
                .extracting("algorithmic").isEqualTo(false);
        assertThat( ReversalAmount.ofPercentage(1) )
                .extracting("algorithmic").isEqualTo(false);
    }
}