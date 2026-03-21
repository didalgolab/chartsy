package one.chartsy.ui.chart.internal.engine;

import one.chartsy.charting.Axis;
import one.chartsy.charting.ChartLayout;
import one.chartsy.charting.DefaultDataRangePolicy;
import one.chartsy.charting.DefaultStepsDefinition;
import one.chartsy.charting.HiLoOpenCloseRendererLegendItem;
import one.chartsy.charting.LabelRenderer;
import one.chartsy.charting.Legend;
import one.chartsy.charting.LegendEntry;
import one.chartsy.charting.LogarithmicAxisTransformer;
import one.chartsy.charting.PlotStyle;
import one.chartsy.charting.Scale;
import one.chartsy.charting.ScaleAnnotation;
import one.chartsy.charting.data.DefaultDataSource;
import one.chartsy.charting.event.ChartAreaEvent;
import one.chartsy.charting.event.ChartListener;
import one.chartsy.charting.financial.AdaptiveCategoryTimeSteps;
import one.chartsy.charting.financial.SharedGrid;
import one.chartsy.charting.renderers.HiLoChartRenderer;
import one.chartsy.charting.renderers.SingleHiLoRenderer;
import one.chartsy.core.Range;
import one.chartsy.data.CandleSeries;
import one.chartsy.ui.chart.ChartContext;
import one.chartsy.ui.chart.ChartFonts;
import one.chartsy.ui.chart.ChartProperties;
import one.chartsy.ui.chart.Indicator;
import one.chartsy.ui.chart.Overlay;
import one.chartsy.ui.chart.Plot;
import one.chartsy.ui.chart.PlotRenderContext;
import one.chartsy.ui.chart.PriceChartStyle;
import one.chartsy.ui.chart.PixelPerfectCandleGeometry;
import one.chartsy.ui.chart.data.VisualRange;
import one.chartsy.TimeFrameHelper;

import javax.swing.BorderFactory;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Map;
import java.util.List;

public final class EngineChartHost implements AutoCloseable {
    private static final int PRICE_SCALE_MARGIN = 55;

    private final one.chartsy.charting.Chart chart = new one.chartsy.charting.Chart();
    private final Scale sharedTimeScale;
    private final Legend legend = new Legend();
    private ScaleAnnotation lastPriceAnnotation;
    private Font legendRegularFont;
    private Font legendEmphasisFont;
    private boolean emphasizeFirstLegendEntry;

    public EngineChartHost(Scale sharedTimeScale) {
        this.sharedTimeScale = sharedTimeScale;
        initializeChart();
    }

    public one.chartsy.charting.Chart chart() {
        return chart;
    }

    public Legend legend() {
        return legend;
    }

    public boolean rendersNatively(ChartContext context, Overlay overlay) {
        return overlay != null && !overlay.getPlots().isEmpty();
    }

    public void configurePriceChart(ChartContext context, List<Overlay> overlays, boolean showTimeScale) {
        if (isDisposed())
            return;
        chart.unSynchronizeAxis(Axis.X_AXIS);
        emphasizeFirstLegendEntry = true;
        clear();
        detachSharedTimeScale();
        applyChrome(context);
        applyViewport(context, context.getChartData().getVisibleRange());
        if (showTimeScale)
            attachTimeScaleOwner(context);
        applyPriceSeries(context);
        updateLastPriceAnnotation(context);

        for (Overlay overlay : overlays) {
            if (rendersNatively(context, overlay))
                renderPlots(context, overlay.getLabel(), overlay.getPlots());
        }

        finishConfiguration();
    }

