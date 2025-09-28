/* Copyright 2025 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.text;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import org.assertj.core.api.Condition;
import org.junit.jupiter.api.Test;

class CharSequenceCounterTest {

    @Test
    void gives_expected_increment_order() {
        var c = new CharSequenceCounter("0");

        var expectedOrder = "123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        for (char expected : expectedOrder.toCharArray())
            assertEquals(String.valueOf(expected), c.incrementAndGet());
        assertThrows(ArithmeticException.class, c::incrementAndGet);
    }

    @Test
    void gives_strictly_increasing_values_and_numeric_first() {
        var previousValue = "000";
        var c = new CharSequenceCounter(previousValue);

        var numeric = new Condition<>(CharSequenceCounterTest::isNumeric, "numeric-only as it was followed by other numeric-only");
        while (c.hasNext()) {
            var currentValue = c.incrementAndGet();
            assertThat(currentValue).isGreaterThan(previousValue);
            if (isNumeric(currentValue))
                assertThat(previousValue).is(numeric);

            previousValue = currentValue;
        }

        assertThat(previousValue).isEqualTo("ZZZ");
    }

    private static boolean isNumeric(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (!Character.isDigit(s.charAt(i)))
                return false;
        }
        return true;
    }

    // ---------- equals / hashCode ----------

    @SuppressWarnings("MisorderedAssertEqualsArguments")
    @Test
    void equals_is_compatible_with_CharSequence() {
        var c = new CharSequenceCounter("123");
        assertEquals(c, new StringBuilder("123"));
        assertNotEquals(c, new StringBuilder("0123"));
    }

    @Test
    void hashCode_mimics_String_hashCode() {
        var c = new CharSequenceCounter("2024");
        assertEquals("2024".hashCode(), c.hashCode());
    }
}
