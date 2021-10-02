///////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2001, Eric D. Friedman All Rights Reserved.
// Copyright (c) 2009, Rob Eden All Rights Reserved.
// Copyright (c) 2009, Jeff Randall All Rights Reserved.
//
// This library is free software; you can redistribute it and/or
// modify it under the terms of the GNU Lesser General Public
// License as published by the Free Software Foundation; either
// version 2.1 of the License, or (at your option) any later version.
//
// This library is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU Lesser General Public
// License along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
///////////////////////////////////////////////////////////////////////////////

/* Copyright 2016 by Mariusz Bernacki. PROPRIETARY and CONFIDENTIAL content.
 * Unauthorized copying of this file, via any medium is strictly prohibited.
 * See the file "LICENSE.txt" for the full license governing this code. */
package one.chartsy.collections;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Arrays;
import java.util.Random;

/**
 * A resizable, array-backed list of double primitives.
 */
public class DoubleArray implements Externalizable {
    
    /** the data of the list */
    protected double[] data;
    
    /** the index after the last entry in the list */
    protected int size;
    
    /** the default capacity for new lists */
    protected static final int DEFAULT_CAPACITY = 100;
    
    public DoubleArray() {
        this(DEFAULT_CAPACITY);
    }
    
    public DoubleArray(int capacity) {
        data = new double[capacity];
    }
    
    public DoubleArray(double[] values) {
        this(values.length);
        add(values);
    }
    
    /**
     * Grow the internal array as needed to accommodate the specified number of
     * elements. The size of the array bytes on each resize unless capacity
     * requires more than twice the current capacity.
     */
    public void ensureCapacity(int capacity) {
        if (capacity > data.length) {
            int newCap = Math.max(data.length * 2, capacity);
            double[] tmp = new double[newCap];
            System.arraycopy(data, 0, tmp, 0, data.length);
            data = tmp;
        }
    }
    
    public int size() {
        return size;
    }
    
    public boolean isEmpty() {
        return size == 0;
    }
    
    /**
     * Sheds any excess capacity above and beyond the current size of the list.
     */
    public void trimToSize() {
        if (data.length > size()) {
            double[] tmp = new double[size()];
            toArray(tmp, 0, tmp.length);
            data = tmp;
        }
    }
    
    public boolean add(double val) {
        ensureCapacity(size + 1);
        data[size++] = val;
        return true;
    }
    
    public void add(double[] vals) {
        add(vals, 0, vals.length);
    }
    
    public void add(double[] vals, int offset, int length) {
        ensureCapacity(size + length);
        System.arraycopy(vals, offset, data, size, length);
        size += length;
    }
    
    public void insert(int offset, double value) {
        ensureCapacity(size + 1);
        // shift right
        if (offset != size)
            System.arraycopy(data, offset, data, offset + 1, size - offset);
        // insert
        data[offset] = value;
        size++;
    }
    
    public void insert(int offset, double[] values) {
        insert(offset, values, 0, values.length);
    }
    
    public void insert(int offset, double[] values, int valOffset, int len) {
        ensureCapacity(size + len);
        // shift right
        if (offset != size)
            System.arraycopy(data, offset, data, offset + len, size - offset);
        // insert
        System.arraycopy(values, valOffset, data, offset, len);
        size += len;
    }
    
    public double get(int offset) {
        if (offset >= size)
            throw new IndexOutOfBoundsException(offset);

        return data[offset];
    }
    
    public double set(int offset, double val) {
        if (offset >= size)
            throw new IndexOutOfBoundsException(offset);
        
        double prev_val = data[offset];
        data[offset] = val;
        return prev_val;
    }
    
    public double replace(int offset, double val) {
        if (offset >= size)
            throw new IndexOutOfBoundsException(offset);
        
        double old = data[offset];
        data[offset] = val;
        return old;
    }
    
    public void set(int offset, double[] values) {
        set(offset, values, 0, values.length);
    }
    
    public void set(int offset, double[] values, int valOffset, int length) {
        if (offset < 0 || offset + length > size)
            throw new IndexOutOfBoundsException(offset);
        
        System.arraycopy(values, valOffset, data, offset, length);
    }
    
    public void clear() {
        size = 0;
    }
    
    /**
     * Flushes the internal state of the list, setting the capacity of the empty
     * list to <tt>capacity</tt>.
     */
    public void clear(int capacity) {
        data = new double[capacity];
        size = 0;
    }
    
    public void reset() {
        size = 0;
    }
    
    public boolean remove(double value) {
        for (int index = 0; index < size; index++)
            if (value == data[index]) {
                remove(index, 1);
                return true;
            }
        return false;
    }
    
    public double removeAt(int offset) {
        double old = get(offset);
        remove(offset, 1);
        return old;
    }
    
    public void remove(int offset, int length) {
        if (length == 0)
            return;
        if (offset < 0 || offset >= size)
            throw new IndexOutOfBoundsException(offset);
        
        if (offset == 0) {
            // data at the front
            System.arraycopy(data, length, data, 0, size - length);
        } else if (size - length == offset) {
            // no copy to make, decrementing pos "deletes" values at
            // the end
        } else {
            // data in the middle
            System.arraycopy(data, offset + length, data, offset, size - (offset + length));
        }
        size -= length;
        // no need to clear old values beyond _pos, because this is a
        // primitive collection and 0 takes as much room as any other
        // value
    }
    
    public boolean containsAll(double[] array) {
        for (int i = array.length; i-- > 0;)
            if (!contains(array[i]))
                return false;
        return true;
    }
    
    public boolean addAll(double[] array) {
        boolean changed = false;
        for (double element : array)
            if (add(element))
                changed = true;
        return changed;
    }
    
