/* Copyright 2016 by Mariusz Bernacki. PROPRIETARY and CONFIDENTIAL content.
 * Unauthorized copying of this file, via any medium is strictly prohibited.
 * See the file "LICENSE.txt" for the full license governing this code. */
package one.chartsy.ui.chart.data;

import java.time.LocalDateTime;
import java.util.OptionalInt;

import one.chartsy.*;
import one.chartsy.commons.Range;
import one.chartsy.data.CandleSeries;
import one.chartsy.data.DoubleDataset;
import one.chartsy.time.Chronological;

public class VisibleCandles {
    /** The number of visible quotes. */
    private final int length;
    /** The right-most visible quote. */
    private final int offset;
    /** The quotes in the range. */
    private final CandleSeries series;
    
    
    public VisibleCandles(CandleSeries series, int offset, int length) {
        this.series = series;
        this.offset = offset;
        this.length = length;
    }
    
    public OptionalInt getBarNumber(int index) {
        int barNo = offset + length - index - 1;
        return isUndefined(series, barNo)? OptionalInt.empty() : OptionalInt.of(barNo);
    }

    private static boolean isUndefined(CandleSeries series, int index) {
        return (index < 0 || index >= series.length());
    }

    public Candle getQuoteAt(int index) {
        int barNo = offset + length - index - 1;
        return isUndefined(series, barNo)? null : series.get(barNo);
    }
    
    public Candle getQuoteAt2(int index) {
        int barNo = offset + length - index - 1;
        barNo = Math.max(barNo, 0);
        barNo = Math.min(barNo, series.length());
        return series.get(barNo);
    }
    
    public Candle getLastQuote() {
        int barNo = offset;
        return isUndefined(series, barNo)? null : series.get(barNo);
    }
    
    public int getOffset() {
        return offset;
    }
    
    public Range.Builder getRange(Range.Builder result) {
        if (result == null)
            result = new Range.Builder();
        
        for (int i = 0; i < length; i++) {
            Candle q0 = getQuoteAt(i);
            result.add(q0.low(), q0.high());
        }
        return result;
    }
    
    public double getMinimum() {
        double min = Double.MAX_VALUE;
        for (int i = 0; i < length; i++) {
            Candle q0 = getQuoteAt(i);
            if (q0 != null && q0.low() < min)
                min = q0.low();
        }
        return (min < Double.MAX_VALUE)? min : 0.0;
    }
    
    public double getMaximum() {
        double max = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < length; i++) {
            Candle q0 = getQuoteAt(i);
            if (q0 != null && q0.high() > max)
                max = q0.high();
        }
        return (max > Double.NEGATIVE_INFINITY)? max : 0.0;
    }
    
    public int getLength() {
        return length;
    }
    
    public VisibleValues getCloses() {
        return getVisibleDataset(series.closes().values());
    }
    
    public VisibleValues getVolumes() {
        return getVisibleDataset(series.volumes().values());
    }
    
    public VisibleValues getVisibleDataset(DoubleDataset values) {
        return new VisibleValues(values, offset, length);
    }
    
    public VisibleCandles getVisibleDataset(CandleSeries series) {
        if (series == this.series)
            return this;
        
        return new VisibleCandles(series, offset, length);
    }
    
    /**
     * Searches the visible data quotes for the quote with the specified date
     * using the binary search algorithm.
     * 
     * @param datetime
     *            the datetime value to be searched for
     * @return index of the search key, see #b.
     */
    public int binarySearch(LocalDateTime datetime) {
        return binarySearch(Chronological.toEpochMicros(datetime));
    }
    
    /**
     * Searches the visible data quotes for the quote with the specified date
     * using the binary search algorithm.
     * 
     * @param time
     *            the datetime value to be searched for
     * @return index of the search key, if it is contained in the array;
     *         otherwise, <tt>(-(<i>insertion point</i>) - 1)</tt>. The
     *         <i>insertion point</i> is defined as the point at which the key
     *         would be inserted into the array: the index of the first element
     *         greater than the key, or <tt>a.length</tt> if all elements in the
     *         array are less than the specified key. Note that this guarantees
     *         that the return value will be &gt;= 0 if and only if the key is
     *         found.
     */
    public int binarySearch(long time) {
        int low = (getQuoteAt(-1) != null)? -1: 0;
        int high = getLength() - 1;
        
        while (low <= high) {
            int mid = (low + high) >> 1;
        Candle midVal = getQuoteAt(mid);
        
        if (midVal.getTime() < time)
            low = mid + 1;
        else if (midVal.getTime() > time)
            high = mid - 1;
        else
            return mid; // key found
        }
        return low;  // key not found.
    }
}
