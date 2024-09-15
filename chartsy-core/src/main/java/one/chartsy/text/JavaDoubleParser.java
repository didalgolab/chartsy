/*
 * @(#)JavaDoubleParser.java
 * Copyright © 2023 Werner Randelshofer, Switzerland. MIT License.
 */
package one.chartsy.text;

import java.util.Arrays;

/**
 * Parses a {@code double} value; the supported syntax is compatible with
 * {@link Double#valueOf(String)}.
 * <p>
 * <b>Syntax</b>
 * <p>
 * Leading and trailing whitespace characters in {@code str} are ignored.
 * Whitespace is removed as if by the {@link java.lang.String#trim()} method;
 * that is, characters in the range [U+0000,U+0020].
 * <p>
 * The rest of {@code str} should constitute a Java {@code FloatingPointLiteral}
 * as described by the lexical syntax rules shown below:
 * <blockquote>
 * <dl>
 * <dt><i>FloatingPointLiteral:</i></dt>
 * <dd><i>[Sign]</i> {@code NaN}</dd>
 * <dd><i>[Sign]</i> {@code Infinity}</dd>
 * <dd><i>[Sign] DecimalFloatingPointLiteral</i></dd>
 * <dd><i>[Sign] HexFloatingPointLiteral</i></dd>
 * <dd><i>SignedInteger</i></dd>
 * </dl>
 *
 * <dl>
 * <dt><i>HexFloatingPointLiteral</i>:
 * <dd><i>HexSignificand BinaryExponent [FloatTypeSuffix]</i>
 * </dl>
 *
 * <dl>
 * <dt><i>HexSignificand:</i>
 * <dd><i>HexNumeral</i>
 * <dd><i>HexNumeral</i> {@code .}
 * <dd>{@code 0x} <i>[HexDigits]</i> {@code .} <i>HexDigits</i>
 * <dd>{@code 0X} <i>[HexDigits]</i> {@code .} <i>HexDigits</i>
 * </dl>
 *
 * <dl>
 * <dt><i>BinaryExponent:</i>
 * <dd><i>BinaryExponentIndicator SignedInteger</i>
 * </dl>
 *
 * <dl>
 * <dt><i>BinaryExponentIndicator:</i>
 * <dd>{@code p}
 * <dd>{@code P}
 * </dl>
 *
 * <dl>
 * <dt><i>DecimalFloatingPointLiteral:</i>
 * <dd><i>DecSignificand [DecExponent] [FloatTypeSuffix]</i>
 * </dl>
 *
 * <dl>
 * <dt><i>DecSignificand:</i>
 * <dd><i>IntegerPart {@code .} [FractionPart]</i>
 * <dd><i>{@code .} FractionPart</i>
 * <dd><i>IntegerPart</i>
 * </dl>
 *
 * <dl>
 * <dt><i>IntegerPart:</i>
 * <dd><i>Digits</i>
 * </dl>
 *
 * <dl>
 * <dt><i>FractionPart:</i>
 * <dd><i>Digits</i>
 * </dl>
 *
 * <dl>
 * <dt><i>DecExponent:</i>
 * <dd><i>ExponentIndicator SignedInteger</i>
 * </dl>
 *
 * <dl>
 * <dt><i>ExponentIndicator:</i>
 * <dd><i>e</i>
 * <dd><i>E</i>
 * </dl>
 *
 * <dl>
 * <dt><i>SignedInteger:</i>
 * <dd><i>[Sign] Digits</i>
 * </dl>
 *
 * <dl>
 * <dt><i>Sign:</i>
 * <dd><i>+</i>
 * <dd><i>-</i>
 * </dl>
 *
 * <dl>
 * <dt><i>Digits:</i>
 * <dd><i>Digit {Digit}</i>
 * </dl>
 *
 * <dl>
 * <dt><i>Digit:</i>
 * <dd><i>(one of)</i>
 * <dd>{@code 0 1 2 3 4 5 6 7 8 9}
 * </dl>
 *
 * <dl>
 * <dt><i>HexNumeral:</i>
 * <dd>{@code 0} {@code x} <i>HexDigits</i>
 * <dd>{@code 0} {@code X} <i>HexDigits</i>
 * </dl>
 *
 * <dl>
 * <dt><i>HexDigits:</i>
 * <dd><i>HexDigit {HexDigit}</i>
 * </dl>
 *
 * <dl>
 * <dt><i>HexDigit:</i>
 * <dd><i>(one of)</i>
 * <dd>{@code 0 1 2 3 4 5 6 7 8 9 a b c d e f A B C D E F}
 * </dl>
 *
 * <dl>
 * <dt><i>FloatTypeSuffix:</i>
 * <dd><i>(one of)</i>
 * <dd>{@code f F d D}
 * </dl>
 * </blockquote>
 * <p>
 * Expected character lengths for values produced by {@link Double#toString}:
 * <ul>
 *     <li>{@code DecSignificand} ({@code IntegerPart} + {@code FractionPart}):
 *     1 to 17 digits</li>
 *     <li>{@code IntegerPart}: 1 to 7 digits</li>
 *     <li>{@code FractionPart}: 1 to 16 digits</li>
 *     <li>{@code SignedInteger} in exponent: 1 to 3 digits</li>
 *     <li>{@code FloatingPointLiteral}: 1 to 24 characters, e.g. "-1.2345678901234568E-300"</li>
 * </ul>
 * Maximal input length supported by this parser:
 * <ul>
 *     <li>{@code FloatingPointLiteral} with or without white space around it:
 *     {@link Integer#MAX_VALUE} - 4 = 2,147,483,643 characters.</li>
 * </ul>
 * <p>
 * References:
 * <dl>
 *     <dt>The Java® Language Specification, Java SE 18 Edition, Chapter 3. Lexical Structure, 3.10.2. Floating-Point Literals </dt>
 *     <dd><a href="https://docs.oracle.com/javase/specs/jls/se18/html/jls-3.html#jls-3.10.2">docs.oracle.com</a></dd>
 * </dl>
 * <p>
 * Modifications:
 * <ul>
 *     <li>[MB] 2024-08-20: Repackaged from <a href="https://github.com/wrandelshofer/FastDoubleParser">https://github.com/wrandelshofer/FastDoubleParser</a>
 *     for use in the Chartsy Framework.</li>
 * </ul>
 */
