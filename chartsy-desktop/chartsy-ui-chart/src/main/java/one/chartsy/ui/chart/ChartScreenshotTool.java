/* Copyright 2025 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.ui.chart;

import one.chartsy.Candle;
import one.chartsy.SymbolIdentity;
import one.chartsy.SymbolResource;
import one.chartsy.TimeFrame;
import one.chartsy.data.CandleSeries;
import one.chartsy.data.DataQuery;
import one.chartsy.data.provider.FlatFileDataProvider;
import one.chartsy.kernel.ServiceManager;
import one.chartsy.time.Chronological;

import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Generates a {@link ChartFrame}-style chart screenshot without launching the full application.
 * <p>
 * This utility intentionally mirrors the "Basic Chart" template used by the GUI front-end
 * (candlesticks + common overlays/indicators).
 */
public final class ChartScreenshotTool {

    /**
     * Specifies what portion of candle data should be used for rendering.
     *
     * <p>To avoid "indicator warmup" gaps at the left edge of the visible window,
     * this range also supports keeping an additional number of historical bars
     * <em>before</em> the visible window.
     *
     * @param startTime inclusive visible start, or {@code null} for unbounded
     * @param endTime inclusive visible end, or {@code null} for unbounded
     * @param limit max number of bars in the visible window (most recent), or {@code 0} for unlimited
     * @param warmupBars additional bars to keep before the visible window (0 disables)
     */
    public record DataRange(LocalDateTime startTime, LocalDateTime endTime, int limit, int warmupBars) {
        /**
         * Default warmup used by {@link #last(int)} when {@code limit > 0}.
         * <p>
         * This value is chosen to cover long warmup overlays in the default "Basic Chart" template
         * (notably Sfora).
         */
        public static final int DEFAULT_WARMUP_BARS = 300;

        public DataRange {
            if (limit < 0)
                throw new IllegalArgumentException("limit < 0");
            if (warmupBars < 0)
                throw new IllegalArgumentException("warmupBars < 0");
        }

        public static DataRange last(int limit) {
            int warmupBars = (limit > 0) ? DEFAULT_WARMUP_BARS : 0;
            return new DataRange(null, null, limit, warmupBars);
        }

        public static DataRange last(int limit, int warmupBars) {
            return new DataRange(null, null, limit, warmupBars);
        }

        public static DataRange all() {
            return new DataRange(null, null, 0, 0);
        }

        public DataRange withWarmupBars(int warmupBars) {
            return new DataRange(startTime, endTime, limit, warmupBars);
        }
    }

    private ChartScreenshotTool() {
    }

    public static BufferedImage renderChart(FlatFileDataProvider provider,
                                            SymbolIdentity symbol,
                                            TimeFrame timeFrame,
                                            DataRange range,
                                            Dimension size) {
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(symbol, "symbol");
        Objects.requireNonNull(timeFrame, "timeFrame");
        Objects.requireNonNull(range, "range");
        Objects.requireNonNull(size, "size");
        requirePositive(size);

        var dataset = loadCandles(provider, symbol, timeFrame, range);
        var template = basicChartTemplate();
        var chartFrame = createChartFrame(provider, dataset, template, size);
        return paint(chartFrame, size, chartFrame.getChartProperties().getBackgroundColor());
    }

    public static void renderChartToPng(Path outputFile,
                                        FlatFileDataProvider provider,
                                        SymbolIdentity symbol,
                                        TimeFrame timeFrame,
                                        DataRange range,
                                        Dimension size) throws IOException {
        Objects.requireNonNull(outputFile, "outputFile");
        BufferedImage image = renderChart(provider, symbol, timeFrame, range, size);
        ImageIO.write(image, "png", outputFile.toFile());
    }

    static CandleSeries loadCandles(FlatFileDataProvider provider,
                                   SymbolIdentity symbol,
                                   TimeFrame timeFrame,
                                   DataRange range) {
        var resource = SymbolResource.of(symbol, timeFrame).withDataType(Candle.class);

        // Load all (bounded by endTime if provided), then apply visible range selection + warmup.
        DataQuery<Candle> query = DataQuery.<Candle>resource(resource)
                .endTime(range.endTime())
                .build();
        if (range.startTime() == null && range.limit() == 0) {
            CandleSeries series = provider.queryForCandles(query)
                    .collectSortedList()
                    .as(CandleSeries.of(resource));

            if (series == null || series.isEmpty())
                throw new IllegalStateException("No candle data for symbol `" + symbol.name() + "`");
            return series;
        }

        List<Candle> candles = provider.queryForCandles(query).collectSortedList().block();

        if (candles == null || candles.isEmpty())
            throw new IllegalStateException("No candle data for symbol `" + symbol.name() + "`");

        long start = (range.startTime() == null) ? Long.MIN_VALUE : Chronological.toEpochNanos(range.startTime());
        long end = (range.endTime() == null) ? Long.MAX_VALUE : Chronological.toEpochNanos(range.endTime());

        int startIndex = lowerBound(candles, start);
        int endIndexExclusive = upperBound(candles, end);
        if (startIndex >= endIndexExclusive)
            throw new IllegalStateException("No candle data in requested range for symbol `" + symbol.name() + "`");

        int limit = range.limit();
        int visibleStartIndex = (limit > 0)
                ? Math.max(startIndex, endIndexExclusive - limit)
                : startIndex;
        int warmupBars = range.warmupBars();
        int warmupStartIndex = (warmupBars > 0)
                ? Math.max(0, visibleStartIndex - warmupBars)
                : visibleStartIndex;

        var slice = new ArrayList<>(candles.subList(warmupStartIndex, endIndexExclusive));
        if (slice.isEmpty())
            throw new IllegalStateException("No candle data in requested range for symbol `" + symbol.name() + "`");

        return CandleSeries.of((SymbolResource<Candle>) resource, slice);
    }

