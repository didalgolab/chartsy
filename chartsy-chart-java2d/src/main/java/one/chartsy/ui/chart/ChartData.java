/* Copyright 2016 by Mariusz Bernacki. PROPRIETARY and CONFIDENTIAL content.
 * Unauthorized copying of this file, via any medium is strictly prohibited.
 * See the file "LICENSE.txt" for the full license governing this code. */
package one.chartsy.ui.chart;

import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.io.Serializable;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import one.chartsy.*;
import one.chartsy.commons.Range;
import one.chartsy.data.CandleSeries;
import one.chartsy.data.DataProviderEx;
import one.chartsy.data.VisibleQuotes;
import one.chartsy.data.provider.DataProvider;
import one.chartsy.time.Chronological;
import one.chartsy.ui.chart.axis.AxisScale;
import one.chartsy.ui.chart.axis.DateScale;
import one.chartsy.collections.DoubleArray;
import one.chartsy.graphic.DataRenderingHint;
import one.chartsy.ui.chart.data.VisibleCandles;
import one.chartsy.util.DataInterval;
import one.chartsy.util.Range;
import one.chartsy.util.RectangleInsets;

/**
 * Holds data associated with the displayed chart.
 * 
 * @author Mariusz Bernacki
 */
public class ChartData implements Serializable, ChartFrameListener {
    
    
    
    public static final int MIN_ITEMS = 40;
    public static final int MAX_ITEMS = 1000;
    public static final Insets dataOffset = new Insets(2, 20, 40, 55);
    
    private SymbolIdentity symbol;
    private TimeFrame timeFrame;
    private ChartCallbackRegistry chartCallbacks;
    private DataProvider dataProvider;
    private String dataProviderName;
    /** The instrument's dataset provided by the corresponding data provider. */
    private CandleSeries dataset;
    /** The chart dataset to display. */
    private CandleSeries chartDataset;
    /** The associated chart plot. */
    private Chart chart;
    private VisibleCandles visible;
    private Range visibleRange;
    private List<Indicator> savedIndicators;
    private List<Overlay> savedOverlays;
    private List<Integer> annotationsCount;
    private List<Annotation> annotations;
    private int period = -1;
    private int last = -1;
    private DataRenderingHint dataRenderingHint;
    
    public ChartData() {
    }
    
    public SymbolIdentity getSymbol() {
        return symbol;
    }
    
    public void setSymbol(SymbolIdentity symbol) {
        this.symbol = symbol;
    }
    
    public boolean isSymbolNull() {
        return symbol == null;
    }
    
    public TimeFrame getTimeFrame() {
        return timeFrame;
    }
    
    public void setTimeFrame(TimeFrame interval) {
        this.timeFrame = interval;
    }
    
    public void updateDataset() {
        // fireDatasetEvent(new DatasetEvent(this));
    }
    
    /**
     * Returns the chart plot associated with this instance.
     * 
     * @return the chart plot, might be {@code null}
     */
    public Chart getChart() {
        return chart;
    }
    
    /**
     * Associates the new chart plot with this instance.
     * 
     * @param chart
     *            the new chart to set
     */
    public void setChart(Chart chart) {
        this.chart = chart;
        
        // recalculate the chart dataset
        chartDataset = computeChartDataset().orElse(null);
    }
    
    /**
     * Computes the chart dataset to display. Gives the computed result if and only
     * if both the {@link #getDataset() dataset} and the {@link #getChart() chart}
     * are set. Otherwise returns {@code null}.
     * 
     * @return the computed chart dataset, might be an empty Optional
     */
    protected Optional<CandleSeries> computeChartDataset() {
        if (chart != null && dataset != null)
            return Optional.of(chart.transformDataset(dataset));
        else
            return Optional.empty();
    }
    
    public DataProvider getDataProvider() {
        //if (dataProvider == null)
        //    return DataProviderManager.getDefault().getDataProvider(dataProviderName);
        return dataProvider;
    }
    
    public void setDataProviderName(String dataProviderName) {
        this.dataProviderName = dataProviderName;
    }
    
    public String getDataProviderName() {
        return dataProviderName;
    }
    
    public boolean isDataProviderNull() {
        return dataProviderName == null;
    }
    
