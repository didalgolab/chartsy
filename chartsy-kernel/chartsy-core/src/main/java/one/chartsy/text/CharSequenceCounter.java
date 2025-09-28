/* Copyright 2025 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.text;

import java.util.Arrays;

/// A fixed-width counter backed by a char[] and exposed as a CharSequence.
///
/// Order produced for any fixed length:
/// - First: purely decimal 0-9 on every position, e.g. 000, 001, ..., 999.
/// - Then: base-36 (per position 0-9 then A-Z), e.g. 99A, 99B, ..., 99Z, 9A0, ..., ZZZ.
///
/// Characteristics:
/// - Monotonically increasing sequence of strings.
/// - Alphabet per position: 0-9 then A-Z (uppercase). Lowercase input is normalized to uppercase.
/// - Underlying storage is a fixed-length char[]; increments are performed in-place.
/// - If the state is all 'Z' (e.g., "ZZZ"), increment throws ArithmeticException("Overflow")
///   and the state remains unchanged.
/// - The maximum number of values for counter of length N is determined by the formula:
///   `(26*36^N + 35*10^N - 61) / 35`
///
/// **Thread-safety:** not synchronized.
///
/// @author Mariusz Bernacki
///
public class CharSequenceCounter implements CharSequence {

    private final char[] digits;
    private boolean hasLetter;

    public CharSequenceCounter(String initial) {
        if (initial == null || initial.isEmpty())
            throw new IllegalArgumentException("`initial` must be non-empty");

        String normalized = initial.toUpperCase();
        for (int i = 0; i < normalized.length(); i++) {
            char c = normalized.charAt(i);
            if (!isBase36(c))
                throw new IllegalArgumentException("`initial` must contain only [0-9A-Z]: " + initial);
            if (c >= 'A' && c <= 'Z')
                hasLetter = true;
        }
        this.digits = normalized.toCharArray();
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

    /// Gives `true` if the counter can be incremented without overflow.
    public boolean hasNext() {
        if (!hasLetter)
            return true;

        for (char c : digits)
            if (c != 'Z')
                return true;

        return false;
    }

    /// Returns the current value and then increments the counter.
    public String getAndIncrement() {
        String valueBefore = toString();
        increment();
        return valueBefore;
    }

    /// Increments the counter and then returns the new value.
    public String incrementAndGet() {
        increment();
        return toString();
    }

    /// Increment policy that matches the requested sequence:
    /// - If current state contains no letters (only '0'..'9' on all positions),
    ///   behave as a fixed-width decimal counter. Example: 019 -> 020, 199 -> 200.
    ///   Special case: if all positions are '9' (e.g., "999"), switch into base-36
    ///   by turning the last '9' into 'A': "999" -> "99A".
    /// - Otherwise (there is at least one 'A'..'Z'), increment as fixed-width base-36:
    ///   per position 0..9, A..Z with carry; "99Z" -> "9A0", "YZZ" -> "Z00".
    private void increment() {
        final int end = digits.length - 1;

        if (!hasLetter) {
            int k = end;
            while (k >= 0 && digits[k] == '9')
                k--;

            if (k < 0) {
                digits[end] = 'A';
                hasLetter = true;
            } else {
                digits[k] = (char) (digits[k] + 1);
                if (k < end)
                    Arrays.fill(digits, k + 1, end + 1, '0');
            }
        } else {
            int k = end;
            while (k >= 0 && digits[k] == 'Z')
                k--;
            if (k < 0)
                throw new ArithmeticException("Overflow");

            digits[k] = nextBase36(digits[k]);
            if (k < end)
                Arrays.fill(digits, k + 1, end + 1, '0');
        }
    }

    private static boolean isBase36(char c) {
        return (c >= '0' && c <= '9') || (c >= 'A' && c <= 'Z');
    }

    private static char nextBase36(char c) {
        if (c >= '0' && c <= '8')
            return (char) (c + 1);
        else if (c == '9')
            return 'A';
        else if (c >= 'A' && c <= 'Y')
            return (char) (c + 1);
        else
            throw new IllegalArgumentException("Invalid base-36 character: " + c);
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
