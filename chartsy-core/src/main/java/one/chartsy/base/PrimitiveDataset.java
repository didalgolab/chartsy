package one.chartsy.base;

import java.util.Spliterator;

public interface PrimitiveDataset<E,
		T_SEQ extends PrimitiveDataset<E, T_SEQ, T_SPLITR>,
		T_SPLITR extends Spliterator.OfPrimitive<E, ?, T_SPLITR>>
		extends SequenceAlike<E, T_SEQ> {

	/**
	 * Returns a primitive spliterator over the elements in the window.
	 *
	 * @return a primitive spliterator
	 */
	@Override
	T_SPLITR spliterator();

}
