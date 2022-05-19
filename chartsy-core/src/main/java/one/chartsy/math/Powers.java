/*
 * Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.math;

/**
 * Collection of power-related math utility methods.
 */
public class Powers {

    /**
     * Finds the next power of 2 greater than or equal to the supplied value.
     *
     * @param value from which to search for next power of 2
     * @return The next power of 2 or the value itself if it is a power of 2.
     * <p>Special cases for return values are as follows:
     * <ul>
     *     <li>{@code <= 0} -> 1</li>
     *     <li>{@code >= 2^30} -> 2^30</li>
     * </ul>
     *
     * @author The Netty Project
     */
    public static int findNextPositivePowerOfTwo(int value) {
        if (value <= 0)
            return 1;
        else if (value >= 0x40000000)
            return 0x40000000;
        else
            return 1 << (32 - Integer.numberOfLeadingZeros(value - 1));
    }
}
