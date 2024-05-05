/*
 * Copyright 2024 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.base;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.PrimitiveIterator;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.LongConsumer;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Ring buffer class that handles an array of elements of specific type. Never gets overloaded,
 * the newest added element overwrites the oldest one.
 *
 * <p>This class is NOT thread-safe. MUST NOT be used by concurrent threads.
 *
 * @param <E> the type of elements in this collection
 */
public class RingBuffer<E>
        extends AbstractRingBuffer
        implements Consumer<E>, Iterable<E>, Dataset<E> {

    public static final int DEFAULT_CAPACITY = 64;

    protected final E[] values;

    public RingBuffer() {
        this(DEFAULT_CAPACITY);
    }

    @SuppressWarnings("unchecked")
    public RingBuffer(int capacity) {
        super(capacity);
        values = (E[]) new Object[nextPowerOfTwo(capacity)];
    }

    /**
     * Adds new item to the ring buffer.
     *
     * @param e the new item
     */
    @Override
    public void accept(E e) {
        values[arrayIndex(-1)] = e;
        nextWrite++;
    }

    public void add(E e) {
        accept(e);
    }

    public void addAll(Iterable<E> e) {
        e.forEach(this);
    }

    @Override
    public E get(int offset) {
        checkOffset(offset);
        return values[arrayIndex(offset)];
    }

    public void set(int offset, E value) {
        checkOffset(offset);
        values[arrayIndex(offset)] = value;
    }

    /**
     * Copy the elements, starting at the specified offset, into the specified
     * array.
     */
    public void copyInto(E[] array, int offset) {
        int count = length();
        if (count > 0) {
            int startIndex = arrayIndex(count - 1);
            int fenceIndex = arrayIndex(0) + 1;
            if (startIndex < fenceIndex) {
                System.arraycopy(values, startIndex, array, offset, count);
            } else {
                int cnt = values.length - startIndex;
                System.arraycopy(values, startIndex, array, offset, cnt);
                System.arraycopy(values, 0, array, offset + cnt, count - cnt);
            }
        }
    }

    /**
     * Create a new array using the specified array factory, and copy the
     * elements into it.
     */
    public E[] toArray(IntFunction<E[]> arrayFactory) {
        E[] result = arrayFactory.apply(length());
        copyInto(result, 0);
        return result;
    }

    @Override
    public void clear() {
        Arrays.fill(values, null);
        nextWrite = 0;
    }

    @Override
    public void forEach(Consumer<? super E> consumer) {
        int count = length();
        if (count > 0) {
            int startIndex = arrayIndex(count - 1);
            int fenceIndex = arrayIndex(0) + 1;
            if (startIndex < fenceIndex) {
                // Simple case: No wrap-around
                for (int i = startIndex; i < fenceIndex; i++)
                    consumer.accept(values[i]);
            } else {
                // Wrap-around case
                for (int i = startIndex; i < values.length; i++)
                    consumer.accept(values[i]);
                for (int i = 0; i < fenceIndex; i++)
                    consumer.accept(values[i]);
            }
        }
    }

    /**
     * Returns a sequential Stream with this ring buffer's elements as its source.
     *
     * @return a Stream over the elements in the buffer
     */
    @Override
    public Stream<E> stream() {
        return StreamSupport.stream(spliterator(), false);
    }

    @Override
    public Iterator<E> iterator() {
        return Spliterators.iterator(spliterator());
    }

    private static final int SPLITERATOR_CHARACTERISTICS
            = Spliterator.SIZED | Spliterator.ORDERED | Spliterator.SUBSIZED;

    @Override
    public Spliterator<E> spliterator() {
        int count = length();
        if (count == 0)
            return Spliterators.emptySpliterator();

        int startIndex = arrayIndex(count - 1);
        int fenceIndex = arrayIndex(0) + 1;
        if (startIndex < fenceIndex) {
            return new RingBufferSpliterator<>(startIndex, fenceIndex, nextWrite);
        } else {
            var left = new RingBufferSpliterator<E>(startIndex, values.length, nextWrite);
            return new RingBufferSpliterator<>(left, 0, fenceIndex, nextWrite - startIndex + values.length);
        }
    }

    @Override
    public String toString() {
        List<E> list = new ArrayList<>();
        forEach(list::add);
        return "RingBuffer:" + list;
    }

    private final class RingBufferSpliterator<T> implements Spliterator<T> {
        private final RingBufferSpliterator<T> left;
        private boolean leftExhausted;
        private int rightIndex;
        private final int rightFence;
        private long safeWriteSequence;

        public RingBufferSpliterator(RingBufferSpliterator<T> left, int rightOrigin, int rightFence, long safeWriteSequence) {
            this.left = left;
            this.leftExhausted = (left == null);
            this.rightIndex = rightOrigin;
            this.rightFence = rightFence;
            this.safeWriteSequence = safeWriteSequence;
        }

        public RingBufferSpliterator(int origin, int fence, long safeWriteSequence) {
            this(null, origin, fence, safeWriteSequence);
        }

        @Override
        public boolean tryAdvance(Consumer<? super T> action) {
            Objects.requireNonNull(action);

            if (!leftExhausted) {
                if (left.tryAdvance(action)) {
                    return true;
                } else {
                    leftExhausted = true;
                }
            }

            if (rightIndex < rightFence) {
                @SuppressWarnings("unchecked") T e = (T) values[rightIndex++];
                if (nextWrite > safeWriteSequence++)
                    throw new ConcurrentModificationException();
                action.accept(e);
                return true;
            }
            return false;
        }

        @Override
        public void forEachRemaining(Consumer<? super T> action) {
            Objects.requireNonNull(action);

            if (!leftExhausted) {
                left.forEachRemaining(action);
                leftExhausted = true;
            }

            int i = rightIndex, hi = rightFence;
            long safeWrite = safeWriteSequence;
            if (i < (rightIndex = hi)) {
                do {
                    if (nextWrite > safeWrite++)
                        throw new ConcurrentModificationException();
                    @SuppressWarnings("unchecked") T e = (T) values[i++];
                    action.accept(e);
                } while (i < hi);
            }
        }

        @Override
        public Spliterator<T> trySplit() {
            if (!leftExhausted) {
                leftExhausted = true;
                return new RingBufferSpliterator<>(left.left, left.rightIndex, left.rightFence, left.safeWriteSequence);
            } else {
                int lo = rightIndex, mid = (lo + rightFence) >>> 1;
                if (lo < mid) {
                    long safeWrite = safeWriteSequence;
                    safeWriteSequence += (mid - lo);
                    return new RingBufferSpliterator<>(lo, rightIndex = mid, safeWrite);
                }
                return null;
            }
        }

        @Override
        public long estimateSize() {
            int rightEstimate = rightFence - rightIndex;
            return leftExhausted ? rightEstimate : left.estimateSize() + rightEstimate;
        }

        @Override
        public int characteristics() {
            return SPLITERATOR_CHARACTERISTICS;
        }
    }

    /**
     * An ordered fixed-capacity collection of primitive values. Elements can be added, but not explicitly removed.
     *
     * @param <E> the wrapper type for this primitive type
     * @param <T_ARR> the array type for this primitive type
     * @param <T_CONS> the Consumer type for this primitive type
     */
    public abstract static class OfPrimitive<E, T_ARR, T_CONS>
            extends AbstractRingBuffer implements Iterable<E> {

        protected final T_ARR values;

        OfPrimitive(int capacity) {
            super(capacity);
            values = newArray(nextPowerOfTwo(capacity));
        }

        OfPrimitive() {
            super(DEFAULT_CAPACITY);
            values = newArray(nextPowerOfTwo(capacity));
        }

        @Override
        public abstract Iterator<E> iterator();

        public abstract T_ARR newArray(int size);

        protected abstract int arrayLength(T_ARR array);

        protected abstract void arrayForEach(T_ARR array, int from, int to, T_CONS consumer);

        @SuppressWarnings("SuspiciousSystemArraycopy")
        public void copyInto(T_ARR array, int offset) {
            int count = length();
            if (count > 0) {
                int startIndex = arrayIndex(count - 1);
                int fenceIndex = arrayIndex(0) + 1;
                if (startIndex < fenceIndex) {
                    System.arraycopy(values, startIndex, array, offset, count);
                } else {
                    int cnt = arrayLength(values) - startIndex;
                    System.arraycopy(values, startIndex, array, offset, cnt);
                    System.arraycopy(values, 0, array, offset + cnt, count - cnt);
                }
            }
        }

        public T_ARR toPrimitiveArray() {
            T_ARR result = newArray(length());
            copyInto(result, 0);
            return result;
        }

        @Override
        public void clear() {
            nextWrite = 0;
        }

        @SuppressWarnings("overloads")
        public void forEach(T_CONS consumer) {
            int count = length();
            if (count > 0) {
                int startIndex = arrayIndex(count - 1);
                int fenceIndex = arrayIndex(0) + 1;
                if (startIndex < fenceIndex) {
                    // Simple case: No wrap-around
                    arrayForEach(values, startIndex, fenceIndex, consumer);
                } else {
                    // Wrap-around case
                    arrayForEach(values, startIndex, arrayLength(values), consumer);
                    arrayForEach(values, 0, fenceIndex, consumer);
                }
            }
        }

        /**
         * A Spliterator designed for RingBuffer.OfPrimitive, handling primitive types efficiently.
         *
         * @param <T_SPLITR> the specific Spliterator type for the primitive type
         */
        abstract class BaseSpliterator<T_SPLITR extends Spliterator.OfPrimitive<E, T_CONS, T_SPLITR>>
                implements Spliterator.OfPrimitive<E, T_CONS, T_SPLITR> {

            private final T_SPLITR left;
            private boolean leftExhausted;
            private int rightIndex;
            private final int rightFence;
            private long safeWriteSequence;

            /**
             * Creates a BaseSpliterator for either wrap-around cases, splitting the iteration into two parts
             * or non-wrap-around cases - depending on the order of {@code origin} and {@code fence} indexes.
             *
             * @param origin            the starting index
             * @param fence             the ending index
             * @param safeWriteSequence the write sequence number for concurrent modification checks
             */
            public BaseSpliterator(int origin, int fence, long safeWriteSequence) {
                this.left = (origin < fence) ? null : newSpliterator(origin, arrayLength(values), safeWriteSequence);
                this.leftExhausted = (left == null);
                this.rightIndex = (origin < fence) ? origin : 0;
                this.rightFence = fence;
                this.safeWriteSequence = (origin < fence) ? safeWriteSequence : nextWrite - origin + arrayLength(values);
            }

            /**
             * Creates a new spliterator of the same type for a specific sub-range.
             *
             * @param origin            the starting index
             * @param fence             the ending index
             * @param safeWriteSequence the write sequence number for concurrent modification checks
             * @return A new spliterator for the specified range
             */
            abstract T_SPLITR newSpliterator(int origin, int fence, long safeWriteSequence);

            /**
             * Consumes a single element from the array using the provided consumer.
             *
             * @param array   The array containing the element
             * @param index   The index of the element to consume
             * @param consumer The consumer to accept the element
             */
            abstract void arrayForOne(T_ARR array, int index, T_CONS consumer);

            @Override
            public long estimateSize() {
                int rightEstimate = rightFence - rightIndex;
                return leftExhausted ? rightEstimate : left.estimateSize() + rightEstimate;
            }

            @Override
            public int characteristics() {
                return SPLITERATOR_CHARACTERISTICS;
            }

            @Override
            public boolean tryAdvance(T_CONS action) {
                Objects.requireNonNull(action);
                if (!leftExhausted) {
                    if (left.tryAdvance(action)) {
                        return true;
                    } else {
                        leftExhausted = true;
                    }
                }
                if (rightIndex < rightFence) {
                    arrayForOne(values, rightIndex++, action);
                    if (nextWrite > safeWriteSequence++)
                        throw new ConcurrentModificationException();
                    return true;
                }
                return false;
            }

            @Override
            public void forEachRemaining(T_CONS action) {
                Objects.requireNonNull(action);
                if (!leftExhausted) {
                    left.forEachRemaining(action);
                    leftExhausted = true;
                }
                int i = rightIndex, hi = rightFence;
                long safeWrite = safeWriteSequence;
                if (i < (rightIndex = hi)) {
                    do {
                        if (nextWrite > safeWrite++)
                            throw new ConcurrentModificationException();
                        arrayForOne(values, i++, action);
                    } while (i < hi);
                }
            }

            @Override
            public T_SPLITR trySplit() {
                if (!leftExhausted) {
                    leftExhausted = true;
                    return left;
                } else {
                    int lo = rightIndex, mid = (lo + rightFence) >>> 1;
                    if (lo < mid) {
                        long safeWrite = safeWriteSequence;
                        safeWriteSequence += (mid - lo);
                        return newSpliterator(lo, rightIndex = mid, safeWrite);
                    }
                    return null;
                }
            }
        }
    }

    /**
     * An ordered collection of {@code int} values.
     */
    @SuppressWarnings("overloads")
    public static class OfInt extends RingBuffer.OfPrimitive<Integer, int[], IntConsumer>
            implements IntConsumer, Dataset.OfInt {
        public OfInt() { }

        public OfInt(int capacity) {
            super(capacity);
        }

        @Override
        public void forEach(Consumer<? super Integer> consumer) {
            if (consumer instanceof IntConsumer) {
                forEach((IntConsumer) consumer);
            } else {
                spliterator().forEachRemaining(consumer);
            }
        }

        @Override
        public int[] newArray(int size) {
            return new int[size];
        }

        @Override
        protected int arrayLength(int[] array) {
            return array.length;
        }

        @Override
        protected void arrayForEach(int[] array, int from, int to, IntConsumer consumer) {
            for (int i = from; i < to; i++)
                consumer.accept(array[i]);
        }

        @Override
        public void accept(int value) {
            values[arrayIndex(-1)] = value;
            nextWrite++;
        }

        public void add(int value) {
            accept(value);
        }

        @Override
        public int get(int offset) {
            checkOffset(offset);
            return values[arrayIndex(offset)];
        }

        public void set(int offset, int value) {
            checkOffset(offset);
            values[arrayIndex(offset)] = value;
        }

        @Override
        public PrimitiveIterator.OfInt iterator() {
            return Spliterators.iterator(spliterator());
        }

        @Override
        public IntStream stream() {
            return StreamSupport.intStream(spliterator(), false);
        }

        @Override
        public Spliterator.OfInt spliterator() {
            class Splitr extends BaseSpliterator<Spliterator.OfInt> implements Spliterator.OfInt {
                Splitr(int origin, int fence, long safeWriteSequence) {
                    super(origin, fence, safeWriteSequence);
                }

                @Override
                Splitr newSpliterator(int origin, int fence, long safeWriteSequence) {
                    return new Splitr(origin, fence, safeWriteSequence);
                }

                @Override
                void arrayForOne(int[] array, int index, IntConsumer consumer) {
                    consumer.accept(array[index]);
                }
            }
            int count = length();
            return (count == 0)
                    ? Spliterators.emptyIntSpliterator()
                    : new Splitr(arrayIndex(count - 1), arrayIndex(0) + 1, nextWrite);
        }
    }

    /**
     * An ordered collection of {@code long} values.
     */
    @SuppressWarnings("overloads")
    public static class OfLong extends RingBuffer.OfPrimitive<Long, long[], LongConsumer>
            implements LongConsumer, Dataset.OfLong {
        public OfLong() { }

        public OfLong(int capacity) {
            super(capacity);
        }

        @Override
        public void forEach(Consumer<? super Long> consumer) {
            if (consumer instanceof LongConsumer) {
                forEach((LongConsumer) consumer);
            } else {
                spliterator().forEachRemaining(consumer);
            }
        }

        @Override
        public long[] newArray(int size) {
            return new long[size];
        }

        @Override
        protected int arrayLength(long[] array) {
            return array.length;
        }

        @Override
        protected void arrayForEach(long[] array, int from, int to, LongConsumer consumer) {
            for (int i = from; i < to; i++)
                consumer.accept(array[i]);
        }

        @Override
        public void accept(long value) {
            values[arrayIndex(-1)] = value;
            nextWrite++;
        }

        public void add(long value) {
            accept(value);
        }

        @Override
        public long get(int offset) {
            checkOffset(offset);
            return values[arrayIndex(offset)];
        }

        public void set(int offset, long value) {
            checkOffset(offset);
            values[arrayIndex(offset)] = value;
        }

        @Override
        public PrimitiveIterator.OfLong iterator() {
            return Spliterators.iterator(spliterator());
        }

        @Override
        public LongStream stream() {
            return StreamSupport.longStream(spliterator(), false);
        }

        @Override
        public Spliterator.OfLong spliterator() {
            class Splitr extends BaseSpliterator<Spliterator.OfLong> implements Spliterator.OfLong {
                Splitr(int origin, int fence, long safeWriteSequence) {
                    super(origin, fence, safeWriteSequence);
                }

                @Override
                Splitr newSpliterator(int origin, int fence, long safeWriteSequence) {
                    return new Splitr(origin, fence, safeWriteSequence);
                }

                @Override
                void arrayForOne(long[] array, int index, LongConsumer consumer) {
                    consumer.accept(array[index]);
                }
            }
            int count = length();
            return (count == 0)
                    ? Spliterators.emptyLongSpliterator()
                    : new Splitr(arrayIndex(count - 1), arrayIndex(0) + 1, nextWrite);
        }
    }

    /**
     * An ordered collection of {@code double} values.
     */
    @SuppressWarnings("overloads")
    public static class OfDouble extends RingBuffer.OfPrimitive<Double, double[], DoubleConsumer>
            implements DoubleConsumer, Dataset.OfDouble {
        public OfDouble() { }

        public OfDouble(int capacity) {
            super(capacity);
        }

        @Override
        public void forEach(Consumer<? super Double> consumer) {
            if (consumer instanceof DoubleConsumer) {
                forEach((DoubleConsumer) consumer);
            } else {
                spliterator().forEachRemaining(consumer);
            }
        }

        @Override
        public double[] newArray(int size) {
            return new double[size];
        }

        @Override
        protected int arrayLength(double[] array) {
            return array.length;
        }

        @Override
        protected void arrayForEach(double[] array, int from, int to, DoubleConsumer consumer) {
            for (int i = from; i < to; i++)
                consumer.accept(array[i]);
        }

        @Override
        public void accept(double value) {
            values[arrayIndex(-1)] = value;
            nextWrite++;
        }

        public void add(double value) {
            accept(value);
        }

        @Override
        public double get(int offset) {
            checkOffset(offset);
            return values[arrayIndex(offset)];
        }

        public void set(int offset, double value) {
            checkOffset(offset);
            values[arrayIndex(offset)] = value;
        }

        @Override
        public PrimitiveIterator.OfDouble iterator() {
            return Spliterators.iterator(spliterator());
        }

        @Override
        public DoubleStream stream() {
            return StreamSupport.doubleStream(spliterator(), false);
        }

        @Override
        public Spliterator.OfDouble spliterator() {
            class Splitr extends BaseSpliterator<Spliterator.OfDouble> implements Spliterator.OfDouble {
                Splitr(int origin, int fence, long safeWriteSequence) {
                    super(origin, fence, safeWriteSequence);
                }

                @Override
                Splitr newSpliterator(int origin, int fence, long safeWriteSequence) {
                    return new Splitr(origin, fence, safeWriteSequence);
                }

                @Override
                void arrayForOne(double[] array, int index, DoubleConsumer consumer) {
                    consumer.accept(array[index]);
                }
            }
            int count = length();
            return (count == 0)
                    ? Spliterators.emptyDoubleSpliterator()
                    : new Splitr(arrayIndex(count - 1), arrayIndex(0) + 1, nextWrite);
        }
    }
}