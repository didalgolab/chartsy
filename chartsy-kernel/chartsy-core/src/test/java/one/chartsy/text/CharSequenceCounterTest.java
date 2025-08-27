/* Copyright 2025 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.text;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class CharSequenceCounterTest {

    // ---------- Construction ----------

    @Test
    void constructor_gives_fixed_width_decimal_from_number() {
        var c = new CharSequenceCounter(9876543210L);
        assertEquals("9876543210", c.toString());
        assertEquals(10, c.length());
    }

    @Test
    void constructor_does_throw_if_negative_initial() {
        assertThrows(IllegalArgumentException.class, () -> new CharSequenceCounter(-1));
        assertThrows(IllegalArgumentException.class, () -> new CharSequenceCounter(-123L));
    }

    // ---------- CharSequence behavior ----------

    @Test
    void length_gives_number_of_digits_and_is_constant_until_overflow() {
        var c = new CharSequenceCounter(1200);
        assertEquals(4, c.length());
        c.incrementAndGet(); // 1201
        c.incrementAndGet(); // 1202
        assertEquals(4, c.length());
    }

    @Test
    void charAt_gives_each_digit() {
        var c = new CharSequenceCounter(123);
        assertEquals('1', c.charAt(0));
        assertEquals('2', c.charAt(1));
        assertEquals('3', c.charAt(2));
    }

    @Test
    void charAt_does_throw_IndexOutOfBounds_when_outside_range() {
        var c = new CharSequenceCounter(42);
        assertThrows(IndexOutOfBoundsException.class, () -> c.charAt(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> c.charAt(2));
    }

    @Test
    void subSequence_gives_expected_slice_when_range_valid() {
        var c = new CharSequenceCounter(12345);
        assertEquals("234", c.subSequence(1, 4));
        assertEquals("", c.subSequence(2, 2));
        assertEquals("12345", c.subSequence(0, c.length()));
    }

    @Test
    void subSequence_does_throw_IndexOutOfBounds_when_range_invalid() {
        var c = new CharSequenceCounter(12345);
        assertThrows(IndexOutOfBoundsException.class, () -> c.subSequence(-1, 2));
        assertThrows(IndexOutOfBoundsException.class, () -> c.subSequence(3, 2));
        assertThrows(IndexOutOfBoundsException.class, () -> c.subSequence(0, 6));
    }

    @Test
    void toString_gives_value_snapshot_when_incremented_later() {
        var c = new CharSequenceCounter(8);
        var valueBefore = c.toString();
        c.incrementAndGet(); // 9
        assertEquals("8", valueBefore);
        assertEquals("9", c.toString());
    }

    // ---------- Increment semantics ----------

    @Test
    void incrementAndGet_gives_next_value() {
        var c = new CharSequenceCounter(42);
        assertEquals("43", c.incrementAndGet());
        assertEquals("43", c.toString());
    }

    @Test
    void getAndIncrement_gives_current_value_then_increments() {
        var c = new CharSequenceCounter(7);
        assertEquals("7", c.getAndIncrement());
        assertEquals("8", c.toString());
    }

    @Test
    void incrementAndGet_does_carry_over_multiple_trailing_nines() {
        var c = new CharSequenceCounter("X1299");
        assertEquals("X1300", c.incrementAndGet());
        assertEquals("X1301", c.incrementAndGet());
    }

    // ---------- Overflow semantics ----------

    @Test
    void getAndIncrement_does_throw_Overflow_when_no_capacity() {
        var c = new CharSequenceCounter(99);
        assertThrows(ArithmeticException.class, c::getAndIncrement);
        assertEquals("99", c.toString()); // state unchanged
    }

    @Test
    void incrementAndGet_does_throw_Overflow_when_no_capacity() {
        var c = new CharSequenceCounter(999);
        assertThrows(ArithmeticException.class, c::incrementAndGet);
        assertEquals("999", c.toString()); // state unchanged
    }

    // ---------- equals / hashCode ----------

    @Test
    void equals_does_value_comparison_against_CharSequence() {
        var c = new CharSequenceCounter(123);
        assertEquals(c, new StringBuilder("123"));
        assertNotEquals(c, new StringBuilder("0123"));
    }

    @Test
    void hashCode_gives_same_value_as_equivalent_String() {
        var c = new CharSequenceCounter(2024);
        assertEquals("2024".hashCode(), c.hashCode());
    }
}