    /**
     * Returns the instrument's dataset. The method gives the dataset provided by
     * the corresponding data provider.
     * 
     * @return the dataset, may be {@code null}
     * @see #getChartDataset()
     */
    public CandleSeries getDataset() {
        return dataset;
    }
    
    /**
     * Checks if the dataset data is available for this instance.
     * 
     * @return {@code true} if dataset data is available, or {@code false} otherwise
     */
    public boolean hasDataset() {
        return getDataset() != null;
    }
    
    /**
     * Gives the length of the dataset. The method returns {@code 0} if dataset data
     * is not available for this instance.
     * 
     * @return the non-negative dataset length, or {@code null} if dataset data is
     *         not available
     */
    public int getDatasetLength() {
        return hasDataset()? dataset.length() : 0;
    }
    
    /**
     * Sets new dataset data for this instance.
     * 
     * @param quotes the new dataset to set
     */
    public void setDataset(CandleSeries quotes) {
        SymbolIdentity symbol = quotes.getResource().getSymbol();
        TimeFrame timeFrame = quotes.getResource().getTimeFrame();
        boolean symbolChanged = !symbol.equals(getSymbol());
        boolean timeFrameChanged = !timeFrame.equals(getTimeFrame());
        
        if (symbolChanged)
            this.symbol = symbol;
        if (timeFrameChanged)
            this.timeFrame = timeFrame;
        this.dataset = quotes;
        
        // recalculate the chart dataset
        chartDataset = computeChartDataset().orElse(null);
    }
    
    public VisibleCandles getVisible() {
        return visible;
    }
    
    private void setVisible(VisibleCandles d) {
        visible = d;
    }
    
    public boolean isVisibleNull() {
        return visible == null;
    }
    
    public int getPeriod() {
        return period;
    }
    
    public void setPeriod(int period) {
        this.period = period;
    }
    
    public int getLast() {
        return last;
    }
    
    public void setLast(int last) {
        this.last = last;
    }
    
    public void setSavedIndicators(List<Indicator> list) {
        savedIndicators = list;
    }
    
    public List<Indicator> getSavedIndicators() {
        return savedIndicators;
    }
    
    public void clearSavedIndicators() {
        if (savedIndicators != null)
            savedIndicators.clear();
        savedIndicators = null;
    }
    
    public void setSavedOverlays(List<Overlay> list) {
        savedOverlays = list;
    }
    
    public List<Overlay> getSavedOverlays() {
        return savedOverlays;
    }
    
    public void clearSavedOverlays() {
        if (savedOverlays != null)
            savedOverlays.clear();
        savedOverlays = null;
    }
    
    public void setAnnotationsCount(List<Integer> list) {
        annotationsCount = list;
    }
    
    public List<Integer> getAnnotationsCount() {
        return annotationsCount;
    }
    
    public void clearAnnotationsCount() {
        if (annotationsCount != null)
            annotationsCount.clear();
        annotationsCount = null;
    }
    
    public void setAnnotations(List<Annotation> list) {
        annotations = list;
    }
    
    public List<Annotation> getAnnotations() {
        return annotations;
    }
    
    public void clearAnnotations() {
        if (annotations != null)
            annotations.clear();
        annotations = null;
    }
    
    public void setVisibleRange(Range r) {
        visibleRange = r;
    }
    
    public Range getVisibleRange() {
        return visibleRange;
    }
    
    public void calculateRange(ChartContext chartFrame, List<Overlay> overlays) {
        Range range = new Range();
        if (!isVisibleNull()) {
            Range.Builder di = getVisible().getRange(null);
            double min = di.max;
            double max = di.min;
            range = new Range(min - (max - min) * 0.00, max + (max - min) * 0.00);//TODO: changed
            
            if (!overlays.isEmpty())
                for (int i = 0; i < overlays.size(); i++) {
                    Overlay overlay = overlays.get(i);
                    if (overlay.isIncludedInRange()) {
                        Range oRange = overlay.getRange(chartFrame);
                        if (oRange != null) {
                            if (oRange.getMin() > 0)
                                range = Range.expandToInclude(range, oRange.getMin());
                            if (!Double.isInfinite(oRange.getMax()))
                                range = Range.expandToInclude(range, oRange.getMax());
                        }
                    }
                }
        }
        setVisibleRange(range);
    }
    