public class JavaDoubleParser {

    private static final BitsFromCharSequence CHAR_SEQUENCE_PARSER = new BitsFromCharSequence();

    /**
     * Convenience method for calling {@link #parseDouble(CharSequence, int, int)}.
     *
     * @param str the string to be parsed
     * @return the parsed value
     * @throws NullPointerException  if the string is null
     * @throws NumberFormatException if the string can not be parsed successfully
     */
    public static double parseDouble(CharSequence str) throws NumberFormatException {
        return parseDouble(str, 0, str.length());
    }

    /**
     * Parses a {@code FloatingPointLiteral} from a {@link CharSequence} and converts it
     * into a {@code double} value.
     *
     * @param str    the string to be parsed
     * @param offset the start offset of the {@code FloatingPointLiteral} in {@code str}
     * @param length the length of {@code FloatingPointLiteral} in {@code str}
     * @return the parsed value
     * @throws NullPointerException     if the string is null
     * @throws IllegalArgumentException if offset or length are illegal
     * @throws NumberFormatException    if the string can not be parsed successfully
     */
    public static double parseDouble(CharSequence str, int offset, int length) throws NumberFormatException {
        long bitPattern = CHAR_SEQUENCE_PARSER.parseFloatingPointLiteral(str, offset, length);
        return Double.longBitsToDouble(bitPattern);
    }

    /**
     * Parses a {@code double} from a {@link CharSequence}.
     */
    private static final class BitsFromCharSequence extends AbstractBitsFromCharSequence {

        @Override
        long nan() {
            return Double.doubleToRawLongBits(Double.NaN);
        }

