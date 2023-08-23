/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.stream.BaseStream;

public interface SequenceAlike<E,
        T_SEQ extends SequenceAlike<E, T_SEQ>> extends Iterable<E> {

    int length();

    BaseStream<E,?> stream();

    T_SEQ take(int start, int count);

    default boolean isEmpty() {
        return length() == 0;
    }

    default boolean isUndefined(int index) {
        return index < 0 || index >= length();
    }

    default Iterator<E> iterator() {
        return stream().iterator();
    }

    default Dataset<T_SEQ> subsequences(int len) {
        if (len <= 0)
            throw new IllegalArgumentException("subsequences length `" + len + "` must be positive");

        return new AbstractDataset<>() {
            @Override
            public T_SEQ get(int index) {
                return SequenceAlike.this.take(index, len);
            }

            @Override
            public int length() {
                return Math.max(0, SequenceAlike.this.length() - len + 1);
            }
        };
    }

    default List<E> toImmutableList() {
        List<E> list = new ArrayList<>(length());
        iterator().forEachRemaining(list::add);
        return Collections.unmodifiableList(list);
    }
}