    public void calculate(ChartContext chartFrame) {
        CandleSeries dataset = getDataset();
        if (dataset != null) {
            double barWidth = chartFrame.getChartProperties().getBarWidth();
            double rectWidth = chartFrame.getMainPanel().getStackPanel().getWidth();
            
            if (last == -1)
                last = dataset.length();
            if (barWidth >= 3.0)
                period = (int) (rectWidth / (barWidth + 2));
            else
                period = (int) (rectWidth / barWidth);
            
            if (period == 0)
                period = 150;
            period = Math.min(period, dataset.length());
            
            setVisible(new VisibleCandles(dataset, dataset.length() - last, period));
            
            // shift the marker index if new dataset is shorter than marker location
            int markerIndex = chartFrame.getMainPanel().getStackPanel().getMarkerIndex();
            if (markerIndex > period - 1) {
                markerIndex = period - 1;
                chartFrame.getMainPanel().getStackPanel().setMarkerIndex(markerIndex);
            }
            chartFrame.updateHorizontalScrollBar();
        }
    }
    
    class DefaultDateScale extends DateScale {
        
        DefaultDateScale(ChronoUnit incrementUnit, int incrementStep, ZonedDateTime startDate, ZonedDateTime endDate) {
            ZonedDateTime date;
            switch (incrementUnit) {
            case YEARS:
                int year = startDate.getYear();
                date = LocalDateTime.of(year/incrementStep*incrementStep, 1, 1, 0, 0).atZone(startDate.getZone());
                labelFormat = DateTimeFormatter.ofPattern("yyyy");
                break;
            case MONTHS:
                int month = startDate.getMonthValue();
                date = LocalDateTime.of(startDate.getYear(), (month - 1)/incrementStep*incrementStep + 1, 1, 0, 0).atZone(startDate.getZone());
                labelFormat = DateTimeFormatter.ofPattern("MMM");
                break;
            case DAYS:
                date = startDate.toLocalDate().atStartOfDay().atZone(startDate.getZone());
                labelFormat = DateTimeFormatter.ofPattern("d");
                break;
            case HOURS:
                int hour = startDate.getHour();
                date = startDate.toLocalDateTime().truncatedTo(ChronoUnit.HOURS).withHour(hour/incrementStep*incrementStep).atZone(startDate.getZone());
                labelFormat = DateTimeFormatter.ofPattern("HH:mm");
                break;
            default:
                throw new UnsupportedOperationException("Unsupported date scale increment unit: " + incrementUnit);
            }
            
            List<LocalDateTime> dates = new ArrayList<>();
            do {
                dates.add(date.withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime());
                date = date.plus(incrementStep, incrementUnit);
            } while (!date.isAfter(endDate));
            this.dates = dates.toArray(new LocalDateTime[dates.size()]);
            
            // construct subscale
            if (timeFrame.isIntraday()) {
                switch (incrementUnit) {
                case YEARS:
                    if (ChronoUnit.DAYS.between(startDate, endDate) < 7)
                        subScale = new DefaultDateScale(ChronoUnit.DAYS, 1, startDate, endDate);
                    else if (dates.size() < 3)
                        subScale = new DefaultDateScale(ChronoUnit.MONTHS, 1, startDate, endDate);
                    else if (dates.size() < 9)
                        subScale = new DefaultDateScale(ChronoUnit.MONTHS, 3, startDate, endDate);
                    break;
                case MONTHS:
                    if (dates.size() <= 3)
                        subScale = new DefaultDateScale(ChronoUnit.DAYS, 1, startDate, endDate);
                    break;
                case DAYS:
                    if (ChronoUnit.DAYS.between(startDate, endDate) < 7)
                        labelFormat = DateTimeFormatter.ofPattern("d MMM");
                    if (ChronoUnit.DAYS.between(startDate, endDate) < 2 && TimeFrame.Period.H1.isAssignableFrom(timeFrame))
                        subScale = new DefaultDateScale(ChronoUnit.HOURS, 1, startDate, endDate);
                    else if (ChronoUnit.DAYS.between(startDate, endDate) < 3 && TimeFrame.Period.H3.isAssignableFrom(timeFrame))
                        subScale = new DefaultDateScale(ChronoUnit.HOURS, 3, startDate, endDate);
                    else if (ChronoUnit.DAYS.between(startDate, endDate) < 3 && TimeFrame.Period.H4.isAssignableFrom(timeFrame))
                        subScale = new DefaultDateScale(ChronoUnit.HOURS, 4, startDate, endDate);
                    else if (ChronoUnit.DAYS.between(startDate, endDate) < 5 && TimeFrame.Period.H6.isAssignableFrom(timeFrame))
                        subScale = new DefaultDateScale(ChronoUnit.HOURS, 6, startDate, endDate);
                    break;
                }
            } else if (incrementUnit == ChronoUnit.YEARS) {
                if (dates.size() < 3)
                    subScale = new DefaultDateScale(ChronoUnit.MONTHS, 1, startDate, endDate);
                else if (dates.size() < 9)
                    subScale = new DefaultDateScale(ChronoUnit.MONTHS, 3, startDate, endDate);
            }
        }
        
