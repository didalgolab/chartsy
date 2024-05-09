/*
 * Copyright 2024 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.base;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.BaseStream;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;

/**
 * Base interface for datasets, which are sequences of elements supporting
 * sequential and parallel aggregate, and random-access indexed operations.
 *
 * @author Mariusz Bernacki
 */
public interface SequenceAlike<E, T_SEQ extends SequenceAlike<E, T_SEQ>> extends Iterable<E> {

	int length();

	boolean isEmpty();

	default boolean isUndefined(int index) {
		return index < 0 || index >= length();
	}

	BaseStream<E, ?> stream();

	Order getOrder();

	default List<E> toImmutableList() {
		List<E> list = new ArrayList<>(length());
		forEach(list::add);
		return Collections.unmodifiableList(list);
	}

	enum Order {
		/** Specifies that encounter order is guaranteed to be ascending index order. */
		INDEX_ASC,
		/** Specifies that encounter order is guaranteed to be descending index order. */
		INDEX_DESC,
		/** Indicates that encounter order is unspecified or not known. */
		UNSPECIFIED;

		public IntStream indexes(SequenceAlike<?, ?> seq) {
			int length = seq.length();
			IntStream stream = IntStream.range(0, length);
			return (this == INDEX_DESC) ? stream.map(i -> length - i - 1) : stream;
		}

		public boolean isDescending() {
			return this == INDEX_DESC;
		}

		public static void reverse(Object[] arr) {
			int start = 0;
			int end = arr.length - 1;

			while (start < end) {
				Object temp = arr[start];
				arr[start++] = arr[end];
				arr[end--] = temp;
			}
		}

		public static void reverse(double[] arr) {
			int start = 0;
			int end = arr.length - 1;

			while (start < end) {
				double temp = arr[start];
				arr[start++] = arr[end];
				arr[end--] = temp;
			}
		}

		public static void reverse(int[] arr) {
			int start = 0;
			int end = arr.length - 1;

			while (start < end) {
				int temp = arr[start];
				arr[start++] = arr[end];
				arr[end--] = temp;
			}
		}

		public static void reverse(long[] arr) {
			int start = 0;
			int end = arr.length - 1;

			while (start < end) {
				long temp = arr[start];
				arr[start++] = arr[end];
				arr[end--] = temp;
			}
		}

		public <E> Stream<E> drop(int n, Stream<E> s, SequenceAlike<E, ?> seq) {
			return switch (this) {
				case INDEX_ASC -> s.skip(n);
				case INDEX_DESC -> s.limit(Math.max(0, seq.length() - n));
				default -> throw new UnsupportedOperationException("Operation `drop` not implemented for order " + this);
			};
		}

		public DoubleStream drop(int n, DoubleStream s, SequenceAlike<Double, ?> seq) {
			return switch (this) {
				case INDEX_ASC -> s.skip(n);
				case INDEX_DESC -> s.limit(Math.max(0, seq.length() - n));
				default -> throw new UnsupportedOperationException("Operation `drop` not implemented for order " + this);
			};
		}

		public IntStream drop(int n, IntStream s, SequenceAlike<Integer, ?> seq) {
			return switch (this) {
				case INDEX_ASC -> s.skip(n);
				case INDEX_DESC -> s.limit(Math.max(0, seq.length() - n));
				default -> throw new UnsupportedOperationException("Operation `drop` not implemented for order " + this);
			};
		}

		public LongStream drop(int n, LongStream s, SequenceAlike<Long, ?> seq) {
			return switch (this) {
				case INDEX_ASC -> s.skip(n);
				case INDEX_DESC -> s.limit(Math.max(0, seq.length() - n));
				default -> throw new UnsupportedOperationException("Operation `drop` not implemented for order " + this);
			};
		}

		public <E> Stream<E> take(int maxCount, Stream<E> s, SequenceAlike<E, ?> seq) {
			return switch (this) {
				case INDEX_ASC -> s.limit(maxCount);
				case INDEX_DESC -> s.skip(Math.max(0, seq.length() - maxCount));
				default -> throw new UnsupportedOperationException("Operation `take` not implemented for order " + this);
			};
		}

		public DoubleStream take(int maxCount, DoubleStream s, SequenceAlike<Double, ?> seq) {
			return switch (this) {
				case INDEX_ASC -> s.limit(maxCount);
				case INDEX_DESC -> s.skip(Math.max(0, seq.length() - maxCount));
				default -> throw new UnsupportedOperationException("Operation `take` not implemented for order " + this);
			};
		}

		public IntStream take(int maxCount, IntStream s, SequenceAlike<Integer, ?> seq) {
			return switch (this) {
				case INDEX_ASC -> s.limit(maxCount);
				case INDEX_DESC -> s.skip(Math.max(0, seq.length() - maxCount));
				default -> throw new UnsupportedOperationException("Operation `take` not implemented for order " + this);
			};
		}

		public LongStream take(int maxCount, LongStream s, SequenceAlike<Long, ?> seq) {
			return switch (this) {
				case INDEX_ASC -> s.limit(maxCount);
				case INDEX_DESC -> s.skip(Math.max(0, seq.length() - maxCount));
				default -> throw new UnsupportedOperationException("Operation `take` not implemented for order " + this);
			};
		}

		public <E> Stream<E> dropTake(int fromIndex, int maxCount, Stream<E> s, SequenceAlike<E, ?> seq) {
			return switch (this) {
				case INDEX_ASC -> s.skip(fromIndex).limit(maxCount);
				case INDEX_DESC -> {
					int length = seq.length();
					yield s.skip(Math.max(0, length - maxCount - fromIndex)).limit(Math.max(0, length - fromIndex));
				}
				default -> throw new UnsupportedOperationException("Operation `dropTake` not implemented for order " + this);
			};
		}

		public DoubleStream dropTake(int fromIndex, int maxCount, DoubleStream s, SequenceAlike<Double, ?> seq) {
			return switch (this) {
				case INDEX_ASC -> s.skip(fromIndex).limit(maxCount);
				case INDEX_DESC -> {
					int length = seq.length();
					yield s.skip(Math.max(0, length - maxCount - fromIndex)).limit(Math.max(0, length - fromIndex));
				}
				default -> throw new UnsupportedOperationException("Operation `dropTake` not implemented for order " + this);
			};
		}

		public IntStream dropTake(int fromIndex, int maxCount, IntStream s, SequenceAlike<Integer, ?> seq) {
			return switch (this) {
				case INDEX_ASC -> s.skip(fromIndex).limit(maxCount);
				case INDEX_DESC -> {
					int length = seq.length();
					yield s.skip(Math.max(0, length - maxCount - fromIndex)).limit(Math.max(0, length - fromIndex));
				}
				default -> throw new UnsupportedOperationException("Operation `dropTake` not implemented for order " + this);
			};
		}

		public LongStream dropTake(int fromIndex, int maxCount, LongStream s, SequenceAlike<Long, ?> seq) {
			return switch (this) {
				case INDEX_ASC -> s.skip(fromIndex).limit(maxCount);
				case INDEX_DESC -> {
					int length = seq.length();
					yield s.skip(Math.max(0, length - maxCount - fromIndex)).limit(Math.max(0, length - fromIndex));
				}
				default -> throw new UnsupportedOperationException("Operation `dropTake` not implemented for order " + this);
			};
		}
	}
}
