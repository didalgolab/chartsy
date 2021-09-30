/* Copyright 2016 by Mariusz Bernacki. PROPRIETARY and CONFIDENTIAL content.
 * Unauthorized copying of this file, via any medium is strictly prohibited.
 * See the file "LICENSE.txt" for the full license governing this code. */
package one.chartsy.ui.chart.data;

import one.chartsy.commons.Range;
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
        return values.get(offset + length - index - 1, Double.NaN);
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
            if (v0 == v0 && v0 > max)
                max = v0;
        }
        return max;
    }
    
    public Range.Builder getRange(Range.Builder rv) {
        int startIndex = offset;
        int endIndex = offset + length;
        return values.getRange(startIndex, endIndex, rv);
    }
    
    public int getLength() {
        return length;
    }
    
}
