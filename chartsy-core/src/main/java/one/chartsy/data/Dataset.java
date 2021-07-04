package one.chartsy.data;

import one.chartsy.data.collections.PackedDataset;

import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * An ordered sequence of arbitrary-type data elements.
 *
 * @author Mariusz Bernacki
 *
 * @param <E> the type of elements stored in this dataset
 */
public interface Dataset<E> extends SequenceAlike<E, Stream<E>, Dataset<E>> {

    E get(int index);

    @Override
    default Stream<E> stream() {
        return IntStream.range(0, length()).mapToObj(this::get);
    }

    /**
     * Returns as a {@link Dataset} values of the series between the
     * {@code start} and the {@code end} index.
     * <p>
     * The first value is taken from the index {@code start} and stored at the
     * index {@code 0} in the vector result. The next value is taken from the
     * index {@code start+1} and stored at the index {@code 1} in the vector
     * result; and so forth.
     *
     * @param start
     *            the first index of the value to be taken (inclusive)
     * @param count
     *            the number of elements to be taken
     * @return the dataset of values between index {@code start} and {@code start+count-1}
     */
    @Override
    default Dataset<E> take(int start, int count) {
        if (start < 0)
            throw new IllegalArgumentException("The `start` argument cannot be negative");
        if (count <= 0)
            throw new IllegalArgumentException("The `count` argument must be positive");
        if (length() < count - start)
            throw new IllegalArgumentException("The take end index cannot exceed dataset length " + length());

        return new Dataset<>() {
            @Override
            public int length() {
                return count;
            }

            @Override
            public E get(int index) {
                return Dataset.this.get(start + Datasets.requireValidIndex(index, this));
            }
        };
    }

    default Dataset<E> toDirect() {
        return PackedDataset.from(this);
    }
}
