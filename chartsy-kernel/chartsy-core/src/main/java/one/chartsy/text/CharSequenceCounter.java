/* Copyright 2025 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.text;

import java.util.Arrays;

/**
 * A fixed-width counter backed by a char[] and exposed as a CharSequence.
 * <p>
 * Characteristics:
 * - Holds a non-negative numeric suffix (one or more trailing digits). A non-digit prefix is allowed.
 * - Underlying storage is a fixed-length char[]; only the trailing digit run is incremented.
 * - Increment operations are performed in-place using only the char[].
 * - If an increment would require more digits in the numeric suffix than available (e.g., "...99" -> "...100"),
 *   an ArithmeticException("Overflow") is thrown and the state remains unchanged.
 *
 * <p><b>Thread-safety:</b> not synchronized.
 */
public class CharSequenceCounter implements CharSequence {

    private final char[] digits;

    public CharSequenceCounter(long initial) {
        this(String.valueOf(initial));
        if (initial < 0)
            throw new IllegalArgumentException("`initial` must be non-negative but was: " + initial);
    }

    public CharSequenceCounter(String initial) {
        if (initial.isEmpty() || !isAsciiDigit(initial.charAt(initial.length() - 1)))
            throw new IllegalArgumentException("`initial` must end with one or more digits: " + initial);
        this.digits = initial.toCharArray();
    }

    @Override
    public int length() {
        return digits.length;
    }

    @Override
    public char charAt(int index) {
        return digits[index];
    }

    @Override
    public CharSequence subSequence(int start, int end) {
        return new String(digits, start, end - start);
    }

    @Override
    public String toString() {
        return new String(digits);
    }

    public String getAndIncrement() {
        var valueBefore = toString();
        increment();
        return valueBefore;
    }

    public String incrementAndGet() {
        increment();
        return toString();
    }

    private void increment() {
        final int end = digits.length - 1;
        if (!isAsciiDigit(digits[end]))
            throw new IllegalStateException("Counter must end with a digit");

        int k = end;
        while (k >= 0 && digits[k] == '9')
            k--;
        if (k < 0 || !isAsciiDigit(digits[k]))
            throw new ArithmeticException("Overflow");

        digits[k]++;
        Arrays.fill(digits, k + 1, end + 1, '0');
    }

    private static boolean isAsciiDigit(char c) {
        return c >= '0' && c <= '9';
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj instanceof CharSequence other) {
            if (other.length() != this.length())
                return false;
            for (int i = 0; i < digits.length; i++)
                if (digits[i] != other.charAt(i))
                    return false;
            return true;
        }
        return false;
    }

    @Override
    public int hashCode() {
        return toString().hashCode();
    }
}