        @Override
        long negativeInfinity() {
            return Double.doubleToRawLongBits(Double.NEGATIVE_INFINITY);
        }

        @Override
        long positiveInfinity() {
            return Double.doubleToRawLongBits(Double.POSITIVE_INFINITY);
        }

        @Override
        long valueOfFloatLiteral(CharSequence str, int startIndex, int endIndex, boolean isNegative,
                                 long significand, int exponent, boolean isSignificandTruncated,
                                 int exponentOfTruncatedSignificand) {
            double d = FastDoubleMath.tryDecFloatToDoubleTruncated(isNegative, significand, exponent, isSignificandTruncated,
                    exponentOfTruncatedSignificand);
            return Double.doubleToRawLongBits(Double.isNaN(d)
                    ? Double.parseDouble(str.subSequence(startIndex, endIndex).toString())
                    : d);
        }

        @Override
        long valueOfHexLiteral(
                CharSequence str, int startIndex, int endIndex, boolean isNegative, long significand, int exponent,
                boolean isSignificandTruncated, int exponentOfTruncatedSignificand) {
            double d = FastDoubleMath.tryHexFloatToDoubleTruncated(isNegative, significand, exponent, isSignificandTruncated,
                    exponentOfTruncatedSignificand);
            return Double.doubleToRawLongBits(Double.isNaN(d)
                    ? Double.parseDouble(str.subSequence(startIndex, endIndex).toString())
                    : d);
        }
    }

    /**
     * Parses a Java {@code FloatingPointLiteral} from a {@link CharSequence}.
     * <p>
     * This class should have a type parameter for the return value of its parse
     * methods. Unfortunately Java does not support type parameters for primitive
     * types. As a workaround we use {@code long}. A {@code long} has enough bits to
     * fit a {@code double} value or a {@code float} value.
     * <p>
     * See {@link JavaDoubleParser} for the grammar of {@code FloatingPointLiteral}.
     */
    private static abstract class AbstractBitsFromCharSequence extends AbstractFloatValueParser {

        /**
         * Skips optional white space in the provided string
         *
         * @param str      a string
         * @param index    start index (inclusive) of the optional white space
         * @param endIndex end index (exclusive) of the optional white space
         * @return index after the optional white space
         */
        private static int skipWhitespace(CharSequence str, int index, int endIndex) {
            while (index < endIndex && str.charAt(index) <= ' ') {
                index++;
            }
            return index;
        }

        /**
         * @return a NaN constant in the specialized type wrapped in a {@code long}
         */
        abstract long nan();

        /**
         * @return a negative infinity constant in the specialized type wrapped in a
         * {@code long}
         */
        abstract long negativeInfinity();