    public void configureIndicatorChart(ChartContext context,
                                        List<? extends Indicator> indicators,
                                        VisualRange visualRange,
                                        boolean showTimeScale,
                                        one.chartsy.charting.Chart masterChart) {
        if (isDisposed())
            return;
        emphasizeFirstLegendEntry = false;
        clear();
        detachSharedTimeScale();
        applyChrome(context);
        if (masterChart != null) {
            chart.unSynchronizeAxis(Axis.X_AXIS);
            chart.synchronizeAxis(masterChart, Axis.X_AXIS, true);
        }

        applyViewport(context, visualRange.range());
        if (showTimeScale)
            attachTimeScaleOwner(context);
        if (visualRange.isLogarithmic())
            chart.getYAxis(0).setTransformer(createViewportLogarithmicTransformer());

        for (Indicator indicator : indicators)
            renderPlots(context, indicator.getLabel(), indicator.getPlots());

        finishConfiguration();
    }

    public Rectangle plotBounds(Component relativeTo) {
        var chartArea = chart.getChartArea();
        if (chartArea == null)
            return new Rectangle();
        Rectangle plotRect = chartArea.getPlotRect();
        if (plotRect == null || plotRect.isEmpty())
            return new Rectangle();
        Point topLeft = SwingUtilities.convertPoint(chart.getChartArea(), plotRect.x, plotRect.y, relativeTo);
        return new Rectangle(topLeft.x, topLeft.y, plotRect.width, plotRect.height);
    }

    @Override
    public void close() {
        if (isDisposed())
            return;
        chart.unSynchronizeAxis(Axis.X_AXIS);
        detachLastPriceAnnotation();
        detachSharedTimeScale();
        chart.dispose();
    }

    private void initializeChart() {
        chart.setBorder(BorderFactory.createEmptyBorder());
        chart.setOpaque(true);
        // NetBeans window-system layout changes often repaint only partial dirty
        // regions. The engine's optimized repaint path selects data from the
        // paint clip, which is fine for simple unique-x series, but can drop most
        // candlesticks because HiLo datasets contain duplicate x-values per slot.
        // Full chart repaints avoid that live-only failure mode and are now cheap
        // enough because crosshair motion no longer repaints the engine chart.
        chart.setOptimizedRepaint(false);
        chart.getChartArea().setOpaque(true);
        chart.getChartArea().setBorder(BorderFactory.createEmptyBorder());
        chart.getChartArea().setPlotRectIncludingAnnotations(false);
        chart.getChartArea().setTopMargin(0);
        chart.setDataRangePolicy(new DefaultDataRangePolicy() {
            @Override
            protected boolean shouldAdjust(one.chartsy.charting.Chart chart, Axis axis) {
                return false;
            }
        });
        attachLegend();
    }

    private void attachLegend() {
        chart.setLegend(legend);
    }

    private void finishConfiguration() {
        applyLegendEntryTypography();
        legend.setVisible(legend.getComponentCount() > 0);
        legend.setSize(legend.getPreferredSize());
        chart.revalidate();
        legend.revalidate();
        legend.doLayout();
        legend.repaint();
        chart.repaint();
        SwingUtilities.invokeLater(() -> {
            applyLegendEntryTypography();
            legend.revalidate();
            legend.doLayout();
            legend.repaint();
        });
    }

    private void clear() {
        if (isDisposed())
            return;
        while (chart.getRendererCount() > 0)
            chart.removeRenderer(chart.getRenderer(0));
        for (var decoration : List.copyOf(chart.getDecorations()))
            chart.removeDecoration(decoration);
        detachLastPriceAnnotation();
        legend.removeAll();
    }

    private boolean isDisposed() {
        return chart.getChartArea() == null;
    }