        @Override
        public double mapMark(int i) {
            LocalDateTime datetime = dates[i];
            return visible.binarySearch(datetime);
        }
    }
    
    public AxisScale getDateAxisScale() {
        if (isVisibleNull())
            return null;
        
        int barCount = getVisible().getLength();
        ZonedDateTime endDate = Chronological.toDateTime(visible.getQuoteAt(barCount - 1).getTime(), (ZoneId)null);
        Candle firstBar = visible.getQuoteAt(-1);
        if (firstBar == null)
            firstBar = visible.getQuoteAt(0);
        ZonedDateTime startDate = Chronological.toDateTime(firstBar.getTime(), (ZoneId)null);
        int years = endDate.getYear() - startDate.getYear();
        if (ChronoUnit.DAYS.between(startDate, endDate) < 7)
            return new DefaultDateScale(ChronoUnit.DAYS, 1, startDate, endDate); //TODO
        else if (years > 0)
            return new DefaultDateScale(ChronoUnit.YEARS, 1, startDate, endDate); //TODO
        else
            return new DefaultDateScale(ChronoUnit.MONTHS, 1, startDate, endDate); //TODO
    }
    
    private static DecimalFormat D1 = new DecimalFormat("0.###");
    private static DecimalFormat D2 = new DecimalFormat("0.0");
    
    public double[] getYValues(Range range, Rectangle rectangle, Insets insets, int fontHeight) {
        int count = 15;
        while (((rectangle.height / count) < (fontHeight + 20)) && (count > -2))
            count--;
        
        double rangeMin = range.getMin();
        double rangeMax = range.getMax();
        
        double vRange = rangeMax - rangeMin;
        double rangeUnit = vRange / count;
        
        int roundedExponent = (int) Math.round(Math.log10(rangeUnit)) - 1;
        double factor = Math.pow(10, -roundedExponent);
        int adjustedValue = (int) (rangeUnit * factor);
        rangeUnit = (double) adjustedValue / factor;
        
        if (rangeUnit < 0.001) {
            rangeUnit = 0.001d;
        } else if (rangeUnit >= 0.001 && rangeUnit < 0.005) {
            String unitStr = D1.format(rangeUnit);
            try {
                rangeUnit = D1.parse(unitStr.trim()).doubleValue();
            } catch (ParseException ex) {
            }
        } else if (rangeUnit >= 0.005 && rangeUnit < 1) {
            String unitStr = D2.format(rangeUnit);
            try {
                rangeUnit = D2.parse(unitStr.trim()).doubleValue();
            } catch (ParseException ex) {
            }
        }
        
        rangeMin = (int) (rangeMin / rangeUnit) * rangeUnit;
        count = (int) (vRange / rangeUnit);
        
        if (count + 2 > 0) {
            double[] result = new double[count + 2];
            for (int i = 0; i < count + 2; i++)
                result[i] = rangeMin + rangeUnit * i;
            return result;
        } else {
            DoubleArray list = getPriceValues(range, rectangle, insets);
            double[] result = new double[list.size()];
            for (int i = 0; i < list.size(); i++)
                result[i] = list.get(i);
            return result;
        }
    }
    
    public DoubleArray getPriceValues(Range range, Rectangle bounds, Insets insets) {
        DoubleArray values = new DoubleArray();
        
        double diff = range.getUpperBound() - range.getLowerBound();
        if (diff > 10) {
            int step = (int) (diff / 10) + 1;
            double low = Math.ceil(range.getUpperBound() - (diff / 10) * 9);
            
            for (double i = low; i <= range.getUpperBound(); i += step)
                values.add(i);
        } else {
            double step = diff / 10;
            for (double i = range.getLowerBound(); i <= range.getUpperBound(); i += step)
                values.add(i);
        }
        
        return values;
    }
    