    static ChartFrame createChartFrame(FlatFileDataProvider provider,
                                       CandleSeries dataset,
                                       ChartTemplate template,
                                       Dimension size) {
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(dataset, "dataset");
        Objects.requireNonNull(template, "template");
        Objects.requireNonNull(size, "size");
        requirePositive(size);

        return callOnEdt(() -> {
            var chartData = new ChartData();
            chartData.setDataProvider(provider);
            chartData.setSymbol(dataset.getResource().symbol());
            chartData.setTimeFrame(dataset.getResource().timeFrame());
            chartData.setChart(Objects.requireNonNull(template.getChart(), "template.chart"));

            var chartFrame = new ChartFrame();
            chartFrame.setChartData(chartData);
            chartFrame.setChartTemplate(template);

            chartFrame.setPreferredSize(size);
            chartFrame.setSize(size);
            chartFrame.initComponents(true);
            layoutRecursively(chartFrame);

            chartFrame.datasetLoaded(dataset);
            layoutRecursively(chartFrame);

            return chartFrame;
        });
    }

    static ChartTemplate basicChartTemplate() {
        ChartTemplate template = new ChartTemplate("Basic Chart");

        // Match the GUI front-end defaults (see GuiFrontEndAutoConfiguration#basicChartTemplate()).
        ChartProperties props = new ChartProperties();
        props.setAxisColor(new Color(0x2e3436));
        props.setAxisStrokeIndex(0);
        props.setAxisLogarithmicFlag(true);

        props.setBarWidth(4.0);
        props.setBarColor(new Color(0x2e3436));
        props.setBarStrokeIndex(0);
        props.setBarVisibility(true);
        props.setBarDownColor(new Color(0x2e3436));
        props.setBarDownVisibility(true);
        props.setBarUpColor(new Color(0xffffff));
        props.setBarUpVisibility(true);

        props.setGridHorizontalColor(new Color(0xeeeeec));
        props.setGridHorizontalStrokeIndex(0);
        props.setGridHorizontalVisibility(true);
        props.setGridVerticalColor(new Color(0xeeeeec));
        props.setGridVerticalStrokeIndex(0);
        props.setGridVerticalVisibility(true);

        props.setBackgroundColor(new Color(0xffffff));
        props.setFont(new Font("Dialog", Font.PLAIN, 12));
        props.setFontColor(new Color(0x2e3436));

        props.setMarkerVisibility(false);
        props.setToolbarVisibility(true);
        props.setToolbarSmallIcons(false);
        props.setToolbarShowLabels(true);
        props.setAnnotationLayerVisible(true);

        template.setChartProperties(props);
        template.setChart(ServiceManager.of(Chart.class).get("Candle Stick"));

        var overlays = ServiceManager.of(Overlay.class);
        template.addOverlay(overlays.get("FRAMA, Leading"));
        template.addOverlay(overlays.get("FRAMA, Trailing"));
        template.addOverlay(overlays.get("Sfora"));
        template.addOverlay(overlays.get("Volume"));
        template.addOverlay(overlays.get("Sentiment Bands"));

        var indicators = ServiceManager.of(Indicator.class);
        template.addIndicator(indicators.get("Fractal Dimension"));

        return template;
    }

    private static BufferedImage paint(ChartFrame chartFrame, Dimension size, Color background) {
        return callOnEdt(() -> {
            BufferedImage image = new BufferedImage(size.width, size.height, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = image.createGraphics();
            try {
                g.setColor(background != null ? background : Color.WHITE);
                g.fillRect(0, 0, size.width, size.height);
                chartFrame.paint(g);
                return image;
            } finally {
                g.dispose();
            }
        });
    }

    private static void layoutRecursively(java.awt.Component component) {
        component.doLayout();
        if (component instanceof java.awt.Container container) {
            for (java.awt.Component child : container.getComponents())
                layoutRecursively(child);
        }
    }

    private static void requirePositive(Dimension size) {
        if (size.width <= 0 || size.height <= 0)
            throw new IllegalArgumentException("size must be positive, got " + size.width + "x" + size.height);
    }

    private static int lowerBound(List<Candle> candles, long time) {
        int low = 0;
        int high = candles.size();
        while (low < high) {
            int mid = (low + high) >>> 1;
            if (candles.get(mid).time() < time)
                low = mid + 1;
            else
                high = mid;
        }
        return low;
    }

    private static int upperBound(List<Candle> candles, long time) {
        int low = 0;
        int high = candles.size();
        while (low < high) {
            int mid = (low + high) >>> 1;
            if (candles.get(mid).time() <= time)
                low = mid + 1;
            else
                high = mid;
        }
        return low;
    }

    private static <T> T callOnEdt(Callable<T> task) {
        if (SwingUtilities.isEventDispatchThread()) {
            try {
                return task.call();
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Throwable> error = new AtomicReference<>();
        try {
            SwingUtilities.invokeAndWait(() -> {
                try {
                    result.set(task.call());
                } catch (Throwable t) {
                    error.set(t);
                }
            });
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (java.lang.reflect.InvocationTargetException e) {
            throw new RuntimeException(e.getCause());
        }

        Throwable t = error.get();
        if (t != null) {
            if (t instanceof RuntimeException re)
                throw re;
            if (t instanceof Error err)
                throw err;
            throw new RuntimeException(t);
        }
        return result.get();
    }
}