    private void applyChrome(ChartContext context) {
        ChartProperties props = context.getChartProperties();
        Font scaleFont = ChartFonts.scaleFont(props);
        Color background = props.getBackgroundColor();
        chart.setAntiAliasing(!context.getValueIsAdjusting());
        chart.setAntiAliasingText(true);
        chart.setBackground(background);
        chart.setBackgroundPaint(background);
        chart.setForeground(props.getFontColor());
        chart.setFont(scaleFont);
        chart.getChartArea().setBackground(background);
        PlotStyle plotStyle = chart.getChartArea().getPlotStyle();
        if (plotStyle != null)
            chart.getChartArea().setPlotStyle(plotStyle.setFillOn(false).setStrokeOn(false));

        applyLegendStyle(props);
        prepareTimeScale(context);

        chart.getYScale(0).setVisible(true);
        chart.getYScale(0).setCrossing(Axis.MAX_VALUE);
        chart.getYScale(0).setAutoSide(true);
        chart.getYScale(0).setAxisVisible(true);
        chart.getYScale(0).setLabelVisible(true);
        chart.getYScale(0).setMajorTickVisible(true);
        chart.getYScale(0).setMinorTickVisible(false);
        chart.getYScale(0).setForeground(props.getAxisColor());
        chart.getYScale(0).setAxisStroke(props.getAxisStroke());
        chart.getYScale(0).setMajorTickSize((int) Math.round(props.getAxisPriceStick()));
        chart.getYScale(0).setLabelOffset((int) Math.round(props.getAxisTick()));
        chart.getYScale(0).setLabelFont(scaleFont);
        chart.getYScale(0).setLabelColor(props.getFontColor());

        chart.getXGrid().setMajorPaint(props.getGridVerticalColor());
        chart.getXGrid().setMajorStroke(props.getGridVerticalStroke());
        chart.getXGrid().setMajorLineVisible(props.getGridVerticalVisibility());
        chart.getXGrid().setMinorLineVisible(false);
        chart.getXGrid().setDrawOrder(-2);

        chart.getYGrid(0).setMajorPaint(props.getGridHorizontalColor());
        chart.getYGrid(0).setMajorStroke(props.getGridHorizontalStroke());
        chart.getYGrid(0).setMajorLineVisible(props.getGridHorizontalVisibility());
        chart.getYGrid(0).setMinorLineVisible(false);
        chart.getYGrid(0).setDrawOrder(-2);
    }

