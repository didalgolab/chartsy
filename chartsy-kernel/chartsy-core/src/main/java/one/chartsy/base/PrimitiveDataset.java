package one.chartsy.base;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Spliterator;

public interface PrimitiveDataset<E,
		T_SEQ extends PrimitiveDataset<E, T_SEQ, T_SPLITR>,
		T_SPLITR extends Spliterator.OfPrimitive<E, ?, T_SPLITR>>
		extends SequenceAlike, Iterable<E> {

	/**
	 * Returns a primitive spliterator over the elements in the window.
	 *
	 * @return a primitive spliterator
	 */
	T_SPLITR spliterator();

	default List<E> toImmutableList() {
		List<E> list = new ArrayList<>(length());
		forEach(list::add);
		return Collections.unmodifiableList(list);
	}
}
