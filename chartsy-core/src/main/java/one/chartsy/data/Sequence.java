package one.chartsy.data;

import java.util.stream.IntStream;
import java.util.stream.Stream;

public interface Sequence<E> extends SequenceAlike<E, Sequence<E>> {

    E get(int index);

    @Override
    default Stream<E> stream() {
        return IntStream.range(0, length()).mapToObj(this::get);
    }
}