        /**
         * Parses a {@code DecimalFloatingPointLiteral} production with optional
         * trailing white space until the end of the text.
         * Given that we have already consumed the optional leading zero of
         * the {@code DecSignificand}.
         * <blockquote>
         * <dl>
         * <dt><i>DecimalFloatingPointLiteralWithWhiteSpace:</i></dt>
         * <dd><i>DecimalFloatingPointLiteral [WhiteSpace] EOT</i></dd>
         * </dl>
         * </blockquote>
         * See {@link JavaDoubleParser} for the grammar of
         * {@code DecimalFloatingPointLiteral} and {@code DecSignificand}.
         *
         * @param str            a string
         * @param index          the current index
         * @param startIndex     start index inclusive of the {@code DecimalFloatingPointLiteralWithWhiteSpace}
         * @param endIndex       end index (exclusive)
         * @param isNegative     true if the float value is negative
         * @param hasLeadingZero true if we have consumed the optional leading zero
         * @return the bit pattern of the parsed value, if the input is legal;
         * otherwise, {@code -1L}.
         */
        private long parseDecFloatLiteral(CharSequence str, int index, int startIndex, int endIndex, boolean isNegative, boolean hasLeadingZero) {
            // Parse significand
            // -----------------
            // Note: a multiplication by a constant is cheaper than an
            //       arbitrary integer multiplication.
            long significand = 0;// significand is treated as an unsigned long
            final int significandStartIndex = index;
            int virtualIndexOfPoint = -1;
            boolean illegal = false;
            char ch = 0;
            for (; index < endIndex; index++) {
                ch = str.charAt(index);
                int digit = (char) (ch - '0');
                if (digit < 10) {
                    // This might overflow, we deal with it later.
                    significand = 10 * significand + digit;
                } else if (ch == '.') {
                    illegal |= virtualIndexOfPoint >= 0;
                    virtualIndexOfPoint = index;
                /*
                for (; index < endIndex - 4; index += 4) {
                    int digits = FastDoubleSwar.tryToParseFourDigits(str, index + 1);
                    if (digits < 0) {
                        break;
                    }
                    // This might overflow, we deal with it later.
                    significand = 10_000L * significand + digits;
                }*/
                } else {
                    break;
                }
            }
            final int digitCount;
            final int significandEndIndex = index;
            int exponent;
            if (virtualIndexOfPoint < 0) {
                digitCount = significandEndIndex - significandStartIndex;
                virtualIndexOfPoint = significandEndIndex;
                exponent = 0;
            } else {
                digitCount = significandEndIndex - significandStartIndex - 1;
                exponent = virtualIndexOfPoint - significandEndIndex + 1;
            }

            // Parse exponent number
            // ---------------------
            int expNumber = 0;
            if ((ch | 0x20) == 'e') {// equals ignore case
                ch = charAt(str, ++index, endIndex);
                boolean isExponentNegative = ch == '-';
                if (isExponentNegative || ch == '+') {
                    ch = charAt(str, ++index, endIndex);
                }
                int digit = (char) (ch - '0');
                illegal |= digit >= 10;
                do {
                    // Guard against overflow
                    if (expNumber < AbstractFloatValueParser.MAX_EXPONENT_NUMBER) {
                        expNumber = 10 * expNumber + digit;
                    }
                    ch = charAt(str, ++index, endIndex);
                    digit = (char) (ch - '0');
                } while (digit < 10);
                if (isExponentNegative) {
                    expNumber = -expNumber;
                }
                exponent += expNumber;
            }

            // Skip optional FloatTypeSuffix
            // long-circuit-or is faster than short-circuit-or
            // ------------------------
            if ((ch | 0x22) == 'f') { // ~ "fFdD"
                index++;
            }

            // Skip trailing whitespace and check if FloatingPointLiteral is complete
            // ------------------------
            index = skipWhitespace(str, index, endIndex);
            if (illegal || index < endIndex
                    || !hasLeadingZero && digitCount == 0) {
                throw new NumberFormatException(SYNTAX_ERROR);
            }

            // Re-parse significand in case of a potential overflow
            // -----------------------------------------------
            final boolean isSignificandTruncated;
            int skipCountInTruncatedDigits = 0;//counts +1 if we skipped over the decimal point
            int exponentOfTruncatedSignificand;
            if (digitCount > 19) {
                significand = 0;
                for (index = significandStartIndex; index < significandEndIndex; index++) {
                    ch = str.charAt(index);
                    if (ch == '.') {
                        skipCountInTruncatedDigits++;
                    } else {
                        if (Long.compareUnsigned(significand, AbstractFloatValueParser.MINIMAL_NINETEEN_DIGIT_INTEGER) < 0) {
                            significand = 10 * significand + ch - '0';
                        } else {
                            break;
                        }
                    }
                }
                isSignificandTruncated = index < significandEndIndex;
                exponentOfTruncatedSignificand = virtualIndexOfPoint - index + skipCountInTruncatedDigits + expNumber;
            } else {
                isSignificandTruncated = false;
                exponentOfTruncatedSignificand = 0;
            }
            return valueOfFloatLiteral(str, startIndex, endIndex, isNegative, significand, exponent, isSignificandTruncated,
                    exponentOfTruncatedSignificand);
        }