    public double calculateWidth(int width) {
        int count = getDatasetLength();
        int items = getPeriod();
        double w = width / items;
        return w * count;
    }
    
    public Point2D.Double valueToJava2D(double xvalue, final double yvalue, Rectangle bounds, Range range, boolean isLog) {
        double px = getX(xvalue, bounds);
        double py = getY(yvalue, bounds, range, isLog);
        Point2D.Double p = new Point2D.Double(px, py);
        return p;
    }
    
    public Point2D.Double getPoint(double x, double y, Range range, Rectangle rect, boolean isLog, Point2D.Double rv) {
        rv.x = getX(x, rect);
        rv.y = getY(y, rect, range, isLog);
        return rv;
    }
    
    public double getX(double value, Rectangle rect) {
        return rect.x + (((value + 0.5D) / (double) getPeriod()) * rect.width);
    }
    
    private double getY(double value, Rectangle2D rect, Range range) {
        return rect.getY() + (range.getMax() - value) / (range.getMax() - range.getMin()) * rect.getHeight();
    }
    
    private double getY(double value, Range range, Rectangle2D bounds, Insets insets) {
        double height = bounds.getHeight() - insets.top - insets.bottom;
        return bounds.getY() + insets.top + (range.getMax() - value) / (range.getMax() - range.getMin()) * height;
    }
    
    private double getReverseY(double y, Rectangle rect, Range range) {
        double value = range.getMax() - (y - rect.getY()) * (range.getMax() - range.getMin()) / rect.getHeight();
        return value;
    }
    
    private double getReverseY(double y, Range range, Rectangle bounds, Insets insets) {
        double height = bounds.getHeight() - insets.top - insets.bottom;
        double value = range.getMax() - (y - bounds.getY() - insets.top) * (range.getMax() - range.getMin()) / height;
        return value;
    }
    
    public double getY(double value, Range range, Rectangle bounds, Insets insets, boolean isLog) {
        if (isLog)
            return getLogY(value, range, bounds, insets);
        return getY(value, range, bounds, insets);
    }
    
    public double getY(double value, Rectangle rect, Range range, boolean isLog) {
        if (isLog)
            return getLogY(value, rect, range);
        return getY(value, rect, range);
    }
    
    public double getY2(double value, Rectangle2D.Double rect, Range range, boolean isLog) {
        if (isLog)
            return getLogY2(value, rect, range);
        return getY(value, rect, range);
    }
    
    public double getReverseY(double y, Rectangle rect, Range range, boolean isLog) {
        if (isLog)
            return getLogReverseY(y, rect, range);
        return getReverseY(y, rect, range);
    }
    
    public double getReverseY(double y, Range range, Rectangle bounds, Insets insets, boolean isLog) {
        if (isLog)
            return getLogReverseY(y, range, bounds, insets);
        return getReverseY(y, range, bounds, insets);
    }
    
    private double getLogY(double value, Rectangle rect, Range range) {
        double base = 0;
        if (range.getMin() < 0)
            base = Math.abs(range.getMin()) + 1.0D;
        double scale = (rect.getHeight() / (Math.log((range.getMax() + base)/(range.getMin() + base))));
        return rect.getY() + (int)(0.5 + (Math.log((range.getMax() + base)/(value + base))) * scale);
    }
    
    private double getLogY(double value, Range range, Rectangle bounds, Insets insets) {
        double base = 0;
        if (range.getMin() < 0)
            base = Math.abs(range.getMin()) + 1.0D;
        double scale = (bounds.height - insets.top - insets.bottom) / Math.log((range.getMax() + base)/(range.getMin() + base));
        return bounds.getY() + insets.top + (int)(0.5 + scale*Math.log((range.getMax() + base)/(value + base)));
    }
    
    private double getLogY2(double value, Rectangle2D.Double rect, Range range) {
        double base = 0;
        if (range.getMin() < 0)
            base = Math.abs(range.getMin()) + 1.0D;
        double scale = (rect.getHeight() / (Math.log((range.getMax() + base)/(range.getMin() + base))));
        return rect.getY() + (Math.log((range.getMax() + base)/(value + base))) * scale;
    }
    
