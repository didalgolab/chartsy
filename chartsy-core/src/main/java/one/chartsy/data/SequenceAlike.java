package one.chartsy.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.stream.BaseStream;

public interface SequenceAlike<E,
        T_STREAM extends BaseStream<E, T_STREAM>,
        T_SEQ extends SequenceAlike<E, T_STREAM, T_SEQ>> extends Iterable<E> {

    int length();

    T_STREAM stream();

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

    default List<T_SEQ> subsequences(int len) {
        if (len <= 0)
            throw new IllegalArgumentException("subsequences length `" + len + "` must be positive");

        int length = length();
        if (len > length)
            return Collections.emptyList();

        List<T_SEQ> subsequences = new ArrayList<>(1 + length - len);
        for (int start = 0, end = length - len; start <= end; start++)
            subsequences.add(take(start, len));
        return subsequences;
    }

    default List<E> toImmutableList() {
        List<E> list = new ArrayList<>(length());
        iterator().forEachRemaining(list::add);
        return Collections.unmodifiableList(list);
    }
}