        /**
         * Parses a {@code FloatingPointLiteral} production with optional leading and trailing
         * white space.
         * <blockquote>
         * <dl>
         * <dt><i>FloatingPointLiteralWithWhiteSpace:</i></dt>
         * <dd><i>[WhiteSpace] FloatingPointLiteral [WhiteSpace]</i></dd>
         * </dl>
         * </blockquote>
         * See {@link JavaDoubleParser} for the grammar of
         * {@code FloatingPointLiteral}.
         *
         * @param str    a string containing a {@code FloatingPointLiteralWithWhiteSpace}
         * @param offset start offset of {@code FloatingPointLiteralWithWhiteSpace} in {@code str}
         * @param length length of {@code FloatingPointLiteralWithWhiteSpace} in {@code str}
         * @return the bit pattern of the parsed value, if the input is legal;
         * otherwise, {@code -1L}.
         */
        public final long parseFloatingPointLiteral(CharSequence str, int offset, int length) {
            final int endIndex = checkBounds(str.length(), offset, length);

            // Skip leading whitespace
            // -------------------
            int index = skipWhitespace(str, offset, endIndex);
            if (index == endIndex) {
                throw new NumberFormatException(SYNTAX_ERROR);
            }
            char ch = str.charAt(index);

            // Parse optional sign
            // -------------------
            final boolean isNegative = ch == '-';
            if (isNegative || ch == '+') {
                ch = charAt(str, ++index, endIndex);
                if (ch == 0) {
                    throw new NumberFormatException(SYNTAX_ERROR);
                }
            }

            // Parse NaN or Infinity (this occurs rarely)
            // ---------------------
            if (ch >= 'I') {
                return parseNaNOrInfinity(str, index, endIndex, isNegative);
            }

            // Parse optional leading zero
            // ---------------------------
            final boolean hasLeadingZero = ch == '0';
            if (hasLeadingZero) {
                ch = charAt(str, ++index, endIndex);
                if ((ch | 0x20) == 'x') {// equals ignore case
                    return parseHexFloatLiteral(str, index + 1, offset, endIndex, isNegative);
                }
            }

            return parseDecFloatLiteral(str, index, offset, endIndex, isNegative, hasLeadingZero);
        }

