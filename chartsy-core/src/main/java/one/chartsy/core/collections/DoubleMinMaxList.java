/* Copyright 2021 by Mariusz Bernacki. PROPRIETARY and CONFIDENTIAL content.
 * Unauthorized copying of this file, via any medium is strictly prohibited.
 * See the file "LICENSE.txt" for the full license governing this code. */
package one.chartsy.core.collections;

import one.chartsy.data.DoubleSeries;

import java.util.AbstractList;
import java.util.Collection;
import java.util.NoSuchElementException;

public class DoubleMinMaxList extends AbstractList<DoubleSeries> {
    /** Holds the minimum and maximum of the {@code Series} added to this list. */
    private DoubleSeries min, max;


    public DoubleMinMaxList() {
    }

    public DoubleMinMaxList(Collection<DoubleSeries> c) {
        addAll(c);
    }
    
    @Override
    public void add(int index, DoubleSeries element) {
        if (min == null)
            min = max = element;
        else {
            min = min.min(element);
            max = max.max(element);
        }
    }
    
    @Override
    public DoubleSeries get(int index) {
        int count = size();
        if (index < 0)
            throw new IndexOutOfBoundsException("index=" + index + " < 0");
        if (index >= count)
            throw new IndexOutOfBoundsException("index=" + index + " >= size=" + count);
        
        return (index == 0)? min : max;
    }
    
    @Override
    public int size() {
        return (min == null)? 0 : 2;
    }
    
    /**
     * Returns the minimum series from the current list.
     * 
     * @return the minimum series
     */
    public DoubleSeries getMinimum() {
        if (isEmpty())
            throw new NoSuchElementException("DoubleMinMaxList.minimum not available");

        return min;
    }
    
    /**
     * Returns the maximum series from the current list.
     * 
     * @return the maximum series
     */
    public DoubleSeries getMaximum() {
        if (isEmpty())
            throw new NoSuchElementException("DoubleMinMaxList.maximum not available");

        return max;
    }
}