/*
 * Copyright 2024 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.base.dataset;

import one.chartsy.base.IntDataset;
import one.chartsy.base.PrimitiveDataset;

import java.util.Iterator;
import java.util.PrimitiveIterator;
import java.util.Spliterator;

public abstract class AbstractPrimitiveDataset<E,
		T_SEQ extends PrimitiveDataset<E, T_SEQ, T_SPLITR>,
		T_SPLITR extends Spliterator.OfPrimitive<E, ?, T_SPLITR>>
		implements PrimitiveDataset<E, T_SEQ, T_SPLITR> {

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

	public abstract static class OfInt extends AbstractPrimitiveDataset<Integer, IntDataset, Spliterator.OfInt> implements IntDataset {

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