        /**
         * Parses the following rules
         * (more rules are defined in {@link AbstractFloatValueParser}):
         * <dl>
         * <dt><i>RestOfHexFloatingPointLiteral</i>:
         * <dd><i>RestOfHexSignificand BinaryExponent</i>
         * </dl>
         *
         * <dl>
         * <dt><i>RestOfHexSignificand:</i>
         * <dd><i>HexDigits</i>
         * <dd><i>HexDigits</i> {@code .}
         * <dd><i>[HexDigits]</i> {@code .} <i>HexDigits</i>
         * </dl>
         *
         * @param str        the input string
         * @param index      index to the first character of RestOfHexFloatingPointLiteral
         * @param startIndex the start index of the string
         * @param endIndex   the end index of the string
         * @param isNegative if the resulting number is negative
         * @return the bit pattern of the parsed value, if the input is legal;
         * otherwise, {@code -1L}.
         */
        private long parseHexFloatLiteral(
                CharSequence str, int index, int startIndex, int endIndex, boolean isNegative) {

            // Parse HexSignificand
            // ------------
            long significand = 0;// significand is treated as an unsigned long
            int exponent = 0;
            final int significandStartIndex = index;
            int virtualIndexOfPoint = -1;
            final int digitCount;
            boolean illegal = false;
            char ch = 0;
            for (; index < endIndex; index++) {
                ch = str.charAt(index);
                // Table look up is faster than a sequence of if-else-branches.
                int hexValue = lookupHex(ch);
                if (hexValue >= 0) {
                    significand = significand << 4 | hexValue;// This might overflow, we deal with it later.
                } else if (hexValue == AbstractFloatValueParser.DECIMAL_POINT_CLASS) {
                    illegal |= virtualIndexOfPoint >= 0;
                    virtualIndexOfPoint = index;
                    for (; index < endIndex - 8; index += 8) {
                        long parsed = FastDoubleSwar.tryToParseEightHexDigits(str, index + 1);
                        if (parsed >= 0) {
                            // This might overflow, we deal with it later.
                            significand = (significand << 32) + parsed;
                        } else {
                            break;
                        }
                    }
                } else {
                    break;
                }
            }
            final int significandEndIndex = index;
            if (virtualIndexOfPoint < 0) {
                digitCount = significandEndIndex - significandStartIndex;
                virtualIndexOfPoint = significandEndIndex;
            } else {
                digitCount = significandEndIndex - significandStartIndex - 1;
                exponent = Math.min(virtualIndexOfPoint - index + 1, AbstractFloatValueParser.MAX_EXPONENT_NUMBER) * 4;
            }

            // Parse exponent
            // --------------
            int expNumber = 0;
            final boolean hasExponent = (ch | 0x20) == 'p';// equals ignore case;
            if (hasExponent) {
                ch = charAt(str, ++index, endIndex);
                boolean isExponentNegative = ch == '-';
                if (isExponentNegative || ch == '+') {
                    ch = charAt(str, ++index, endIndex);
                }
                int digit = (char) (ch - '0');
                illegal |= digit >= 10;
                do {
                    // Guard against overflow
                    if (expNumber < AbstractFloatValueParser.MAX_EXPONENT_NUMBER) {
                        expNumber = 10 * expNumber + digit;
                    }
                    ch = charAt(str, ++index, endIndex);
                    digit = (char) (ch - '0');
                } while (digit < 10);
                if (isExponentNegative) {
                    expNumber = -expNumber;
                }
                exponent += expNumber;
            }

            // Skip optional FloatTypeSuffix
            // long-circuit-or is faster than short-circuit-or
            // ------------------------
            if ((ch | 0x22) == 'f') { // ~ "fFdD"
                index++;
            }

            // Skip trailing whitespace and check if FloatingPointLiteral is complete
            // ------------------------
            index = skipWhitespace(str, index, endIndex);
            if (illegal || index < endIndex
                    || digitCount == 0
                    || !hasExponent) {
                throw new NumberFormatException(SYNTAX_ERROR);
            }

            // Re-parse significand in case of a potential overflow
            // -----------------------------------------------
            final boolean isSignificandTruncated;
            int skipCountInTruncatedDigits = 0;//counts +1 if we skipped over the decimal point
            if (digitCount > 16) {
                significand = 0;
                for (index = significandStartIndex; index < significandEndIndex; index++) {
                    ch = str.charAt(index);
                    // Table look up is faster than a sequence of if-else-branches.
                    int hexValue = lookupHex(ch);
                    if (hexValue >= 0) {
                        if (Long.compareUnsigned(significand, AbstractFloatValueParser.MINIMAL_NINETEEN_DIGIT_INTEGER) < 0) {
                            significand = significand << 4 | hexValue;
                        } else {
                            break;
                        }
                    } else {
                        skipCountInTruncatedDigits++;
                    }
                }
                isSignificandTruncated = index < significandEndIndex;
            } else {
                isSignificandTruncated = false;
            }

            return valueOfHexLiteral(str, startIndex, endIndex, isNegative, significand, exponent, isSignificandTruncated,
                    (virtualIndexOfPoint - index + skipCountInTruncatedDigits) * 4 + expNumber);
        }


