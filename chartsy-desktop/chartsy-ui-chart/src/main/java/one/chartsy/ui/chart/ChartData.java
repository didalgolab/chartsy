/*
 * Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.ui.chart;

import java.awt.Component;
import java.awt.GraphicsConfiguration;
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
import one.chartsy.core.Range;
import one.chartsy.data.CandleSeries;
import one.chartsy.data.provider.DataProvider;
import one.chartsy.time.Chronological;
import one.chartsy.ui.chart.axis.AxisScale;
import one.chartsy.ui.chart.axis.DateScale;
import one.chartsy.collections.DoubleArray;
import one.chartsy.ui.chart.data.VisibleCandles;

/**
 * Holds data associated with the displayed chart.
 * 
 * @author Mariusz Bernacki
 */
public class ChartData implements Serializable, ChartFrameListener {
    
    
    
    public static final int MIN_ITEMS = 1;
    public static final int MAX_ITEMS = 1000;
    private static final double TRAILING_DATA_GAP_PX = 1.0;
    private static final double MIN_BAR_WIDTH = PixelPerfectCandleGeometry.MIN_BODY_WIDTH;
    private static final double MAX_BAR_WIDTH = PixelPerfectCandleGeometry.MAX_BODY_WIDTH;
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
    /** The native engine-backed price chart style. */
    private PriceChartStyle priceChartStyle = PriceChartStyle.CANDLE;
    /** Optional slot policy used to project future slots for regular time frames. */
    private transient TimeFrameSlotPolicy slotPolicy;
    private VisibleCandles visible;
    private Range visibleRange;
    private List<Indicator> savedIndicators;
    private List<Overlay> savedOverlays;
    private List<Integer> annotationsCount;
    private List<Annotation> annotations;
    private int period = -1;
    private int last = -1;
    private DataRenderingHint dataRenderingHint;
    private int visibleSlots = 140;
    private int visibleStartSlot;
    private double leadingSlotPadding;
    private double trailingSlotPadding;
    private int userTailSlots;
    private int annotationTailSlots;
    private boolean viewportInitialized;
    
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
        this.slotPolicy = null;
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
        this.priceChartStyle = PriceChartStyle.fromChart(chart);
        
