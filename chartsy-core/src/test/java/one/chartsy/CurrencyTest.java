package one.chartsy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CurrencyTest {

    @Test void currencyCode() {
        assertEquals("EUR", Currency.EUR.currencyCode());
        assertEquals("USD", Currency.USD.currencyCode());
        assertEquals("BTC", Currency.BTC.currencyCode());
    }

    @Test void of_gives_shared_instance() {
        assertSame(Currency.USD, Currency.of("USD"));
        assertSame(Currency.BTC, Currency.of("BTC"));
    }
}