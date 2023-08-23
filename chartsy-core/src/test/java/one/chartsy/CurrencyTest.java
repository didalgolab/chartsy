/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CurrencyTest {

    @Test
    void currencyCode() {
        assertEquals("EUR", Currency.EUR.currencyCode());
        assertEquals("USD", Currency.USD.currencyCode());
        assertEquals("BTC", Currency.BTC.currencyCode());
    }

    @Test
    void of_gives_shared_instance() {
        assertSame(Currency.USD, Currency.of("USD"));
        assertSame(Currency.BTC, Currency.of("BTC"));
    }

    @Test
    void supports_custom_currencies() {
        Currency custom = Currency.of("Q");

        assertEquals("Q", custom.currencyCode());
        assertEquals("Q", custom.currencyName());
        assertSame(custom, Currency.of(custom.currencyCode()),
                "should give shared instance when requesting custom currency again");
    }
}