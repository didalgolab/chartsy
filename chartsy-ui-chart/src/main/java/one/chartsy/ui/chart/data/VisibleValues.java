/* Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.ui.chart.data;

import one.chartsy.core.Range;
import one.chartsy.data.DoubleDataset;

public class VisibleValues {
    /** The number of visible quotes. */
    private final int length;
    /** The right-most visible quote. */
    private final int offset;
    /** The quotes in the range. */
    private final DoubleDataset values;
    
    
    
    public VisibleValues rescale(double factor) {
        return new VisibleValues(values.mul(factor), offset, length);
    }
    
    public VisibleValues(DoubleDataset values, int offset, int length) {
        this.values = values;
        this.offset = offset;
        this.length = length;
    }
    
    public double getValueAt(int index) {
        return getOrElse(values, offset + length - index - 1, Double.NaN);
    }

    private static double getOrElse(DoubleDataset values, int index, double defaultValue) {
        if (index < 0 || index >= values.length())
            return defaultValue;
        else
            return values.get(index);
    }

    public double getMinimum() {
        double min = Double.MAX_VALUE;
        for (int i = 0; i < length; i++) {
            double v0 = getValueAt(i);
            if (v0 == v0 && v0 < min)
                min = v0;
        }
        return min;
    }
    
    public double getMaximum() {
        double max = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < length; i++) {
            double v0 = getValueAt(i);
            if (v0 > max)
                max = v0;
        }
        return max;
    }
    
    public Range.Builder getRange(Range.Builder rv) {
        if (rv == null)
            rv = new Range.Builder();

        var startIndex = offset;
        var endIndex = Math.min(offset + length, values.length());
        for (var i = startIndex; i < endIndex; i++)
            rv.add(values.get(i));
        return rv;
    }
    
    public int getLength() {
        return length;
    }
    
}
