package one.chartsy.data;

import java.util.PrimitiveIterator;
import java.util.stream.IntStream;

public interface IntSequence extends SequenceAlike<Integer, IntStream, IntSequence> {

    int get(int index);

    @Override
    default IntStream stream() {
        return IntStream.range(0, length()).map(this::get);
    }

    @Override
    default PrimitiveIterator.OfInt iterator() {
        return stream().iterator();
    }
}
