/* Copyright 2024 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.text;

public class FromString {

    private static final double[] POWERS_OF_TEN = {
            1.0e0,  1.0e1,  1.0e2,  1.0e3,  1.0e4,  1.0e5,  1.0e6,  1.0e7,
            1.0e8,  1.0e9,  1.0e10, 1.0e11, 1.0e12, 1.0e13, 1.0e14, 1.0e15
    };

    public static double toDouble(CharSequence str) {
        return (str.length() <= POWERS_OF_TEN.length) ? toDoubleShortForm(str) : toDoubleFullForm(str);
    }

    private static double toDoubleShortForm(CharSequence str) {
        long significand = 0;
        int scale = 0;
        boolean negative = false;

        int i = 0;
        char c = str.charAt(0);
        if (c < '0') {
            if (c == '+' || (negative = c == '-')) {
                i++;
            }
        }

        for (int len = str.length(); i < len; i++) {
            c = str.charAt(i);
            if (c >= '0' && c <= '9') {
                significand = significand * 10 + (c - '0');
            } else if (c == '.' && scale == 0) {
                scale = len - 1 - i;
            } else {
                return toDoubleFullForm(str);
            }
        }

        double result = significand / POWERS_OF_TEN[scale];
        return negative ? -result : result;
    }

    private static double toDoubleFullForm(CharSequence str) {
        return JavaDoubleParser.parseDouble(str);
    }

    public static int toInt(CharSequence str) {
        return Integer.parseInt(str, 0, str.length(), 10);
    }
}
