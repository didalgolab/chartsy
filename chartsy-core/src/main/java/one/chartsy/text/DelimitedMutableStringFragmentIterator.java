/* Copyright 2024 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.text;

import java.util.NoSuchElementException;

/**
 * A {@link MutableStringFragmentIterator} that splits a string based on a delimiter.
 * This class uses the flyweight pattern, returning itself as the next element in the iteration.
 *
 * @author Mariusz Bernacki
 *
 */
final class DelimitedMutableStringFragmentIterator extends MutableStringFragment implements MutableStringFragmentIterator {
    private final char delimiter;

    DelimitedMutableStringFragmentIterator(String line, char delimiter) {
        super(line, 0, -1);
        this.delimiter = delimiter;
    }

    private void findNext() {
        start = end + 1;
        end = line.indexOf(delimiter, start);
        if (end == -1) {
            end = line.length();
        }
    }

    @Override
    public boolean hasNext() {
        return end < line.length();
    }

    @Override
    public MutableStringFragment next() {
        if (!hasNext()) {
            throw new NoSuchElementException();
        }
        findNext();
        return this;
    }
}