/* Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.money;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CurrencyConventionPriorityComparatorTest {
    final static CurrencyConventionPriorityComparator comparator = new CurrencyConventionPriorityComparator();

    @Test
    void testConventionOrder() {
        var pairs = List.of(
                "AUD/CAD", "AUD/CHF", "AUD/CZK", "AUD/DKK", "AUD/HKD", "AUD/HUF", "AUD/JPY", "AUD/MXN", "AUD/NOK",
                "AUD/NZD", "AUD/PLN", "AUD/SEK", "AUD/SGD", "AUD/USD", "AUD/ZAR", "CAD/CHF", "CAD/CZK", "CAD/DKK",
                "CAD/HKD", "CAD/HUF", "CAD/JPY", "CAD/MXN", "CAD/NOK", "CAD/PLN", "CAD/SEK", "CAD/SGD", "CAD/ZAR",
                "CHF/CZK", "CHF/DKK", "CHF/HKD", "CHF/HUF", "CHF/JPY", "CHF/MXN", "CHF/NOK", "CHF/PLN", "CHF/SEK",
                "CHF/SGD", "CHF/TRY", "CHF/ZAR", "DKK/CZK", "DKK/HKD", "DKK/HUF", "DKK/MXN", "DKK/NOK", "DKK/PLN",
                "DKK/SEK", "DKK/SGD", "DKK/ZAR", "EUR/AUD", "EUR/CAD", "EUR/CHF", "EUR/CZK", "EUR/DKK", "EUR/GBP",
                "EUR/HKD", "EUR/HUF", "EUR/JPY", "EUR/MXN", "EUR/NOK", "EUR/NZD", "EUR/PLN", "EUR/SEK", "EUR/SGD",
                "EUR/TRY", "EUR/USD", "EUR/ZAR", "GBP/AUD", "GBP/CAD", "GBP/CHF", "GBP/CZK", "GBP/DKK", "GBP/HKD",
                "GBP/HUF", "GBP/JPY", "GBP/MXN", "GBP/NOK", "GBP/NZD", "GBP/PLN", "GBP/SEK", "GBP/SGD", "GBP/USD",
                "GBP/ZAR", "JPY/CZK", "JPY/DKK", "JPY/HKD", "JPY/HUF", "JPY/MXN", "JPY/NOK", "JPY/PLN", "JPY/SEK",
                "JPY/SGD", "JPY/ZAR", "NOK/CZK", "NOK/HKD", "NOK/HUF", "NOK/MXN", "NOK/PLN", "NOK/SEK", "NOK/SGD",
                "NOK/ZAR", "NZD/CAD", "NZD/CHF", "NZD/CZK", "NZD/DKK", "NZD/HKD", "NZD/HUF", "NZD/JPY", "NZD/MXN",
                "NZD/NOK", "NZD/PLN", "NZD/SEK", "NZD/SGD", "NZD/USD", "NZD/ZAR", "USD/CAD", "USD/CHF", "USD/CZK",
                "USD/DKK", "USD/HKD", "USD/HUF", "USD/JPY", "USD/MXN", "USD/NOK", "USD/PLN", "USD/SEK", "USD/SGD",
                "USD/TRY", "USD/ZAR",
                "BTC/USD", "BTC/EUR", "XAU/XAG", "XAU/EUR", "XAU/USD", "XAG/EUR", "XAG/USD"
        );
        for (var pair : pairs)
            assertInOrder(pair);
    }

    private static void assertInOrder(String pair) {
        var orderedList = Arrays.asList(pair.split("/"));
        Collections.shuffle(orderedList);
        orderedList.sort(comparator);
        assertEquals(List.of(pair.split("/")), orderedList);
    }
}