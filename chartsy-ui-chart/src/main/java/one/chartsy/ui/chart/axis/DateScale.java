/* Copyright 2016 by Mariusz Bernacki. PROPRIETARY and CONFIDENTIAL content.
 * Unauthorized copying of this file, via any medium is strictly prohibited.
 * See the file "LICENSE.txt" for the full license governing this code. */
package one.chartsy.ui.chart.axis;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import one.chartsy.time.Chronological;

/**
 * Base class for all date scales.
 * 
 * @author Mariusz Bernacki
 *
 */
public abstract class DateScale implements AxisScale {
    /** An array of Date objects - scale labels. */
    protected LocalDateTime[] dates;
    /** The date format used to format labels of the scale. */
    protected DateTimeFormatter labelFormat;
    /** The sub-scale of the scale. */
    protected AxisScale subScale;
    
    private final ZoneId timeZone = Chronological.TIME_ZONE;
    
    
    @Override
    public double mapMark(int i) {
        return Chronological.toEpochMicros(dates[i]);
    }
    
    @Override
    public int getMarkCount() {
        return dates.length;
    }
    
    @Override
    public String getLabelAt(int i) {
        return getLabelFor(dates[i]);
    }
    
    @Override
    public String getLabelAt(int i, Locale locale) {
        return getLabelAt(i);
    }
    
    @Override
    public AxisScale getSubScale() {
        return subScale;
    }
    
    protected String getLabelFor(LocalDateTime date) {
        return labelFormat.format(ZonedDateTime.ofInstant(date, ZoneOffset.UTC, timeZone));
    }
    
    public DateTimeFormatter getLabelFormat() {
        return labelFormat;
    }
    
    public void setLabelFormat(DateTimeFormatter labelFormat) {
        this.labelFormat = labelFormat;
    }
}