        private long parseNaNOrInfinity(CharSequence str, int index, int endIndex, boolean isNegative) {
            if (str.charAt(index) == 'N') {
                if (index + 2 < endIndex
                        // && str.charAt(index) == 'N'
                        && str.charAt(index + 1) == 'a'
                        && str.charAt(index + 2) == 'N') {

                    index = skipWhitespace(str, index + 3, endIndex);
                    if (index == endIndex) {
                        return nan();
                    }
                }
            } else {
                if (index + 7 < endIndex
                        && str.charAt(index) == 'I'
                        && str.charAt(index + 1) == 'n'
                        && str.charAt(index + 2) == 'f'
                        && str.charAt(index + 3) == 'i'
                        && str.charAt(index + 4) == 'n'
                        && str.charAt(index + 5) == 'i'
                        && str.charAt(index + 6) == 't'
                        && str.charAt(index + 7) == 'y'
                ) {
                    index = skipWhitespace(str, index + 8, endIndex);
                    if (index == endIndex) {
                        return isNegative ? negativeInfinity() : positiveInfinity();
                    }
                }
            }
            throw new NumberFormatException(SYNTAX_ERROR);
        }

        /**
         * @return a positive infinity constant in the specialized type wrapped in a
         * {@code long}
         */
        abstract long positiveInfinity();

        /**
         * Computes a float value from the given components of a decimal float
         * literal.
         *
         * @param str                            the string that contains the float literal (and maybe more)
         * @param startIndex                     the start index (inclusive) of the float literal
         *                                       inside the string
         * @param endIndex                       the end index (exclusive) of the float literal inside
         *                                       the string
         * @param isNegative                     whether the float value is negative
         * @param significand                    the significand of the float value (can be truncated)
         * @param exponent                       the exponent of the float value
         * @param isSignificandTruncated         whether the significand is truncated
         * @param exponentOfTruncatedSignificand the exponent value of the truncated
         *                                       significand
         * @return the bit pattern of the parsed value, if the input is legal;
         * otherwise, {@code -1L}.
         */
        abstract long valueOfFloatLiteral(
                CharSequence str, int startIndex, int endIndex,
                boolean isNegative, long significand, int exponent,
                boolean isSignificandTruncated, int exponentOfTruncatedSignificand);

        /**
         * Computes a float value from the given components of a hexadecimal float
         * literal.
         *
         * @param str                            the string that contains the float literal (and maybe more)
         * @param startIndex                     the start index (inclusive) of the float literal
         *                                       inside the string
         * @param endIndex                       the end index (exclusive) of the float literal inside
         *                                       the string
         * @param isNegative                     whether the float value is negative
         * @param significand                    the significand of the float value (can be truncated)
         * @param exponent                       the exponent of the float value
         * @param isSignificandTruncated         whether the significand is truncated
         * @param exponentOfTruncatedSignificand the exponent value of the truncated
         *                                       significand
         * @return the bit pattern of the parsed value, if the input is legal;
         * otherwise, {@code -1L}.
         */
        abstract long valueOfHexLiteral(
                CharSequence str, int startIndex, int endIndex,
                boolean isNegative, long significand, int exponent,
                boolean isSignificandTruncated, int exponentOfTruncatedSignificand);
    }

    /**
     * Abstract base class for parsers that parse a {@code FloatingPointLiteral} from a
     * character sequence ({@code str}).
     * <p>
     * This is a C++ to Java port of Daniel Lemire's fast_double_parser.
     * <p>
     * References:
     * <dl>
     *     <dt>Daniel Lemire, fast_float number parsing library: 4x faster than strtod.
     *     <a href="https://github.com/fastfloat/fast_float/blob/dc88f6f882ac7eb8ec3765f633835cb76afa0ac2/LICENSE-MIT">MIT License</a>.</dt>
     *     <dd><a href="https://github.com/fastfloat/fast_float">github.com</a></dd>
     *
     *     <dt>Daniel Lemire, Number Parsing at a Gigabyte per Second,
     *     Software: Practice and Experience 51 (8), 2021.
     *     arXiv.2101.11408v3 [cs.DS] 24 Feb 2021</dt>
     *     <dd><a href="https://arxiv.org/pdf/2101.11408.pdf">arxiv.org</a></dd>
     * </dl>
     */
    private static abstract class AbstractFloatValueParser extends AbstractNumberParser {

        /**
         * This is the smallest non-negative number that has 19 decimal digits.
         */
        final static long MINIMAL_NINETEEN_DIGIT_INTEGER = 1000_00000_00000_00000L;