    private void applyLegendStyle(ChartProperties props) {
        legendRegularFont = ChartFonts.legendFont(props);
        legendEmphasisFont = ChartFonts.legendSymbolFont(props);
        legend.setLayout(new FlowLayout(FlowLayout.LEFT, 6, 2));
        legend.setPaintingBackground(true);
        legend.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(90, 98, 108, 60)),
                BorderFactory.createEmptyBorder(1, 6, 1, 6)
        ));
        legend.setBackground(Color.WHITE);
        legend.setTransparency(190);
        legend.setOpaque(false);
        legend.setForeground(props.getFontColor());
        legend.setMovable(false);
        legend.setAntiAliasing(true);
        legend.setAntiAliasingText(true);
        legend.setFont(legendRegularFont);
    }

    private void prepareTimeScale(ChartContext context) {
        var dates = EngineSeriesAdapter.dates(context.getChartData());
        if (dates.length == 0) {
            sharedTimeScale.setVisible(false);
            sharedTimeScale.setStepsDefinition(new DefaultStepsDefinition());
        } else {
            sharedTimeScale.setVisible(true);
            sharedTimeScale.setCrossing(Axis.MIN_VALUE);
            sharedTimeScale.setAutoSide(true);
            sharedTimeScale.setForeground(context.getChartProperties().getAxisColor());
            sharedTimeScale.setAxisStroke(context.getChartProperties().getAxisStroke());
            sharedTimeScale.setLabelFont(ChartFonts.footerUpperFont(context.getChartProperties()));
            sharedTimeScale.setLabelColor(context.getChartProperties().getFontColor());
        sharedTimeScale.setSkippingLabel(true);
        sharedTimeScale.setSkipLabelMode(Scale.ADAPTIVE_SKIP);
            sharedTimeScale.setMajorTickVisible(true);
            sharedTimeScale.setMinorTickVisible(false);
            sharedTimeScale.setAxisVisible(false);
            sharedTimeScale.setLabelVisible(false);
            sharedTimeScale.setMajorTickSize((int) Math.round(context.getChartProperties().getAxisDateStick()));
            sharedTimeScale.setLabelOffset((int) Math.round(context.getChartProperties().getAxisTick()));
            sharedTimeScale.setStepsDefinition(new AdaptiveCategoryTimeSteps(
                    dates,
                    EngineSeriesAdapter.timeUnit(context.getChartData())
            ));
        }

        chart.setXGrid(new SharedGrid(sharedTimeScale, context.getChartProperties().getGridVerticalColor()));
        chart.getXGrid().setMajorStroke(context.getChartProperties().getGridVerticalStroke());
        chart.getXGrid().setMajorLineVisible(context.getChartProperties().getGridVerticalVisibility());
        chart.getXGrid().setMinorLineVisible(false);
        if (chart.getXScale() != null)
            chart.setXScale(null);
        chart.getChartArea().setMargins(new Insets(0, 0, 0, PRICE_SCALE_MARGIN));
    }

    private void attachTimeScaleOwner(ChartContext context) {
        if (chart.getXScale() != sharedTimeScale) {
            if (chart.getXScale() != null)
                chart.setXScale(null);
            chart.setXScale(sharedTimeScale);
        }
        chart.getXScale().setVisible(false);
        chart.getXScale().setAxisVisible(false);
        chart.getXScale().setLabelVisible(false);
        chart.getXScale().setMajorTickVisible(false);
        chart.getXScale().setMinorTickVisible(false);
        chart.getXScale().setLabelFont(ChartFonts.footerUpperFont(context.getChartProperties()));
        chart.getXScale().setLabelColor(context.getChartProperties().getFontColor());
    }

    private void detachSharedTimeScale() {
        var owner = sharedTimeScale.getChart();
        if (owner != null && owner != chart)
            owner.setXScale(null);
        if (chart.getXScale() == sharedTimeScale)
            chart.setXScale(null);
    }

    private void applyViewport(ChartContext context, Range range) {
        var data = context.getChartData();
        int totalSlots = Math.max(1, data.getTotalSlotCount());
        double viewportMinX = data.getViewportMinX();
        double viewportMaxX = Math.max(viewportMinX + 1.0, data.getViewportMaxX());
        double dataMinX = Math.min(-0.5, viewportMinX);
        double dataMaxX = Math.max(totalSlots - 0.5, viewportMaxX);

        chart.getXAxis().setAutoDataRange(false);
        chart.getXAxis().setAutoVisibleRange(false);
        chart.getXAxis().setDataRange(dataMinX, dataMaxX);
        chart.getXAxis().setVisibleRange(viewportMinX, viewportMaxX);

        Range safeRange = normalizeRange(range);
        chart.getYAxis(0).setAutoDataRange(false);
        chart.getYAxis(0).setAutoVisibleRange(false);
        chart.getYAxis(0).setDataRange(safeRange.min(), safeRange.max());
        chart.getYAxis(0).setVisibleRange(safeRange.min(), safeRange.max());
        chart.getYAxis(0).setTransformer(EngineSeriesAdapter.supportsLogarithmicScale(context, safeRange)
                ? createViewportLogarithmicTransformer()
                : null);
    }

    static LogarithmicAxisTransformer createViewportLogarithmicTransformer() {
        LogarithmicAxisTransformer transformer = new LogarithmicAxisTransformer();
        transformer.setRoundingToPowers(false);
        return transformer;
    }

    private static Range normalizeRange(Range range) {
        if (range == null || range.isEmpty())
            return Range.of(0.0, 1.0);
        if (!Double.isFinite(range.min()) || !Double.isFinite(range.max()))
            return Range.of(0.0, 1.0);
        if (range.max() <= range.min()) {
            double base = range.max();
            double padding = Math.max(Math.abs(base) * 0.01, 1.0);
            return Range.of(base - padding, base + padding);
        }
        return range;
    }

    private void applyPriceSeries(ChartContext context) {
        CandleSeries dataset = context.getChartData().getDisplayDataset();
        if (dataset == null || dataset.length() == 0)
            return;

        var ohlc = EngineSeriesAdapter.adapt("Price", dataset);
        var renderer = new HiLoChartRenderer(
                context.getChartData().getPriceChartStyle() == PriceChartStyle.OHLC
                        ? HiLoChartRenderer.Mode.OPENCLOSE
                        : HiLoChartRenderer.Mode.CANDLE,
                SingleHiLoRenderer.Type.STICK,
                EngineSeriesAdapter.widthPercent(context));
        renderer.setPixelBodyWidthHint(PixelPerfectCandleGeometry.snapBodyWidth(context.getChartProperties().getBarWidth()));
        renderer.setName(priceLegendName(context));
        renderer.setUseCategorySpacingAtBorders(true);
        renderer.setDataSource(new DefaultDataSource(ohlc.asArray()));
        renderer.setStyles(createPriceStyles(context.getChartProperties(), context.getChartData().getPriceChartStyle()));
        // Let the price renderer own the first legend row; overlay rows follow in
        // renderer order and therefore remain behind the symbol row.
        renderer.setLegendEntryProvider(() -> createPriceLegendEntries(renderer));
        renderer.setLegended(true);
        chart.addRenderer(renderer);
    }

    private static List<LegendEntry> createPriceLegendEntries(HiLoChartRenderer renderer) {
        if (!renderer.isLegended())
            return List.of();
        int childCount = renderer.getChildCount();
        if (childCount < 2)
            return List.of();

        List<LegendEntry> entries = new ArrayList<>(Math.max(1, childCount / 2));
        for (int i = 0; i + 1 < childCount; i += 2) {
            if (!(renderer.getChild(i) instanceof SingleHiLoRenderer hiLoChild))
                continue;
            if (!(renderer.getChild(i + 1) instanceof SingleHiLoRenderer openCloseChild))
                continue;
            entries.add(new HiLoOpenCloseRendererLegendItem(renderer, hiLoChild, openCloseChild));
        }
        return entries;
    }

    private void updateLastPriceAnnotation(ChartContext context) {
        detachLastPriceAnnotation();
        var lastCandle = context.getChartData().getLastDisplayedCandle();
        if (lastCandle == null)
            return;
        double lastPrice = lastCandle.close();
        if (!Double.isFinite(lastPrice))
            return;

        Color requestedBackground = lastCandle.close() >= lastCandle.open()
                ? context.getChartProperties().getBarUpColor()
                : context.getChartProperties().getBarDownColor();
        Color background = ensureContrastWithScaleBackground(requestedBackground, context.getChartProperties().getBackgroundColor());
        Color foreground = preferredTextColor(background);
        Color border = background.darker();

        LabelRenderer renderer = new LabelRenderer(background, border);
        renderer.setOpaque(true);
        renderer.setColor(foreground);
        renderer.setScalingFont(false);
        renderer.setFont(ChartFonts.scaleAnnotationFont(context.getChartProperties()));
        renderer.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(border),
                BorderFactory.createEmptyBorder(1, 5, 1, 5)
        ));

        lastPriceAnnotation = new ScaleAnnotation(lastPrice, formatPrice(lastPrice), renderer);
        chart.getYScale(0).addAnnotation(lastPriceAnnotation);
    }

    private void detachLastPriceAnnotation() {
        if (lastPriceAnnotation == null)
            return;
        Scale scale = lastPriceAnnotation.getScale();
        if (scale != null)
            scale.removeAnnotation(lastPriceAnnotation);
        lastPriceAnnotation = null;
    }

    private static String priceLegendName(ChartContext context) {
        var chartData = context.getChartData();
        if (chartData == null || chartData.getSymbol() == null)
            return "Price";
        String symbol = chartData.getSymbol().name();
        String timeFrame = TimeFrameHelper.getName(chartData.getTimeFrame());
        return symbol + ", " + timeFrame;
    }

    private PlotStyle[] createPriceStyles(ChartProperties properties, PriceChartStyle style) {
        Color outline = properties.getBarColor();
        PlotStyle wickStyle = new PlotStyle(properties.getBarStroke(), outline).setFillOn(false);
        PlotStyle riseStyle = new PlotStyle(properties.getBarStroke(), outline, properties.getBarUpColor());
        PlotStyle fallStyle = new PlotStyle(properties.getBarStroke(), outline, properties.getBarDownColor());
        if (style == PriceChartStyle.OHLC) {
            PlotStyle riseLine = new PlotStyle(properties.getBarStroke(), properties.getBarUpColor()).setFillOn(false);
            PlotStyle fallLine = new PlotStyle(properties.getBarStroke(), properties.getBarDownColor()).setFillOn(false);
            return new PlotStyle[] { wickStyle, wickStyle, riseLine, fallLine };
        }
        return new PlotStyle[] { wickStyle, wickStyle, riseStyle, fallStyle };
    }

    private void renderPlots(ChartContext context, String ownerLabel, Map<String, ? extends Plot> plots) {
        if (plots.isEmpty())
            return;

        var target = new EnginePlotRenderTarget(chart);
        boolean legendClaimed = false;
        int plotOrder = 0;
        for (var entry : plots.entrySet()) {
            Plot plot = entry.getValue();
            if (plot == null)
                continue;
            boolean legended = plot.supportsLegend() && !legendClaimed;
            if (legended)
                legendClaimed = true;
            plot.render(target, new PlotRenderContext(
                    context,
                    entry.getKey(),
                    primaryLegendName(ownerLabel, entry.getKey()),
                    plotOrder++,
                    context.getChartData().getHistoricalSlotCount(),
                    context.getChartData().getTotalSlotCount(),
                    EngineSeriesAdapter.widthPercent(context),
                    legended
            ));
        }
    }

    private static String primaryLegendName(String ownerLabel, String plotKey) {
        if (ownerLabel == null || ownerLabel.isBlank())
            return (plotKey == null || plotKey.isBlank()) ? "Plot" : plotKey;
        return ownerLabel;
    }

    private static String formatPrice(double value) {
        DecimalFormat decimalFormat = Math.abs(value) >= 10.0 ? new DecimalFormat("#,##0.00") : new DecimalFormat("#,##0.0000");
        return decimalFormat.format(value);
    }

    private static Color ensureContrastWithScaleBackground(Color requested, Color scaleBackground) {
        if (requested == null)
            return new Color(0xF3D36C);
        if (scaleBackground == null)
            return requested;
        return colorDistance(requested, scaleBackground) < 60.0 ? new Color(0xF3D36C) : requested;
    }

    private static double colorDistance(Color left, Color right) {
        int dr = left.getRed() - right.getRed();
        int dg = left.getGreen() - right.getGreen();
        int db = left.getBlue() - right.getBlue();
        return Math.sqrt(dr * dr + dg * dg + db * db);
    }

    private static Color preferredTextColor(Color background) {
        double luminance = (0.2126 * background.getRed() + 0.7152 * background.getGreen() + 0.0722 * background.getBlue()) / 255.0;
        return luminance > 0.64 ? Color.BLACK : Color.WHITE;
    }

    private void applyLegendEntryTypography() {
        if (legendRegularFont == null || legendEmphasisFont == null)
            return;

        boolean first = true;
        for (Component component : legend.getComponents()) {
            if (!(component instanceof LegendEntry entry))
                continue;
            entry.setFont(emphasizeFirstLegendEntry && first ? legendEmphasisFont : legendRegularFont);
            first = false;
        }
    }
}
