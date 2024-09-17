package one.chartsy.data.structures;

import java.util.NoSuchElementException;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;

/**
 * A data structure that efficiently computes the moving median of a sliding window of values.
 * This class provides specialized implementations for different data types.
 *
 * @param <T> the type of elements maintained by this median calculator
 */
public class MovingMedian<T extends Comparable<? super T>> extends AbstractMovingMedian implements Consumer<T> {
    /** The circular queue of values. */
    protected final Object[] data;
    /** Function to calculate the mean of two elements */
    private final BiFunction<? super T, ? super T, ? extends T> meanFunction;

    /**
     * Constructs a new {@code MovingMedian} to calculate the running median over a window of
     * {@code windowSize} elements.
     *
     * @param windowSize   the size of the moving median window
     * @param meanFunction the function to calculate the mean of two elements (required if windowSize is even)
     * @throws IllegalArgumentException if {@code windowSize} is less than 1
     */
    public MovingMedian(int windowSize, BiFunction<? super T, ? super T, ? extends T> meanFunction) {
        super(windowSize);
        this.data = new Object[windowSize];
        if (windowSize % 2 == 0 && meanFunction == null) {
            throw new IllegalArgumentException("Mean function must be provided for even window sizes");
        }
        this.meanFunction = meanFunction;
    }

    /**
     * Constructs a new {@code MovingMedian} to calculate the running median over a window of
     * {@code windowSize} elements, using the natural order comparator and no mean function.
     *
     * @param windowSize the size of the moving median window
     * @throws IllegalArgumentException if {@code windowSize} is less than 1, or if {@code windowSize} is even and {@code meanFunction} is null
     */
    public MovingMedian(int windowSize) {
        this(windowSize, null);
    }

    @SuppressWarnings("unchecked")
    @Override
    protected int compareAt(int i, int j) {
        T a = (T) data[heap[middle + i]];
        T b = (T) data[heap[middle + j]];
        return a.compareTo(b);
    }

    @Override
    public void accept(T v) {
        boolean isFull = isFull();
        int p = pos[index];
        @SuppressWarnings("unchecked")
        T old = (T) data[index];
        data[index] = v;
        index = (index + 1) % size;
        if (!isFull) {
            count++;
        }
        acceptValue(p, isFull, (old == null ? -1 : old.compareTo(v)));
    }

    /**
     * Returns the current median value of the window.
     *
     * @return the current median value
     */
    @SuppressWarnings("unchecked")
    public T getMedian() {
        if (isEmpty())
            throw new NoSuchElementException("Median window is empty");

        T value = (T) data[heap[middle]];
        if ((count & 1) == 0) {
            T other = (T) data[heap[middle - 1]];
            return meanFunction.apply(other, value);
        }
        return value;
    }

    /**
     * Specialized class for handling primitive doubles.
     */
    public static class OfDouble extends AbstractMovingMedian implements DoubleConsumer {
        /** The circular queue of values. */
        private final double[] data;

        public OfDouble(int windowSize) {
            super(windowSize);
            data = new double[windowSize];
        }

        @Override
        protected int compareAt(int i, int j) {
            return Double.compare(data[heap[middle + i]], data[heap[middle + j]]);
        }

        @Override
        public void accept(double v) {
            boolean isFull = isFull();
            int p = pos[index];
            double old = data[index];
            data[index] = v;
            index = (index + 1) % size;
            if (!isFull) {
                count++;
            }
            acceptValue(p, isFull, Double.compare(old, v));
        }

        public double getMedian() {
            if (isEmpty())
                throw new NoSuchElementException("Median window is empty");

            double value = data[heap[middle]];
            if ((count & 1) == 0) {
                value = (value + data[heap[middle - 1]]) / 2.0;
            }
            return value;
        }
    }
}
