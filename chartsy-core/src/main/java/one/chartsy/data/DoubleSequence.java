package one.chartsy.data;

import java.util.PrimitiveIterator;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;

public interface DoubleSequence extends SequenceAlike<Double, DoubleSequence> {

    double get(int index);

    @Override
    default DoubleStream stream() {
        return IntStream.range(0, length()).mapToDouble(this::get);
    }

    @Override
    default PrimitiveIterator.OfDouble iterator() {
        return stream().iterator();
    }
}
