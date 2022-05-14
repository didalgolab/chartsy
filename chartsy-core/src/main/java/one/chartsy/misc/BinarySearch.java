/* Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.misc;

import java.util.function.IntToLongFunction;

public class BinarySearch {

    public static int binarySearch(IntToLongFunction arrayFunction, int fromIndex, int toIndex, long key) {
        rangeCheck(arrayFunction, fromIndex, toIndex);
        int low = fromIndex;
        int high = toIndex - 1;

        while (low <= high) {
            int mid = (low + high) >>> 1;
            long midVal = arrayFunction.applyAsLong(mid);

            if (midVal < key)
                low = mid + 1;
            else if (midVal > key)
                high = mid - 1;
            else
                return mid; // key found
        }
        return -(low + 1);  // key not found.
    }

    public static int binarySearchReversed(IntToLongFunction arrayFunction, int fromIndex, int toIndex, long key) {
        rangeCheck(arrayFunction, fromIndex, toIndex);
        int low = fromIndex;
        int high = toIndex - 1;

        while (low <= high) {
            int mid = (low + high) >>> 1;
            long midVal = arrayFunction.applyAsLong(mid);

            if (midVal > key)
                low = mid + 1;
            else if (midVal < key)
                high = mid - 1;
            else
                return mid; // key found
        }
        return -(low + 1);  // key not found.
    }

    /**
     * Checks that {@code fromIndex} and {@code toIndex} are in
     * the range and throws an exception if they aren't.
     */
    static void rangeCheck(IntToLongFunction arrayFunction, int fromIndex, int toIndex) {
        if (fromIndex > toIndex) {
            throw new IllegalArgumentException(
                    "fromIndex(" + fromIndex + ") > toIndex(" + toIndex + ")");
        }
        if (fromIndex < 0) {
            throw new ArrayIndexOutOfBoundsException(fromIndex);
        }
        if (fromIndex < toIndex) {
            arrayFunction.applyAsLong(toIndex - 1); // may throw IndexOutOfBoundsException
        }
    }
}
