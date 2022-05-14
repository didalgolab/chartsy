/* Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy;

import org.junit.jupiter.api.Test;

import java.text.ParseException;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class CurrencyPairTest {
    CurrencyPair EUR_USD = CurrencyPair.of("EUR", "USD");
    CurrencyPair USD_CHF = CurrencyPair.of("USD", "CHF");
    CurrencyPair XAU_XAU = CurrencyPair.of("XAU", "XAU");//identity pair

    @Test
    void can_construct_CurrencyPair_from_two_currency_codes() {
        CurrencyPair pair = CurrencyPair.of("EUR", "USD");

        assertEquals("EUR", pair.baseCurrency());
        assertEquals("USD", pair.currency());
        assertEquals("EUR/USD", pair.toString());
    }

    @Test
    void can_construct_CurrencyPair_from_currency_pair_code() throws ParseException {
        assertEquals(EUR_USD, CurrencyPair.parse("EUR/USD"));
        assertEquals(EUR_USD, CurrencyPair.parse("EUR.USD"));
        assertEquals(EUR_USD, CurrencyPair.parse("EURUSD"));

        assertThrows(ParseException.class, () -> CurrencyPair.parse(""));
        assertThrows(ParseException.class, () -> CurrencyPair.parse("X"));
        assertThrows(ParseException.class, () -> CurrencyPair.parse("USD"));
    }

    @Test
    void isIdentity_tests_for_identity_pair() {
        assertFalse(EUR_USD.isIdentity());
        assertTrue(XAU_XAU.isIdentity());
    }

    @Test
    void inverse_gives_pair_inverted() {
        CurrencyPair pair = EUR_USD, inverse = pair.inverse();

        assertEquals(pair.currency(), inverse.baseCurrency());
        assertEquals(pair.baseCurrency(), inverse.currency());
        assertEquals(pair, inverse.inverse());
        assertTrue(inverse.isInverse(pair));
    }

    @Test
    void contains_tests_for_currency_containment() {
        assertTrue(EUR_USD.contains("EUR"));
        assertTrue(EUR_USD.contains("USD"));
        assertTrue(EUR_USD.contains(Currency.EUR));
        assertTrue(EUR_USD.contains(Currency.USD));

        assertFalse(EUR_USD.contains("GBP"));
        assertFalse(EUR_USD.contains(Currency.GBP));
    }

    @Test
    void other_gives_other_currency_in_a_pair_except_when_identity() {
        assertEquals(Optional.of("USD"), EUR_USD.other("EUR"));
        assertEquals(Optional.of("EUR"), EUR_USD.other("USD"));

        assertEquals(Optional.empty(), EUR_USD.other("GBP"));
        assertEquals(Optional.empty(), XAU_XAU.other("XAU"));
    }

    @Test
    void parse_gives_all_currency_codes_interned() throws ParseException {
        CurrencyPair pair1 = CurrencyPair.parse("EUR/USD");
        CurrencyPair pair2 = CurrencyPair.parse("EUR/USD");

        assertSame(pair1.baseCurrency(), pair2.baseCurrency());
        assertSame(pair1.currency(), pair2.currency());
    }

    @Test
    void toString_gives_parseable_text_representation() throws ParseException {
        String currencyPairCode = EUR_USD.toString();
        assertEquals(EUR_USD, CurrencyPair.parse(currencyPairCode));
    }

    @Test
    void toCurrencyCodes_gives_currencies_of_a_CurrencyPair() {
        assertEquals(Set.of("EUR","USD"), EUR_USD.toCurrencyCodes());
        assertEquals(Set.of("CHF","USD"), USD_CHF.toCurrencyCodes());
        assertEquals(Set.of("XAU"), XAU_XAU.toCurrencyCodes());
    }
}