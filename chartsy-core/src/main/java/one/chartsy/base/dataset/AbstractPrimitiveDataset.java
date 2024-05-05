/*
 * Copyright 2024 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.base.dataset;

import one.chartsy.base.Dataset;

import java.util.Iterator;
import java.util.PrimitiveIterator;
import java.util.Spliterator;

public abstract class AbstractPrimitiveDataset<E,
		T_SEQ extends Dataset.OfPrimitive<E, T_SEQ, T_SPLITR>,
		T_SPLITR extends Spliterator.OfPrimitive<E, ?, T_SPLITR>>
		implements Dataset.OfPrimitive<E, T_SEQ, T_SPLITR> {

	@Override
	public boolean isEmpty() {
		return length() == 0;
	}

	@Override
	public Iterator<E> iterator() {
		return stream().iterator();
	}

//	@Override
//	public Spliterator<E> spliterator() {
//		return stream().spliterator();
//	}

	public abstract static class OfInt extends AbstractPrimitiveDataset<Integer, Dataset.OfInt, Spliterator.OfInt> implements Dataset.OfInt {

		@Override
		public PrimitiveIterator.OfInt iterator() {
			return stream().iterator();
		}

		@Override
		public Spliterator.OfInt spliterator() {
			return stream().spliterator();
		}
	}
}