    public boolean retainAll(double[] array) {
        boolean changed = false;
        Arrays.sort(array);
        double[] data = this.data;
        
        for (int i = size; i-- > 0;)
            if (Arrays.binarySearch(array, data[i]) < 0) {
                remove(i, 1);
                changed = true;
            }
        return changed;
    }
    
    public boolean removeAll(double[] array) {
        boolean changed = false;
        for (int i = array.length; i-- > 0;)
            if (remove(array[i]))
                changed = true;
        return changed;
    }
    
    public void reverse() {
        reverse(0, size);
    }
    
    public void reverse(int from, int to) {
        if (from > to)
            throw new IllegalArgumentException("from cannot be greater than to");
        for (int i = from, j = to - 1; i < j; i++, j--)
            swap(i, j);
    }
    
    public void shuffle(Random rand) {
        for (int i = size; i-- > 1;)
            swap(i, rand.nextInt(i));
    }
    
    /**
     * Swap the values at offsets <tt>i</tt> and <tt>j</tt>.
     * 
     * @param i
     *            an offset into the data array
     * @param j
     *            an offset into the data array
     */
    private void swap(int i, int j) {
        double tmp = data[i];
        data[i] = data[j];
        data[j] = tmp;
    }
    
    public DoubleArray subList(int begin, int end) {
        if (end < begin)
            throw new IllegalArgumentException("end index " + end + " greater than begin index " + begin);
        if (begin < 0)
            throw new IndexOutOfBoundsException("begin index can not be < 0");
        if (end > data.length)
            throw new IndexOutOfBoundsException("end index < " + data.length);
        
        DoubleArray list = new DoubleArray(end - begin);
        for (int i = begin; i < end; i++)
            list.add(data[i]);
        return list;
    }
    
    public double[] toArray() {
        return toArray(0, size);
    }
    
    public double[] toArray(int offset, int len) {
        double[] rv = new double[len];
        toArray(rv, offset, len);
        return rv;
    }
    
    public double[] toArray(double[] dest) {
        int len = dest.length;
        if (dest.length > size) {
            len = size;
        }
        toArray(dest, 0, len);
        return dest;
    }
    
    public double[] toArray(double[] dest, int offset, int len) {
        if (offset < 0 || offset >= size)
            throw new IndexOutOfBoundsException(offset);
        
        System.arraycopy(data, offset, dest, 0, len);
        return dest;
    }
    
    public double[] toArray(double[] dest, int source_pos, int dest_pos, int len) {
        if (source_pos < 0 || source_pos >= size)
            throw new IndexOutOfBoundsException(source_pos);
        
        System.arraycopy(data, source_pos, dest, dest_pos, len);
        return dest;
    }
    
    @Override
    public boolean equals(Object other) {
        if (other == this)
            return true;
        if (other instanceof DoubleArray) {
            DoubleArray that = (DoubleArray) other;
            if (that.size() == this.size()) {
                for (int i = size; i-- > 0;)
                    if (this.data[i] != that.data[i])
                        return false;
                return true;
            }
        }
        return false;
    }
    
    @Override
    public int hashCode() {
        int result = 1;
        for (int i = size; i-- > 0;) {
            long bits = Double.doubleToLongBits(data[i]);
            result = 31 * result + (int) (bits ^ (bits >>> 32));
        }
        return result;
    }
    
    public void sort() {
        Arrays.sort(data, 0, size);
    }
    
    public void sort(int fromIndex, int toIndex) {
        Arrays.sort(data, fromIndex, toIndex);
    }

    public DoubleArrayStatistics sortAndComputeStatistics() {
        return new DoubleArrayStatistics(this);
    }

    public void fill(double val) {
        Arrays.fill(data, 0, size, val);
    }
    
    public void fill(int fromIndex, int toIndex, double val) {
        if (toIndex > size) {
            ensureCapacity(toIndex);
            size = toIndex;
        }
        Arrays.fill(data, fromIndex, toIndex, val);
    }
    
    public int indexOf(double value) {
        return indexOf(0, value);
    }
    
    public int indexOf(int offset, double value) {
        for (int i = offset; i < size; i++)
            if (data[i] == value)
                return i;
        return -1;
    }
    
    public int lastIndexOf(double value) {
        return lastIndexOf(size, value);
    }
    
    public int lastIndexOf(int offset, double value) {
        for (int i = offset; i-- > 0;)
            if (data[i] == value)
                return i;
        return -1;
    }
    
    public boolean contains(double value) {
        return lastIndexOf(value) >= 0;
    }
    
    public double max() {
        if (size() == 0)
            throw new IllegalStateException("cannot find maximum of an empty list");
        
        double max = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < size; i++)
            if (data[i] > max)
                max = data[i];
        return max;
    }
    
    public double min() {
        if (size() == 0)
            throw new IllegalStateException("cannot find minimum of an empty list");
        
        double min = Double.POSITIVE_INFINITY;
        for (int i = 0; i < size; i++)
            if (data[i] < min)
                min = data[i];
        return min;
    }
    
    public double sum() {
        double sum = 0;
        for (int i = 0; i < size; i++)
            sum += data[i];
        return sum;
    }
    
    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder("{");
        for (int i = 0, end = size - 1; i < end; i++) {
            buf.append(data[i]);
            buf.append(", ");
        }
        if (size() > 0)
            buf.append(data[size - 1]);
        buf.append("}");
        return buf.toString();
    }
    
    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        out.writeByte(0);
        out.writeInt(size);

        int len = data.length;
        out.writeInt(len);
        for (int i = 0; i < len; i++)
            out.writeDouble(data[i]);
    }
    
    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        in.readByte();
        size = in.readInt();
        
        int len = in.readInt();
        data = new double[len];
        for (int i = 0; i < len; i++)
            data[i] = in.readDouble();
    }
}
