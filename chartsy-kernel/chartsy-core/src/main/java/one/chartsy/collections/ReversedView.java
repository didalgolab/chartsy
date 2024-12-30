/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.collections;

import java.util.*;

/**
 * Provides an read-only view over another list instance which is itself a
 * {@code List} but gives the elements of the underlying list in a reversed
 * order.
 * 
 * @author Mariusz Bernacki
 *
 * @param <E>
 *            the type of elements of an underlying list
 */
public class ReversedView<E> extends AbstractList<E> {
    
    private static class RandomAccessView<E> extends ReversedView<E> implements RandomAccess {
        RandomAccessView(List<E> backingList) {
            super(backingList);
        }
    }
    
    public static <E> List<E> of(List<E> list) {
        if (list instanceof RandomAccess)
            return new RandomAccessView<>(list);
        else
            return new ReversedView<>(list);
    }
    
    @SafeVarargs
    public static <E> List<E> of(E... e) {
        return new RandomAccessView<>(Arrays.asList(e));
    }
    
    private final List<E> backingList;
    
    private ReversedView(List<E> backingList) {
        this.backingList = backingList;
    }
    
    @Override
    public E get(int i) {
        return backingList.get(backingList.size() - i - 1);
    }
    
    @Override
    public int size() {
        return backingList.size();
    }
}