        // recalculate the chart dataset
        chartDataset = computeChartDataset().orElse(null);
    }

    public PriceChartStyle getPriceChartStyle() {
        return priceChartStyle;
    }

    public void setPriceChartStyle(PriceChartStyle priceChartStyle) {
        if (priceChartStyle != null)
            this.priceChartStyle = priceChartStyle;
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
        SymbolIdentity symbol = quotes.getResource().symbol();
        TimeFrame timeFrame = quotes.getResource().timeFrame();
        boolean symbolChanged = !symbol.equals(getSymbol());
        boolean timeFrameChanged = !timeFrame.equals(getTimeFrame());
        
        if (symbolChanged)
            this.symbol = symbol;
        if (timeFrameChanged)
            this.timeFrame = timeFrame;
        this.dataset = quotes;
        this.slotPolicy = null;
        this.userTailSlots = 0;
        this.annotationTailSlots = 0;
        this.viewportInitialized = false;
        
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
        Range.Builder range = new Range.Builder();
        if (!isVisibleNull() && getVisible().getLength() > 0) {
            Range di = getVisible().getRange(null).toRange();
            double min = di.min();
            double max = di.max();
            range.add(min - (max - min) * 0.00, max + (max - min) * 0.00);//TODO: changed
            
            if (!overlays.isEmpty())
                for (int i = 0; i < overlays.size(); i++) {
                    Overlay overlay = overlays.get(i);
                    if (overlay.isIncludedInRange()) {
                        Range oRange = overlay.getRange(chartFrame);
                        if (oRange != null) {
                            if (oRange.min() > 0)
                                range.add(oRange.min());
                            if (!Double.isInfinite(oRange.max()))
                                range.add(oRange.max());
                        }
                    }
                }
        }
        if (range.toRange().isEmpty() && hasDataset()) {
            Candle lastVisible = getDataset().get(0);
            range.add(lastVisible.low(), lastVisible.high());
        }
        setVisibleRange(range.toRange());
    }
    
    public void calculate(ChartContext chartFrame) {
        CandleSeries dataset = getDataset();
        if (dataset == null || dataset.length() == 0) {
            setVisible(null);
            visibleRange = null;
            period = -1;
            last = -1;
            leadingSlotPadding = 0.0;
            trailingSlotPadding = 0.0;
            return;
        }

        int historicalSlots = getHistoricalSlotCount();
        int desiredVisibleSlots = clampVisibleSlots(estimateVisibleSlots(chartFrame), getTotalSlotCount());
        if (!viewportInitialized) {
            visibleSlots = desiredVisibleSlots;
            visibleStartSlot = Math.max(0, historicalSlots - visibleSlots);
            viewportInitialized = true;
        } else if (visibleSlots != desiredVisibleSlots) {
            int rightEdge = getVisibleEndSlot();
            visibleSlots = desiredVisibleSlots;
            visibleStartSlot = Math.clamp(rightEdge - visibleSlots, 0, getMaxVisibleStart());
        } else {
            visibleSlots = clampVisibleSlots(visibleSlots, getTotalSlotCount());
            visibleStartSlot = Math.clamp(visibleStartSlot, 0, getMaxVisibleStart());
        }

        updateViewportMetrics(chartFrame);
        updateVisibleWindow();
        chartFrame.updateHorizontalScrollBar();
    }

    private int estimateVisibleSlots(ChartContext chartFrame) {
        double plotWidth = estimatePlotWidth(chartFrame);
        double displaySpan = projectorDeviceSpan(chartFrame, plotWidth);
        if (displaySpan <= 0.0)
            return Math.max(1, visibleSlots);

        double slotStep = estimateSlotStepPixels(chartFrame.getChartProperties());
        return Math.max(1, (int) Math.floor(displaySpan / slotStep));
    }

    private static double estimateSlotStepPixels(ChartProperties properties) {
        return PixelPerfectCandleGeometry.slotStep(properties.getBarWidth());
    }

    private static double estimatePlotWidth(ChartContext chartFrame) {
        if (chartFrame.getMainPanel() == null)
            return -1.0;

        var chartPanel = chartFrame.getMainPanel().getChartPanel();
        if (chartPanel != null) {
            Rectangle renderBounds = chartPanel.getRenderBounds();
            if (renderBounds.width > 0)
                return renderBounds.width;

            int panelWidth = chartPanel.getWidth();
            if (panelWidth > 0)
                return Math.max(1.0, panelWidth - dataOffset.right);
        }

        int stackWidth = chartFrame.getMainPanel().getStackPanel().getWidth();
        if (stackWidth > 0)
            return Math.max(1.0, stackWidth - dataOffset.right);
        return -1.0;
    }

    private int clampVisibleSlots(int requested, int totalSlots) {
        int maxVisible = Math.max(1, Math.min(MAX_ITEMS, Math.max(1, totalSlots)));
        int minVisible = Math.max(1, Math.min(MIN_ITEMS, maxVisible));
        return Math.clamp(requested, minVisible, maxVisible);
    }

    private void updateViewportMetrics(ChartContext chartFrame) {
        if (chartFrame == null || visibleSlots <= 0) {
            leadingSlotPadding = 0.0;
            trailingSlotPadding = 0.0;
            return;
        }

        double plotWidth = estimatePlotWidth(chartFrame);
        double displaySpan = projectorDeviceSpan(chartFrame, plotWidth);
        if (displaySpan <= 0.0) {
            leadingSlotPadding = 0.0;
            trailingSlotPadding = 0.0;
            return;
        }

        double slotStep = estimateSlotStepPixels(chartFrame.getChartProperties());
        double naturalVisibleSlots = displaySpan / slotStep;
        leadingSlotPadding = Math.max(0.0, naturalVisibleSlots - visibleSlots);
        trailingSlotPadding = (slotStep > 0.0 && displaySpan > TRAILING_DATA_GAP_PX)
                ? TRAILING_DATA_GAP_PX / slotStep
                : 0.0;
    }

    private static double projectorDisplaySpan(double plotWidth) {
        if (plotWidth <= 0.0)
            return -1.0;
        return Math.max(1.0, plotWidth - 1.0);
    }

    private static double projectorDeviceSpan(ChartContext chartFrame, double plotWidth) {
        double logicalSpan = projectorDisplaySpan(plotWidth);
        if (logicalSpan <= 0.0)
            return logicalSpan;
        return logicalSpan * deviceScaleX(chartFrame);
    }

    private static double deviceScaleX(ChartContext chartFrame) {
        Component component = null;
        double paintedScale = -1.0;
        if (chartFrame != null && chartFrame.getMainPanel() != null) {
            var chartPanel = chartFrame.getMainPanel().getChartPanel();
            if (chartPanel != null) {
                component = chartPanel;
                paintedScale = chartPanel.getLastPaintScaleX();
            }
            if (component == null)
                component = chartFrame.getMainPanel();
        }
        if (Double.isFinite(paintedScale) && paintedScale > 0.0)
            return paintedScale;

        GraphicsConfiguration configuration = (component != null) ? component.getGraphicsConfiguration() : null;
        if (configuration == null)
            return 1.0;

        double scale = configuration.getDefaultTransform().getScaleX();
        return (Double.isFinite(scale) && scale > 0.0) ? scale : 1.0;
    }

    private void updateVisibleWindow() {
        CandleSeries dataset = getDataset();
        if (dataset == null) {
            setVisible(null);
            period = -1;
            last = -1;
            return;
        }

        int historicalSlots = getHistoricalSlotCount();
        int visibleEndSlot = Math.min(historicalSlots, visibleStartSlot + visibleSlots);
        int visibleHistoricalLength = Math.max(0, visibleEndSlot - visibleStartSlot);
        int offset = historicalSlots - visibleEndSlot;
        setVisible(new VisibleCandles(dataset, Math.max(0, offset), visibleHistoricalLength));
        period = visibleSlots;
        last = historicalSlots - visibleStartSlot;
    }

    public void resetViewport() {
        viewportInitialized = false;
        userTailSlots = 0;
        annotationTailSlots = 0;
        visibleStartSlot = 0;
        leadingSlotPadding = 0.0;
        trailingSlotPadding = 0.0;
    }

    public int getVisibleSlotCount() {
        return visibleSlots;
    }

    public int getVisibleStartSlot() {
        return visibleStartSlot;
    }

    public int getVisibleEndSlot() {
        return visibleStartSlot + visibleSlots;
    }

    public double getLeadingSlotPadding() {
        return leadingSlotPadding;
    }

    public double getViewportSlotSpan() {
        return Math.max(1.0, visibleSlots + leadingSlotPadding + trailingSlotPadding);
    }

    public double getViewportMinX() {
        return visibleStartSlot - 0.5 - leadingSlotPadding;
    }

    public double getViewportMaxX() {
        return visibleStartSlot + visibleSlots - 0.5 + trailingSlotPadding;
    }

    public int getHistoricalSlotCount() {
        CandleSeries dataset = getChartDataset();
        if (dataset != null)
            return dataset.length();
        return getDatasetLength();
    }

    public CandleSeries getDisplayDataset() {
        return chartDataset != null ? chartDataset : dataset;
    }

    public int getFutureTailSlots() {
        if (!supportsFutureSlots())
            return 0;
        return Math.max(userTailSlots, annotationTailSlots);
    }

    public int getTotalSlotCount() {
        return getHistoricalSlotCount() + getFutureTailSlots();
    }

    public int getMaxVisibleStart() {
        return Math.max(0, getTotalSlotCount() - visibleSlots);
    }

    public int getMaxHistoricalVisibleStart() {
        return Math.max(0, getHistoricalSlotCount() - visibleSlots);
    }

    public boolean setVisibleStartSlot(int visibleStartSlot) {
        int clamped = Math.clamp(visibleStartSlot, 0, getMaxVisibleStart());
        if (this.visibleStartSlot == clamped)
            return false;
        this.visibleStartSlot = clamped;
        if (annotationTailSlots == 0 && getVisibleEndSlot() <= getHistoricalSlotCount())
            userTailSlots = 0;
        updateVisibleWindow();
        return true;
    }

    public boolean scrollVisibleBy(int deltaSlots) {
        if (deltaSlots == 0)
            return false;

        int currentStart = visibleStartSlot;
        int maxStartBefore = getMaxVisibleStart();
        int requested = currentStart + deltaSlots;
        if (supportsFutureSlots() && deltaSlots > 0 && currentStart >= maxStartBefore) {
            userTailSlots = Math.max(userTailSlots, requested - getMaxHistoricalVisibleStart());
            visibleSlots = clampVisibleSlots(visibleSlots, getTotalSlotCount());
        }

        boolean changed = setVisibleStartSlot(requested);
        if (deltaSlots < 0 && annotationTailSlots == 0 && getVisibleEndSlot() <= getHistoricalSlotCount()) {
            if (userTailSlots != 0) {
                userTailSlots = 0;
                changed = true;
                setVisibleStartSlot(Math.min(visibleStartSlot, getMaxVisibleStart()));
            }
        }
        return changed;
    }

    public boolean setVisibleSlotCount(int visibleSlots) {
        int current = this.visibleSlots;
        int clamped = clampVisibleSlots(visibleSlots, getTotalSlotCount());
        if (current == clamped)
            return false;

        int rightEdge = getVisibleEndSlot();
        this.visibleSlots = clamped;
        return setVisibleStartSlot(Math.max(0, rightEdge - clamped));
    }

    public boolean setAnnotationTailSlots(int annotationTailSlots) {
        int normalized = supportsFutureSlots() ? Math.max(0, annotationTailSlots) : 0;
        if (this.annotationTailSlots == normalized)
            return false;
        this.annotationTailSlots = normalized;
        setVisibleStartSlot(Math.min(visibleStartSlot, getMaxVisibleStart()));
        return true;
    }

    public int visibleIndexToSlot(int visibleIndex) {
        return visibleStartSlot + visibleIndex;
    }

    public int slotToVisibleIndex(int slot) {
        return slot - visibleStartSlot;
    }

    public boolean isFutureSlot(int slot) {
        return slot >= getHistoricalSlotCount();
    }

    public Candle getCandleAtSlot(int slot) {
        CandleSeries dataset = getDisplayDataset();
        int historicalSlots = getHistoricalSlotCount();
        if (dataset == null || slot < 0 || slot >= historicalSlots)
            return null;
        return dataset.get(historicalSlots - slot - 1);
    }

    public int getLastDisplayedHistoricalSlot() {
        int historicalEnd = Math.min(getHistoricalSlotCount(), getVisibleEndSlot());
        return historicalEnd - 1;
    }

    public Candle getLastDisplayedCandle() {
        return getCandleAtSlot(getLastDisplayedHistoricalSlot());
    }

    public long getSlotTime(int slot) {
        CandleSeries dataset = getDisplayDataset();
        if (dataset == null || dataset.length() == 0)
            return 0L;

        int historicalSlots = getHistoricalSlotCount();
        if (historicalSlots == 0)
            return 0L;
        if (slot < 0)
            slot = 0;
        if (slot < historicalSlots)
            return dataset.get(historicalSlots - slot - 1).time();

        if (slotPolicy == null) {
            long latest = dataset.get(0).time();
            slotPolicy = TimeFrameSlotPolicy.of(timeFrame, latest).orElse(null);
        }
        if (slotPolicy == null)
            return dataset.get(0).time();
        return slotPolicy.timeAtOffset(slot - historicalSlots + 1);
    }

    public int getSlotIndex(long epochNanos) {
        CandleSeries dataset = getDisplayDataset();
        int historicalSlots = getHistoricalSlotCount();
        if (dataset == null || historicalSlots == 0)
            return 0;

        long newestTime = dataset.get(0).time();
        if (epochNanos > newestTime) {
            if (slotPolicy == null)
                slotPolicy = TimeFrameSlotPolicy.of(timeFrame, newestTime).orElse(null);
            if (slotPolicy != null)
                return historicalSlots - 1 + Math.max(1, slotPolicy.slotOffset(epochNanos));
            return historicalSlots - 1;
        }

        int seriesIndex = dataset.getTimeline().getTimeLocation(epochNanos);
        if (seriesIndex < 0) {
            long oldestTime = dataset.get(historicalSlots - 1).time();
            if (epochNanos <= oldestTime)
                return 0;
            seriesIndex = -seriesIndex - (TimeFrameHelper.isIntraday(timeFrame) ? 1 : 2);
        }
        return historicalSlots - seriesIndex - 1;
    }

    public int getSlotAtX(double x, Rectangle rect) {
        int visibleIndex = getVisibleIndexAtX(x, rect);
        if (visibleIndex < 0)
            return -1;
        return visibleIndexToSlot(visibleIndex);
    }

    public double getSlotCenterX(int slot, Rectangle rect) {
        if (rect == null)
            return 0.0;
        if (visibleSlots <= 0 || rect.width <= 0)
            return rect.x;
        double slotStep = getSlotStepPixels(rect);
        return getLeftDataX(rect) + ((slot - visibleStartSlot) + 0.5) * slotStep;
    }

    public boolean supportsFutureSlots() {
        return timeFrame != null && (timeFrame.getAsSeconds().isPresent() || timeFrame.getAsMonths().isPresent());
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
            if (TimeFrameHelper.isIntraday(timeFrame)) {
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
            } else if (incrementUnit == ChronoUnit.MONTHS) {
                long days = ChronoUnit.DAYS.between(startDate, endDate);
                if (days <= 45)
                    subScale = new DefaultDateScale(ChronoUnit.DAYS, 1, startDate, endDate);
                else if (days <= 120)
                    subScale = new DefaultDateScale(ChronoUnit.DAYS, 7, startDate, endDate);
                else if (days <= 240)
                    subScale = new DefaultDateScale(ChronoUnit.DAYS, 14, startDate, endDate);
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
        ZonedDateTime endDate = Chronological.toDateTime(visible.getQuoteAt(barCount - 1).time(), (ZoneId)null);
        Candle firstBar = visible.getQuoteAt(-1);
        if (firstBar == null)
            firstBar = visible.getQuoteAt(0);
        ZonedDateTime startDate = Chronological.toDateTime(firstBar.time(), (ZoneId)null);
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
        
        double rangeMin = range.min();
        double rangeMax = range.max();
        
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
        
        double diff = range.max() - range.min();
        if (diff > 10) {
            int step = (int) (diff / 10) + 1;
            double low = Math.ceil(range.max() - (diff / 10) * 9);
            
            for (double i = low; i <= range.max(); i += step)
                values.add(i);
        } else {
            double step = diff / 10;
            for (double i = range.min(); i <= range.max(); i += step)
                values.add(i);
        }
        
        return values;
    }
    
    public double calculateWidth(int width) {
        int count = getDatasetLength();
        double w = projectorDisplaySpan(width) / getViewportSlotSpan();
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
        if (rect == null || rect.width <= 0)
            return 0.0;
        return getLeftDataX(rect) + (value + 0.5D) * getSlotStepPixels(rect);
    }
    
    private double getY(double value, Rectangle2D rect, Range range) {
        return rect.getY() + (range.max() - value) / (range.max() - range.min()) * rect.getHeight();
    }
    
    private double getY(double value, Range range, Rectangle2D bounds, Insets insets) {
        double height = bounds.getHeight() - insets.top - insets.bottom;
        return bounds.getY() + insets.top + (range.max() - value) / (range.max() - range.min()) * height;
    }
    
    private double getReverseY(double y, Rectangle rect, Range range) {
        double value = range.max() - (y - rect.getY()) * (range.max() - range.min()) / rect.getHeight();
        return value;
    }
    
    private double getReverseY(double y, Range range, Rectangle bounds, Insets insets) {
        double height = bounds.getHeight() - insets.top - insets.bottom;
        double value = range.max() - (y - bounds.getY() - insets.top) * (range.max() - range.min()) / height;
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
        if (range.min() < 0)
            base = Math.abs(range.min()) + 1.0D;
        double scale = (rect.getHeight() / (Math.log((range.max() + base)/(range.min() + base))));
        return rect.getY() + (int)(0.5 + (Math.log((range.max() + base)/(value + base))) * scale);
    }
    
    private double getLogY(double value, Range range, Rectangle bounds, Insets insets) {
        double base = 0;
        if (range.min() < 0)
            base = Math.abs(range.min()) + 1.0D;
        double scale = (bounds.height - insets.top - insets.bottom) / Math.log((range.max() + base)/(range.min() + base));
        return bounds.getY() + insets.top + (int)(0.5 + scale*Math.log((range.max() + base)/(value + base)));
    }
    
    private double getLogY2(double value, Rectangle2D.Double rect, Range range) {
        double base = 0;
        if (range.min() < 0)
            base = Math.abs(range.min()) + 1.0D;
        double scale = (rect.getHeight() / (Math.log((range.max() + base)/(range.min() + base))));
        return rect.getY() + (Math.log((range.max() + base)/(value + base))) * scale;
    }
    
    private double getLogReverseY(double y, Rectangle rect, Range range) {
        double base = 0;
        if (range.min() < 0)
            base = Math.abs(range.min()) + 1.0D;
        double scale = (rect.getHeight() / (Math.log(range.max() + base) - Math.log(range.min() + base)));
        double value = Math.exp(Math.log(range.max() + base) - (y - rect.getY())/scale) - base;
        return value;
    }
    
    private double getLogReverseY(double y, Range range, Rectangle bounds, Insets insets) {
        double base = 0;
        if (range.min() < 0)
            base = Math.abs(range.min()) + 1.0D;
        double scale = (bounds.getHeight() - insets.top - insets.bottom) / (Math.log(range.max() + base) - Math.log(range.min() + base));
        double value = Math.exp(Math.log(range.max() + base) - (y - bounds.getY() - insets.top)/scale) - base;
        return value;
    }
    
    public int getIndex(int x, Rectangle rect) {
        return getIndex(x, 1, rect);
    }
    
    public int getIndex(int x, int y, Rectangle rect) {
        if (rect == null || !rect.contains(x, y))
            return -1;

        return getVisibleIndexAtX(x, rect);
    }
    
    public int getIndex2(int x, int y, Rectangle rect) {
        return getVisibleIndexAtX(x, rect);
    }

    private double getLeftDataX(Rectangle rect) {
        return rect.x + leadingSlotPadding * getSlotStepPixels(rect);
    }

    private double getSlotStepPixels(Rectangle rect) {
        if (rect == null || rect.width <= 0)
            return 1.0;
        return projectorDisplaySpan(rect.width) / getViewportSlotSpan();
    }

    private int getVisibleIndexAtX(double x, Rectangle rect) {
        if (visibleSlots <= 0 || rect == null || rect.width <= 0 || x < rect.x || x > rect.x + rect.width)
            return -1;

        double slotStep = getSlotStepPixels(rect);
        double leftDataX = getLeftDataX(rect);
        double rightDataX = leftDataX + (visibleSlots + trailingSlotPadding) * slotStep;
        if (x < leftDataX || x > rightDataX)
            return -1;

        double relative = (x - leftDataX) / slotStep;
        return Math.min(visibleSlots - 1, Math.max(0, (int) Math.floor(relative)));
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
        return Math.clamp(
                PixelPerfectCandleGeometry.zoomIn(Math.max(MIN_BAR_WIDTH, barWidth)),
                MIN_BAR_WIDTH,
                MAX_BAR_WIDTH
        );
    }
    
    public double zoomOut(double barWidth) {
        return Math.clamp(
                PixelPerfectCandleGeometry.zoomOut(Math.max(MIN_BAR_WIDTH, barWidth)),
                MIN_BAR_WIDTH,
                MAX_BAR_WIDTH
        );
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
