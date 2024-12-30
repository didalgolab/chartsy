/*
 * @(#)FastDoubleSwar.java
 * Copyright © 2023 Werner Randelshofer, Switzerland. MIT License.
 */
package one.chartsy.text;

/**
 * This class provides methods for parsing multiple characters at once using
 * the "SIMD with a register" (SWAR) technique.
 * <p>
 * References:
 * <dl>
 *     <dt>Leslie Lamport, Multiple Byte Processing with Full-Word Instructions</dt>
 *     <dd><a href="https://lamport.azurewebsites.net/pubs/multiple-byte.pdf">azurewebsites.net</a></dd>
 *
 *     <dt>Daniel Lemire, fast_float number parsing library: 4x faster than strtod.
 *     <a href="https://github.com/fastfloat/fast_float/blob/cc1e01e9eee74128e48d51488a6b1df4a767a810/LICENSE-MIT">MIT License</a>.</dt>
 *     <dd><a href="https://github.com/fastfloat/fast_float">github.com</a></dd>
 *
 *     <dt>Daniel Lemire, Number Parsing at a Gigabyte per Second,
 *     Software: Practice and Experience 51 (8), 2021.
 *     arXiv.2101.11408v3 [cs.DS] 24 Feb 2021</dt>
 *     <dd><a href="https://arxiv.org/pdf/2101.11408.pdf">arxiv.org</a></dd>
 * </dl>
 * </p>
 *
 * <p>Changes for Chartsy Framework:
 * <ul>
 *     <li>[MB] 2024-08-20: Removed methods: {@code isEightDigits}, {@code isEightDigitsUtf16},
 *     {@code isEightDigitsUtf16}, {@code isEightZeroes}</li>
 * </ul>
 * </p>
 */
class FastDoubleSwar {

    /**
     * Tries to parse eight digits at once using the
     * 'SIMD within a register technique' (SWAR).
     *
     * @param str    a character sequence
     * @param offset the index of the first character in the character sequence
     * @return the parsed digits or -1
     */
    public static long tryToParseEightHexDigits(CharSequence str, int offset) {
        long first = (long) str.charAt(offset) << 48
                | (long) str.charAt(offset + 1) << 32
                | (long) str.charAt(offset + 2) << 16
                | (long) str.charAt(offset + 3);
        long second = (long) str.charAt(offset + 4) << 48
                | (long) str.charAt(offset + 5) << 32
                | (long) str.charAt(offset + 6) << 16
                | (long) str.charAt(offset + 7);
        return FastDoubleSwar.tryToParseEightHexDigitsUtf16(first, second);
    }

    /**
     * Tries to parse eight hex digits from two longs using the
     * 'SIMD within a register technique' (SWAR).
     *
     * <pre>{@code
     * char[] chars = ...;
     * long first  = (long) chars[0] << 48
     *             | (long) chars[1] << 32
     *             | (long) chars[2] << 16
     *             | (long) chars[3];
     *
     * long second = (long) chars[4] << 48
     *             | (long) chars[5] << 32
     *             | (long) chars[6] << 16
     *             | (long) chars[7];
     * }</pre>
     *
     * @param first  contains 4 utf-16 characters in big endian order
     * @param second contains 4 utf-16 characters in big endian order
     * @return the parsed number,
     * returns a negative value if the two longs do not contain 8 hex digits
     */
    public static long tryToParseEightHexDigitsUtf16(long first, long second) {
        if (((first | second) & 0xff80_ff80_ff80_ff80L) != 0) {
            return -1;
        }
        long f = first * 0x0000_0000_0001_0100L;
        long s = second * 0x0000_0000_0001_0100L;
        long utf8Bytes = (f & 0xffff_0000_0000_0000L)
                | ((f & 0xffff_0000L) << 16)
                | ((s & 0xffff_0000_0000_0000L) >>> 32)
                | ((s & 0xffff_0000L) >>> 16);
        return tryToParseEightHexDigitsUtf8(utf8Bytes);
    }

    /**
     * Tries to parse eight digits from a long using the
     * 'SIMD within a register technique' (SWAR).
     *
     * @param chunk contains 8 ascii characters in big endian order
     * @return the parsed number,
     * returns a negative value if {@code value} does not contain 8 digits
     */
    public static long tryToParseEightHexDigitsUtf8(long chunk) {
        // The following code is based on the technique presented in the paper
        // by Leslie Lamport.

        // The predicates are true if the hsb of a byte is set.

        // Create a predicate for all bytes which are less than '0'
        long lt_0 = chunk - 0x30_30_30_30_30_30_30_30L;
        lt_0 &= 0x80_80_80_80_80_80_80_80L;

        // Create a predicate for all bytes which are greater than '9'
        long gt_9 = chunk + (0x39_39_39_39_39_39_39_39L ^ 0x7f_7f_7f_7f_7f_7f_7f_7fL);
        gt_9 &= 0x80_80_80_80_80_80_80_80L;

        // We can convert upper case characters to lower case by setting the 0x20 bit.
        // (This does not have an impact on decimal digits, which is very handy!).
        // Subtract character '0' (0x30) from each of the eight characters
        long vec = (chunk | 0x20_20_20_20_20_20_20_20L) - 0x30_30_30_30_30_30_30_30L;

        // Create a predicate for all bytes which are greater or equal than 'a'-'0' (0x30).
        long ge_a = vec + (0x30_30_30_30_30_30_30_30L ^ 0x7f_7f_7f_7f_7f_7f_7f_7fL);
        ge_a &= 0x80_80_80_80_80_80_80_80L;

        // Create a predicate for all bytes which are less or equal than 'f'-'0' (0x37).
        long le_f = vec - 0x37_37_37_37_37_37_37_37L;
        // we don't need to 'and' with 0x80…L here, because we 'and' this with ge_a anyway.
        //le_f &= 0x80_80_80_80_80_80_80_80L;

        // If a character is less than '0' or greater than '9' then it must be greater or equal than 'a' and less or equal then 'f'.
        if (((lt_0 | gt_9) != (ge_a & le_f))) {
            return -1;
        }

        // Expand the predicate to a byte mask
        long gt_9mask = (gt_9 >>> 7) * 0xffL;

        // Subtract 'a'-'0'+10 (0x27) from all bytes that are greater than 0x09.
        long v = vec & ~gt_9mask | vec - (0x27272727_27272727L & gt_9mask);

        // Compact all nibbles
        //return Long.compress(v, 0x0f0f0f0f_0f0f0f0fL);// since Java 19, Long.comporess is faster on Intel x64 but slower on Apple Silicon
        long v2 = v | v >>> 4;
        long v3 = v2 & 0x00ff00ff_00ff00ffL;
        long v4 = v3 | v3 >>> 8;
        long v5 = ((v4 >>> 16) & 0xffff_0000L) | v4 & 0xffffL;
        return v5;
    }
}