    private double getLogReverseY(double y, Rectangle rect, Range range) {
        double base = 0;
        if (range.getMin() < 0)
            base = Math.abs(range.getMin()) + 1.0D;
        double scale = (rect.getHeight() / (Math.log(range.getMax() + base) - Math.log(range.getMin() + base)));
        double value = Math.exp(Math.log(range.getMax() + base) - (y - rect.getY())/scale) - base;
        return value;
    }
    
    private double getLogReverseY(double y, Range range, Rectangle bounds, Insets insets) {
        double base = 0;
        if (range.getMin() < 0)
            base = Math.abs(range.getMin()) + 1.0D;
        double scale = (bounds.getHeight() - insets.top - insets.bottom) / (Math.log(range.getMax() + base) - Math.log(range.getMin() + base));
        double value = Math.exp(Math.log(range.getMax() + base) - (y - bounds.getY() - insets.top)/scale) - base;
        return value;
    }
    
    public int getIndex(int x, Rectangle rect) {
        return getIndex(x, 1, rect);
    }
    
    public int getIndex(int x, int y, Rectangle rect) {
        if (!rect.contains(x, y))
            return -1;
        
        int items = getPeriod();
        double w = rect.getWidth() / items;
        int index = (int)((x - rect.x) / w);
        if (index >= items)
            index = items;
        return index;
    }
    
    public int getIndex2(int x, int y, Rectangle rect) {
        int items = getPeriod();
        double w = rect.getWidth() / items;
        int index = (int)((x - rect.x) / w);
        //		if (index >= items)
        //			index = items;
        return index;
    }
    
    @Override
    public void symbolChanged(SymbolIdentity newSymbol) {
        setSymbol(newSymbol);
    }
    
    @Override
    public void timeFrameChanged(TimeFrame newInterval) {
        setTimeFrame(newInterval);
    }
    
    @Override
    public void chartChanged(Chart newChart) {
        setChart(newChart);
    }
    
    public double zoomIn(double barWidth) {
        double newWidth = (barWidth < 1)? barWidth * 2 : barWidth + 1;
        int i = (int) ((period * barWidth) / newWidth);
        newWidth = i < MIN_ITEMS ? barWidth : newWidth;
        return newWidth;
    }
    
    public double zoomOut(double barWidth) {
        if (hasDataset()) {
            double newWidth = (barWidth > 1)? barWidth - 1 : barWidth / 2;
            int barCount = (int) ((period * barWidth) / newWidth);
            if (barCount > getDataset().length())
                newWidth = (period * barWidth) / getDataset().length() * 0.9;
            return newWidth;
        }
        return barWidth;
    }
    
    @Override
    public void datasetChanged(CandleSeries quotes) {
        setDataset(quotes);
    }
    
    /**
     * @param dataProvider
     *            the dataProvider to set
     */
    public void setDataProvider(DataProvider dataProvider) {
        this.dataProvider = dataProvider;
    }
    
    /**
     * @return the dataRenderingHint
     */
    public DataRenderingHint getDataRenderingHint() {
        return dataRenderingHint;
    }
    
    /**
     * @param dataRenderingHint the dataRenderingHint to set
     */
    public void setDataRenderingHint(DataRenderingHint dataRenderingHint) {
        this.dataRenderingHint = dataRenderingHint;
    }
    
    /**
     * @return the chartCallbacks
     */
    public ChartCallbackRegistry getChartCallbacks() {
        return chartCallbacks;
    }
    
    /**
     * @param chartCallbacks the chartCallbacks to set
     */
    public void setChartCallbacks(ChartCallbackRegistry chartCallbacks) {
        this.chartCallbacks = chartCallbacks;
    }
    
    /**
     * Returns the chart dataset for the chart plot. The method gives the
     * instrument's dataset as provided by the {@link #getDataset()} method
     * optionally transformed with the {@link Chart#transformDataset(CandleSeries)} method
     * using the {@link #getChart() chart plot} attached to this instance.
     * 
     * @return the chart dataset to display
     * @see #computeChartDataset()
     */
    public CandleSeries getChartDataset() {
        return chartDataset;
    }
}
