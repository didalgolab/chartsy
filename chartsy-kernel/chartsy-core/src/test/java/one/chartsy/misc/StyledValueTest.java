/* Copyright 2026 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.misc;

import one.chartsy.Candle;
import one.chartsy.SymbolResource;
import one.chartsy.TimeFrame;
import one.chartsy.data.CandleSeries;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

class StyledValueTest {
    @Test
    void value_structured_value_preserves_original_and_text_export() {
        var series = CandleSeries.of(SymbolResource.<Candle>of("TEST", TimeFrame.Period.DAILY),
                List.of(Candle.of(LocalDate.of(2026, 9, 5).atStartOfDay(), 42)));
        PriceSeriesThumbnail thumbnail = PriceSeriesThumbnail.of(series, 7);
        StyledValue value = StyledValue.of(thumbnail);

        assertSame(thumbnail, value.value());
        assertNull(value.numberValue());
        assertEquals(thumbnail.toString(), value.toRawValue());
        assertEquals(thumbnail.toString(), value.stringValue());
    }

    @Test
    void value_formatted_number_preserves_numeric_value_and_styles() {
        Double original = 1234.5;
        var format = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.ROOT));
        StyledValue value = StyledValue.of(original, format, Color.BLUE, Color.WHITE);

        assertSame(original, value.value());
        assertSame(original, value.numberValue());
        assertSame(original, value.toRawValue());
        assertEquals("1,234.50", value.toString());
        assertEquals(Color.BLUE, value.getStyle(StyleOption.BACKGROUND).orElseThrow());
        assertEquals(Color.WHITE, value.getStyle(StyleOption.FOREGROUND).orElseThrow());
    }

    @Test
    void value_numeric_string_keeps_original_and_existing_numeric_parsing() {
        StyledValue value = StyledValue.of("42");

        assertEquals("42", value.value());
        assertEquals(42, value.numberValue());
        assertEquals(42, value.toRawValue());
        assertEquals("42", value.toString());
        assertTrue(value.compareTo(StyledValue.of("100")) < 0);
    }

    @Test
    void value_null_keeps_existing_empty_display_and_raw_value() {
        StyledValue value = StyledValue.of(null);

        assertNull(value.value());
        assertNull(value.toRawValue());
        assertNull(value.numberValue());
        assertEquals("", value.toString());
    }

    @Test
    void compareTo_original_comparable_values_keep_existing_text_order() {
        StyledValue first = StyledValue.of(LocalDate.of(2026, 1, 1));
        StyledValue second = StyledValue.of(LocalDate.of(2026, 9, 4));

        assertEquals(first.toString().compareTo(second.toString()), first.compareTo(second));
        assertTrue(StyledValue.of(100).compareTo(first) < 0);
        assertTrue(first.compareTo(StyledValue.of(100)) > 0);
    }

    @Test
    void of_incompatible_formatter_keeps_original_and_falls_back_to_text() {
        LocalDate original = LocalDate.of(2026, 9, 4);
        StyledValue value = StyledValue.of(original, new DecimalFormat("0.0"));

        assertSame(original, value.value());
        assertEquals("2026-09-04", value.toRawValue());
        assertEquals("2026-09-04", value.toString());
    }
}
