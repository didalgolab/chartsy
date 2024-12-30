/* Copyright 2024 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.text;

import java.util.Iterator;

/**
 * An iterator that provides {@link MutableStringFragment} instances.
 * <p>
 * Implementations of this interface typically use the flyweight pattern for efficiency.
 * As a result, the {@link MutableStringFragment} returned by the {@link #next()} method
 * is only valid until the next call to {@link #next()}. Storing or referencing the returned
 * fragment outside of the immediate context of iteration may lead to unexpected behavior.
 * <p>
 * If you need to preserve the state of a fragment for later use, convert it to a
 * {@link String} using {@link MutableStringFragment#toString()}.
 *
 * @author Mariusz Bernacki
 *
 */
public interface MutableStringFragmentIterator extends Iterator<MutableStringFragment> {

    /**
     * Creates a {@link MutableStringFragmentIterator} that splits the given string
     * using the specified delimiter.
     *
     * @param str       the string to split
     * @param delimiter the character to use as a delimiter
     * @return a new {@link MutableStringFragmentIterator}
     */
    static MutableStringFragmentIterator forSplit(String str, char delimiter) {
        return new DelimitedMutableStringFragmentIterator(str, delimiter);
    }
}