        /**
         * The decimal exponent of a double has a range of -324 to +308.
         * The hexadecimal exponent of a double has a range of -1022 to +1023.
         */
        final static int MAX_EXPONENT_NUMBER = 1024;
    }

    private static abstract class AbstractNumberParser {
        /**
         * Message text for the {@link IllegalArgumentException} that is thrown
         * when offset or length are illegal
         */
        public static final String ILLEGAL_OFFSET_OR_ILLEGAL_LENGTH = "offset < 0 or length > str.length";
        /**
         * Message text for the {@link NumberFormatException} that is thrown
         * when the syntax is illegal.
         */
        public static final String SYNTAX_ERROR = "illegal syntax";
        /**
         * Special value in {@link #CHAR_TO_HEX_MAP} for
         * the decimal point character.
         */
        static final byte DECIMAL_POINT_CLASS = -4;
        /**
         * Special value in {@link #CHAR_TO_HEX_MAP} for
         * characters that are neither a hex digit nor
         * a decimal point character..
         */
        static final byte OTHER_CLASS = -1;
        /**
         * Includes all non-negative values of a {@code byte}, so that we only have
         * to check for byte values {@literal <} 0 before accessing this array.
         */
        static final byte[] CHAR_TO_HEX_MAP = new byte[256];

        static {
            Arrays.fill(CHAR_TO_HEX_MAP, OTHER_CLASS);
            for (char ch = '0'; ch <= '9'; ch++) {
                CHAR_TO_HEX_MAP[ch] = (byte) (ch - '0');
            }
            for (char ch = 'A'; ch <= 'F'; ch++) {
                CHAR_TO_HEX_MAP[ch] = (byte) (ch - 'A' + 10);
            }
            for (char ch = 'a'; ch <= 'f'; ch++) {
                CHAR_TO_HEX_MAP[ch] = (byte) (ch - 'a' + 10);
            }
            CHAR_TO_HEX_MAP['.'] = DECIMAL_POINT_CLASS;
        }

        /**
         * Returns the character at the specified index if index is &lt; endIndex;
         * otherwise returns 0.
         *
         * @param str      the string
         * @param i        the index
         * @param endIndex the end index
         * @return the character or 0
         */
        protected static char charAt(CharSequence str, int i, int endIndex) {
            return i < endIndex ? str.charAt(i) : 0;
        }

        /**
         * Looks the character up in the {@link #CHAR_TO_HEX_MAP} returns
         * a value &lt; 0 if the character is not in the map.
         * <p>
         * Returns -1 if the character code is &gt; 255.
         * <p>
         * Returns -4 if the character is a decimal point.
         *
         * @param ch a character
         * @return the hex value or a value &lt; 0.
         */
        protected static int lookupHex(char ch) {
            // The branchy code is faster than the branch-less code.
            // Branch-less code: return CHAR_TO_HEX_MAP[ch & 0xff] | (127 - ch) >> 31;
            // Branch-less code: return CHAR_TO_HEX_MAP[(ch|((127-ch)>>31))&0xff];
            // Branch-less code: return CHAR_TO_HEX_MAP[ch<128?ch:0];
            return ch < 128 ? CHAR_TO_HEX_MAP[ch] : -1;
        }

        /**
         * Checks the bounds and returns the end index (exclusive) of the data in the array.
         *
         * @param size   length of array (Must be in the range from 0 to max length of
         *               a Java array. This value is not checked, because this is an internal API!)
         * @param offset start-index of data into array (Must be non-negative and smaller than size)
         * @param length length of data (Must be non-negative and smaller than size - offset)
         * @return offset + length
         */
        protected static int checkBounds(int size, int offset, int length) {
            if ((offset | length | size - length - offset) < 0) { // tricky way of testing multiple negative values at once
                throw new IllegalArgumentException(ILLEGAL_OFFSET_OR_ILLEGAL_LENGTH);
            }
            return length + offset;
        }
    }
}
