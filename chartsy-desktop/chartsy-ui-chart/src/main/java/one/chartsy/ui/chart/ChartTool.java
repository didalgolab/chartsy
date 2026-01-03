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
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import org.apache.batik.dom.GenericDOMImplementation;
import org.apache.batik.svggen.SVGGeneratorContext;
import org.apache.batik.svggen.SVGGraphics2D;
import org.apache.batik.svggen.SVGCSSStyler;
import org.apache.batik.util.SVGConstants;
import org.w3c.dom.DOMImplementation;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.imageio.ImageIO;
import javax.swing.JLayer;
import javax.swing.SwingUtilities;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.LayoutManager;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.Writer;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Generates a {@link ChartFrame}-style chart screenshot or a prompt-ready
 * summary without launching the full application.
 * <p>
 * This utility intentionally mirrors the "Basic Chart" template used by the GUI front-end
 * (candlesticks + common overlays/indicators).
 */
public final class ChartTool {

    private static final String GOAL_ANALYZE = """
            Analyze this financial chart for trade opportunities and its qualities.
            """;
    private static final int PROMPT_BAR_COUNT = 60;
    private static final int SVG_DECIMAL_PRECISION = 2;

    public enum SvgMinifyMode {
        STANDARD,
        AGGRESSIVE
    }

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

    private ChartTool() {
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
        removeScreenshotChrome(chartFrame);
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

    public static void renderChartToSvg(Path outputFile,
                                        FlatFileDataProvider provider,
                                        SymbolIdentity symbol,
                                        TimeFrame timeFrame,
                                        DataRange range,
                                        Dimension size) throws IOException {
        renderChartToSvg(outputFile, provider, symbol, timeFrame, range, size, SvgMinifyMode.AGGRESSIVE);
    }

    public static void renderChartToSvg(Path outputFile,
                                        FlatFileDataProvider provider,
                                        SymbolIdentity symbol,
                                        TimeFrame timeFrame,
                                        DataRange range,
                                        Dimension size,
                                        SvgMinifyMode minifyMode) throws IOException {
        Objects.requireNonNull(outputFile, "outputFile");
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(symbol, "symbol");
        Objects.requireNonNull(timeFrame, "timeFrame");
        Objects.requireNonNull(range, "range");
        Objects.requireNonNull(size, "size");
        Objects.requireNonNull(minifyMode, "minifyMode");
        requirePositive(size);

        var dataset = loadCandles(provider, symbol, timeFrame, range);
        var template = basicChartTemplate();
        var chartFrame = createChartFrame(provider, dataset, template, size);
        removeScreenshotChrome(chartFrame);

        DOMImplementation impl = GenericDOMImplementation.getDOMImplementation();
        Document document = impl.createDocument(SVGConstants.SVG_NAMESPACE_URI, "svg", null);
        SVGGeneratorContext context = SVGGeneratorContext.createDefault(document);
        context.setComment(null);
        context.setPrecision(SVG_DECIMAL_PRECISION);
        SVGGraphics2D svgGraphics = new SVGGraphics2D(context, false);
        svgGraphics.setSVGCanvasSize(size);

        paintSvg(chartFrame, size, chartFrame.getChartProperties().getBackgroundColor(), svgGraphics);

        writeSvg(outputFile, svgGraphics, minifyMode);
    }

    public static String createPrompt(FlatFileDataProvider provider,
                                      SymbolIdentity symbol,
                                      TimeFrame timeFrame,
                                      DataRange range) {
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(symbol, "symbol");
        Objects.requireNonNull(timeFrame, "timeFrame");
        Objects.requireNonNull(range, "range");

        var dataset = loadCandles(provider, symbol, timeFrame, range);
        var daily = aggregateCandles(dataset, TimeFrame.Period.DAILY);
        var weekly = aggregateCandles(dataset, TimeFrame.Period.WEEKLY);
        var monthly = aggregateCandles(dataset, TimeFrame.Period.MONTHLY);

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("daily_bars", toBarsMap(daily, PROMPT_BAR_COUNT, "daily"));
        root.put("weekly_bars", toBarsMap(weekly, PROMPT_BAR_COUNT, "weekly"));
        root.put("monthly_bars", toBarsMap(monthly, PROMPT_BAR_COUNT, "monthly"));

        return GOAL_ANALYZE + "\n" + dumpYaml(root);
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

    private static List<Candle> aggregateCandles(CandleSeries series, TimeFrame timeFrame) {
        Objects.requireNonNull(series, "series");
        Objects.requireNonNull(timeFrame, "timeFrame");

        var aggregator = timeFrame.getAggregator();
        var chronological = new ArrayList<Candle>(series.length());
        for (Candle candle : series) {
            chronological.add(candle);
        }
        return aggregator.aggregate(chronological, true);
    }

    private static Map<String, Map<String, BigDecimal>> toBarsMap(List<Candle> candles, int count, String label) {
        int startIndex = Math.max(0, candles.size() - count);
        var bars = new LinkedHashMap<String, Map<String, BigDecimal>>(count);
        for (int i = candles.size() - 1; i >= startIndex; i--) {
            Candle candle = candles.get(i);
            LocalDate date = candle.getDate();
            bars.put(date.toString(), toOhlcvMap(candle));
        }
        return bars;
    }

    private static Map<String, BigDecimal> toOhlcvMap(Candle candle) {
        var values = new LinkedHashMap<String, BigDecimal>(5);
        values.put("open", toDecimal(candle.open()));
        values.put("high", toDecimal(candle.high()));
        values.put("low", toDecimal(candle.low()));
        values.put("close", toDecimal(candle.close()));
        values.put("volume", toDecimal(candle.volume()));
        return values;
    }

    private static BigDecimal toDecimal(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }

    private static String dumpYaml(Map<String, Object> data) {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setDefaultScalarStyle(DumperOptions.ScalarStyle.PLAIN);
        options.setPrettyFlow(true);
        options.setIndent(2);
        options.setIndicatorIndent(1);
        options.setLineBreak(DumperOptions.LineBreak.UNIX);

        Yaml yaml = new Yaml(options);
        return yaml.dump(data).stripTrailing();
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
        //template.addOverlay(overlays.get("Sentiment Bands"));

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

    private static void paintSvg(ChartFrame chartFrame,
                                 Dimension size,
                                 Color background,
                                 SVGGraphics2D svgGraphics) {
        callOnEdt(() -> {
            svgGraphics.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_DEFAULT);
            svgGraphics.setColor(background != null ? background : Color.WHITE);
            svgGraphics.fillRect(0, 0, size.width, size.height);
            chartFrame.paint(svgGraphics);
            return null;
        });
    }

    private static void writeSvg(Path outputFile,
                                 SVGGraphics2D svgGraphics,
                                 SvgMinifyMode minifyMode) throws IOException {
        Element root = svgGraphics.getRoot();
        SVGCSSStyler.style(root);
        minifySvg(root, minifyMode);
        root.setAttributeNS(SVGConstants.XMLNS_NAMESPACE_URI, SVGConstants.XMLNS_PREFIX, SVGConstants.SVG_NAMESPACE_URI);
        if (usesXlink(root)) {
            root.setAttributeNS(SVGConstants.XMLNS_NAMESPACE_URI,
                    SVGConstants.XMLNS_PREFIX + ":" + SVGConstants.XLINK_PREFIX,
                    SVGConstants.XLINK_NAMESPACE_URI);
        } else {
            root.removeAttributeNS(SVGConstants.XMLNS_NAMESPACE_URI,
                    SVGConstants.XMLNS_PREFIX + ":" + SVGConstants.XLINK_PREFIX);
            root.removeAttribute("xmlns:xlink");
        }

        TransformerFactory factory = TransformerFactory.newInstance();
        try (Writer writer = Files.newBufferedWriter(outputFile, StandardCharsets.UTF_8)) {
            Transformer transformer = factory.newTransformer();
            transformer.setOutputProperty(OutputKeys.METHOD, "xml");
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            transformer.setOutputProperty(OutputKeys.INDENT, "no");
            transformer.transform(new DOMSource(root), new StreamResult(writer));
        } catch (TransformerException e) {
            throw new IOException("Failed to write SVG output", e);
        }
    }

    private static void minifySvg(Element root, SvgMinifyMode minifyMode) {
        removeEmptyDefs(root);
        simplifyClipPaths(root);
        shortenClipPathIds(root);
        dedupeClipPaths(root);
        groupClipPathRuns(root);
        hoistClipPaths(root);
        removeXmlSpace(root);
        compactStyles(root);
        if (minifyMode == SvgMinifyMode.AGGRESSIVE) {
            stripRootStyleDefaults(root);
        }
        mergeShapeRuns(root);
        minifyPaths(root);
        removeUnusedIds(root);
        removeUnusedNamespaces(root);
        removeEmptyDefs(root);
        removeRedundantGroups(root);
        mergeDefs(root);
    }

    private static void removeEmptyDefs(Element root) {
        List<Element> toRemove = new ArrayList<>();
        visitElements(root, element -> {
            if ("defs".equals(element.getTagName())) {
                boolean hasElementChild = false;
                for (var child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
                    if (child instanceof Element) {
                        hasElementChild = true;
                        break;
                    }
                }
                if (!hasElementChild) {
                    toRemove.add(element);
                }
            }
        });
        for (Element element : toRemove) {
            var parent = element.getParentNode();
            if (parent != null) {
                parent.removeChild(element);
            }
        }
    }

    private static void shortenClipPathIds(Element root) {
        List<Element> clipPaths = new ArrayList<>();
        visitElements(root, element -> {
            if ("clipPath".equals(element.getTagName()) && element.hasAttribute("id")) {
                clipPaths.add(element);
            }
        });
        if (clipPaths.isEmpty()) {
            return;
        }

        Map<String, String> idMap = new LinkedHashMap<>();
        int index = 0;
        for (Element clipPath : clipPaths) {
            String oldId = clipPath.getAttribute("id");
            if (oldId == null || oldId.isEmpty()) {
                continue;
            }
            String newId = "c" + index++;
            idMap.put(oldId, newId);
            clipPath.setAttribute("id", newId);
        }
        if (idMap.isEmpty()) {
            return;
        }

        visitElements(root, element -> {
            var attributes = element.getAttributes();
            if (attributes == null) {
                return;
            }
            for (int i = 0; i < attributes.getLength(); i++) {
                var attr = attributes.item(i);
                String value = attr.getNodeValue();
                if (value == null || value.isEmpty()) {
                    continue;
                }
                String updated = replaceUrlReferences(value, idMap);
                if (!updated.equals(value)) {
                    attr.setNodeValue(updated);
                }
            }
        });
    }

    private static String replaceUrlReferences(String value, Map<String, String> idMap) {
        String updated = value;
        for (Map.Entry<String, String> entry : idMap.entrySet()) {
            String oldId = entry.getKey();
            String newId = entry.getValue();
            String urlToken = "url(#" + oldId + ")";
            if (updated.contains(urlToken)) {
                updated = updated.replace(urlToken, "url(#" + newId + ")");
            }
            if (updated.equals("#" + oldId)) {
                updated = "#" + newId;
            }
        }
        return updated;
    }

    private static void simplifyClipPaths(Element root) {
        visitElements(root, element -> {
            if (!"clipPath".equals(element.getTagName())) {
                return;
            }
            if ("userSpaceOnUse".equals(element.getAttribute("clipPathUnits"))) {
                element.removeAttribute("clipPathUnits");
            }
            Element rect = clipPathToRect(element);
            if (rect != null) {
                var existingChild = element.getFirstChild();
                while (existingChild != null) {
                    var next = existingChild.getNextSibling();
                    element.removeChild(existingChild);
                    existingChild = next;
                }
                element.appendChild(rect);
            }
        });
    }

    private static void dedupeClipPaths(Element root) {
        Map<String, String> duplicateMap = new LinkedHashMap<>();
        Map<String, String> signatureToId = new LinkedHashMap<>();
        List<Element> duplicates = new ArrayList<>();

        visitElements(root, element -> {
            if (!"clipPath".equals(element.getTagName())) {
                return;
            }
            if (!element.hasAttribute("id")) {
                return;
            }
            String id = element.getAttribute("id");
            if (id == null || id.isEmpty()) {
                return;
            }
            String signature = clipPathSignature(element);
            if (signature == null) {
                return;
            }
            String existing = signatureToId.get(signature);
            if (existing == null) {
                signatureToId.put(signature, id);
            } else if (!existing.equals(id)) {
                duplicateMap.put(id, existing);
                duplicates.add(element);
            }
        });

        if (duplicateMap.isEmpty()) {
            return;
        }

        visitElements(root, element -> {
            var attributes = element.getAttributes();
            if (attributes == null) {
                return;
            }
            for (int i = 0; i < attributes.getLength(); i++) {
                var attr = attributes.item(i);
                String value = attr.getNodeValue();
                if (value == null || value.isEmpty()) {
                    continue;
                }
                String updated = replaceUrlReferences(value, duplicateMap);
                if (!updated.equals(value)) {
                    attr.setNodeValue(updated);
                }
            }
        });

        for (Element element : duplicates) {
            var parent = element.getParentNode();
            if (parent != null) {
                parent.removeChild(element);
            }
        }
    }

    private static String clipPathSignature(Element clipPath) {
        Element rect = null;
        for (var child = clipPath.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element element) {
                if (!SVGConstants.SVG_RECT_TAG.equals(element.getTagName())) {
                    return null;
                }
                if (rect != null) {
                    return null;
                }
                rect = element;
            }
        }
        if (rect == null) {
            return null;
        }
        double x = parseNumber(rect.getAttribute("x"), 0.0);
        double y = parseNumber(rect.getAttribute("y"), 0.0);
        Double width = parseNumber(rect.getAttribute("width"));
        Double height = parseNumber(rect.getAttribute("height"));
        if (width == null || height == null) {
            return null;
        }
        return "rect:" + formatNumber(x) + ',' + formatNumber(y) + ','
                + formatNumber(width) + ',' + formatNumber(height);
    }

    private static Element clipPathToRect(Element clipPath) {
        Element path = null;
        for (var child = clipPath.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element element) {
                if (!"path".equals(element.getTagName())) {
                    return null;
                }
                if (path != null) {
                    return null;
                }
                path = element;
            }
        }
        if (path == null || !path.hasAttribute("d")) {
            return null;
        }
        Rect rect = parseRectPath(path.getAttribute("d"));
        if (rect == null) {
            return null;
        }
        Element rectElement = clipPath.getOwnerDocument()
                .createElementNS(SVGConstants.SVG_NAMESPACE_URI, SVGConstants.SVG_RECT_TAG);
        if (rect.x != 0.0) {
            rectElement.setAttribute("x", formatNumber(rect.x));
        }
        if (rect.y != 0.0) {
            rectElement.setAttribute("y", formatNumber(rect.y));
        }
        rectElement.setAttribute("width", formatNumber(rect.width));
        rectElement.setAttribute("height", formatNumber(rect.height));
        return rectElement;
    }

    private static Rect parseRectPath(String data) {
        if (data == null || data.isBlank()) {
            return null;
        }
        PathTokens tokens = parsePathTokens(data);
        if (tokens == null || tokens.hasOtherCommands()) {
            return null;
        }
        List<double[]> points = new ArrayList<>();
        int i = 0;
        while (i < tokens.items.size()) {
            PathToken token = tokens.items.get(i++);
            if (token.type == PathTokenType.COMMAND) {
                char cmd = token.command;
                if (cmd == 'Z' || cmd == 'z') {
                    continue;
                }
                if (cmd == 'm' || cmd == 'l') {
                    return null;
                }
                if (cmd != 'M' && cmd != 'L') {
                    return null;
                }
                while (i < tokens.items.size() && tokens.items.get(i).type == PathTokenType.NUMBER) {
                    if (i + 1 >= tokens.items.size()
                            || tokens.items.get(i + 1).type != PathTokenType.NUMBER) {
                        return null;
                    }
                    double x = tokens.items.get(i++).number;
                    double y = tokens.items.get(i++).number;
                    points.add(new double[]{x, y});
                }
            } else {
                return null;
            }
        }
        if (points.size() < 4) {
            return null;
        }
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        for (double[] point : points) {
            minX = Math.min(minX, point[0]);
            minY = Math.min(minY, point[1]);
            maxX = Math.max(maxX, point[0]);
            maxY = Math.max(maxY, point[1]);
        }
        if (!(minX < maxX && minY < maxY)) {
            return null;
        }
        for (double[] point : points) {
            boolean xOk = point[0] == minX || point[0] == maxX;
            boolean yOk = point[1] == minY || point[1] == maxY;
            if (!xOk || !yOk) {
                return null;
            }
        }
        return new Rect(minX, minY, maxX - minX, maxY - minY);
    }

    private static void hoistClipPaths(Element root) {
        hoistClipPathsRecursive(root);
    }

    private static ClipSummary hoistClipPathsRecursive(Element element) {
        if (isRenderableElement(element)) {
            String clip = getClipPathValue(element);
            if (clip == null) {
                return ClipSummary.withoutClip();
            }
            return ClipSummary.withClip(clip);
        }

        ClipSummary summary = ClipSummary.empty();
        for (var child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element childElement) {
                summary = summary.merge(hoistClipPathsRecursive(childElement));
            }
        }

        if ("g".equals(element.getTagName()) && summary.canHoist()
                && getClipPathValue(element) == null) {
            setClipPathValue(element, summary.clipValue);
            removeClipPathFromDescendants(element, summary.clipValue);
            return ClipSummary.withClip(summary.clipValue);
        }

        String ownClip = getClipPathValue(element);
        if (ownClip != null) {
            return ClipSummary.withClip(ownClip);
        }

        return summary;
    }

    private static void removeClipPathFromDescendants(Element root, String clipValue) {
        visitElements(root, element -> {
            if (element == root) {
                return;
            }
            if (!clipValue.equals(getClipPathValue(element))) {
                return;
            }
            element.removeAttribute("clip-path");
            removeStyleProperty(element, "clip-path");
        });
    }

    private static void groupClipPathRuns(Element root) {
        groupClipPathRunsRecursive(root);
    }

    private static void groupClipPathRunsRecursive(Element element) {
        var child = element.getFirstChild();
        while (child != null) {
            var nextSibling = child.getNextSibling();
            if (child instanceof Element childElement) {
                String clip = getClipPathValue(childElement);
                if (clip != null) {
                    List<Element> run = new ArrayList<>();
                    var runner = child;
                    while (runner instanceof Element runElement) {
                        if (!clip.equals(getClipPathValue(runElement))) {
                            break;
                        }
                        run.add(runElement);
                        runner = runElement.getNextSibling();
                    }
                    if (run.size() > 1) {
                        Element group = element.getOwnerDocument()
                                .createElementNS(SVGConstants.SVG_NAMESPACE_URI, SVGConstants.SVG_G_TAG);
                        group.setAttribute("clip-path", clip);
                        element.insertBefore(group, run.get(0));
                        for (Element runElement : run) {
                            removeClipPathFromElement(runElement, clip);
                            group.appendChild(runElement);
                        }
                        groupClipPathRunsRecursive(group);
                        child = runner;
                        continue;
                    }
                }
                groupClipPathRunsRecursive(childElement);
            }
            child = nextSibling;
        }
    }

    private static void removeClipPathFromElement(Element element, String clipValue) {
        if (!clipValue.equals(getClipPathValue(element))) {
            return;
        }
        element.removeAttribute("clip-path");
        removeStyleProperty(element, "clip-path");
    }

    private static String getClipPathValue(Element element) {
        String attrValue = element.getAttribute("clip-path");
        if (attrValue != null && !attrValue.isEmpty()) {
            return attrValue.trim();
        }
        String style = element.getAttribute("style");
        if (style == null || style.isBlank()) {
            return null;
        }
        Map<String, String> props = parseStyle(style);
        return props.get("clip-path");
    }

    private static void setClipPathValue(Element element, String clipValue) {
        if (clipValue == null || clipValue.isEmpty()) {
            return;
        }
        element.setAttribute("clip-path", clipValue);
        removeStyleProperty(element, "clip-path");
    }

    private static boolean removeStyleProperty(Element element, String property) {
        String style = element.getAttribute("style");
        if (style == null || style.isBlank()) {
            return false;
        }
        Map<String, String> props = parseStyle(style);
        if (!props.containsKey(property)) {
            return false;
        }
        props.remove(property);
        String updated = serializeStyle(props);
        if (updated.isEmpty()) {
            element.removeAttribute("style");
        } else {
            element.setAttribute("style", updated);
        }
        return true;
    }

    private static boolean isRenderableElement(Element element) {
        String tag = element.getTagName();
        return "path".equals(tag)
                || "rect".equals(tag)
                || "line".equals(tag)
                || "text".equals(tag)
                || "circle".equals(tag)
                || "ellipse".equals(tag)
                || "polygon".equals(tag)
                || "polyline".equals(tag)
                || "image".equals(tag)
                || "use".equals(tag);
    }

    private static void removeXmlSpace(Element root) {
        visitElements(root, element -> {
            if (!"text".equals(element.getTagName())) {
                return;
            }
            if (element.hasAttributeNS(SVGConstants.XML_NAMESPACE_URI, SVGConstants.XML_SPACE_ATTRIBUTE)) {
                element.removeAttributeNS(SVGConstants.XML_NAMESPACE_URI, SVGConstants.XML_SPACE_ATTRIBUTE);
            } else if (element.hasAttribute("xml:space")) {
                element.removeAttribute("xml:space");
            }
        });
    }

    private static void mergeShapeRuns(Element root) {
        mergeLineRuns(root);
        mergeRectRuns(root);
        mergePathRuns(root);
    }

    private static void mergeLineRuns(Element element) {
        String tagName = element.getTagName();
        if ("defs".equals(tagName) || "clipPath".equals(tagName)) {
            return;
        }
        var child = element.getFirstChild();
        while (child != null) {
            var nextSibling = child.getNextSibling();
            if (child instanceof Element childElement) {
                if ("line".equals(childElement.getTagName()) && canMergeElement(childElement)) {
                    String signature = buildSignature(childElement, Set.of("x1", "y1", "x2", "y2"));
                    if (signature != null) {
                        List<Element> run = new ArrayList<>();
                        var runner = child;
                        while (runner instanceof Element runElement
                                && "line".equals(runElement.getTagName())
                                && canMergeElement(runElement)
                                && signature.equals(buildSignature(runElement, Set.of("x1", "y1", "x2", "y2")))) {
                            run.add(runElement);
                            runner = runElement.getNextSibling();
                        }
                        if (!run.isEmpty()) {
                            Element path = element.getOwnerDocument()
                                    .createElementNS(SVGConstants.SVG_NAMESPACE_URI, SVGConstants.SVG_PATH_TAG);
                            copyAttributes(run.get(0), path, Set.of("x1", "y1", "x2", "y2"));
                            String d = buildLinePathData(run);
                            if (d != null && !d.isEmpty()
                                    && (run.size() > 1
                                    || shouldReplaceSingleElement(run.get(0), d, Set.of("x1", "y1", "x2", "y2"), "line"))) {
                                path.setAttribute("d", d);
                                element.insertBefore(path, run.get(0));
                                for (Element line : run) {
                                    element.removeChild(line);
                                }
                                mergeLineRuns(path);
                                child = runner;
                                continue;
                            }
                        }
                    }
                }
                mergeLineRuns(childElement);
            }
            child = nextSibling;
        }
    }

    private static void mergeRectRuns(Element element) {
        String tagName = element.getTagName();
        if ("defs".equals(tagName) || "clipPath".equals(tagName)) {
            return;
        }
        var child = element.getFirstChild();
        while (child != null) {
            var nextSibling = child.getNextSibling();
            if (child instanceof Element childElement) {
                if ("rect".equals(childElement.getTagName()) && canMergeRect(childElement)) {
                    String signature = buildSignature(childElement, Set.of("x", "y", "width", "height"));
                    if (signature != null) {
                        List<Element> run = new ArrayList<>();
                        var runner = child;
                        while (runner instanceof Element runElement
                                && "rect".equals(runElement.getTagName())
                                && canMergeRect(runElement)
                                && signature.equals(buildSignature(runElement, Set.of("x", "y", "width", "height")))) {
                            run.add(runElement);
                            runner = runElement.getNextSibling();
                        }
                        if (!run.isEmpty()) {
                            Element path = element.getOwnerDocument()
                                    .createElementNS(SVGConstants.SVG_NAMESPACE_URI, SVGConstants.SVG_PATH_TAG);
                            copyAttributes(run.get(0), path, Set.of("x", "y", "width", "height"));
                            String d = buildRectPathData(run);
                            if (d != null && !d.isEmpty()
                                    && (run.size() > 1
                                    || shouldReplaceSingleElement(run.get(0), d, Set.of("x", "y", "width", "height"), "rect"))) {
                                path.setAttribute("d", d);
                                element.insertBefore(path, run.get(0));
                                for (Element rect : run) {
                                    element.removeChild(rect);
                                }
                                mergeRectRuns(path);
                                child = runner;
                                continue;
                            }
                        }
                    }
                }
                mergeRectRuns(childElement);
            }
            child = nextSibling;
        }
    }

    private static void mergePathRuns(Element element) {
        String tagName = element.getTagName();
        if ("defs".equals(tagName) || "clipPath".equals(tagName)) {
            return;
        }
        var child = element.getFirstChild();
        while (child != null) {
            var nextSibling = child.getNextSibling();
            if (child instanceof Element childElement) {
                if (SVGConstants.SVG_PATH_TAG.equals(childElement.getTagName()) && canMergeElement(childElement)) {
                    String signature = buildSignature(childElement, Set.of("d"));
                    if (signature != null) {
                        List<Element> run = new ArrayList<>();
                        var runner = child;
                        while (runner instanceof Element runElement
                                && SVGConstants.SVG_PATH_TAG.equals(runElement.getTagName())
                                && canMergeElement(runElement)
                                && signature.equals(buildSignature(runElement, Set.of("d")))) {
                            run.add(runElement);
                            runner = runElement.getNextSibling();
                        }
                        if (run.size() > 1) {
                            String merged = mergePathData(run);
                            if (merged != null && !merged.isEmpty()) {
                                Element path = element.getOwnerDocument()
                                        .createElementNS(SVGConstants.SVG_NAMESPACE_URI, SVGConstants.SVG_PATH_TAG);
                                copyAttributes(run.get(0), path, Set.of("d"));
                                path.setAttribute("d", merged);
                                element.insertBefore(path, run.get(0));
                                for (Element item : run) {
                                    element.removeChild(item);
                                }
                                mergePathRuns(path);
                                child = runner;
                                continue;
                            }
                        }
                    }
                }
                mergePathRuns(childElement);
            }
            child = nextSibling;
        }
    }

    private static String mergePathData(List<Element> paths) {
        StringBuilder merged = new StringBuilder();
        for (Element path : paths) {
            String d = path.getAttribute("d");
            if (d == null || d.isBlank()) {
                return null;
            }
            String trimmed = d.trim();
            char first = trimmed.charAt(0);
            if (first != 'M') {
                return null;
            }
            if (merged.length() > 0) {
                merged.append(' ');
            }
            merged.append(trimmed);
        }
        return merged.toString();
    }

    private static boolean canMergeElement(Element element) {
        if (element.hasAttribute("id")) {
            return false;
        }
        String style = element.getAttribute("style");
        if (style != null && !style.isBlank() && style.contains("marker-")) {
            return false;
        }
        return true;
    }

    private static boolean canMergeRect(Element element) {
        if (!canMergeElement(element)) {
            return false;
        }
        if (element.hasAttribute("rx") || element.hasAttribute("ry")) {
            return false;
        }
        return true;
    }

    private static String buildSignature(Element element, Set<String> ignore) {
        var attributes = element.getAttributes();
        if (attributes == null || attributes.getLength() == 0) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < attributes.getLength(); i++) {
            var attr = attributes.item(i);
            String name = attr.getNodeName();
            if (ignore.contains(name)) {
                continue;
            }
            String value = attr.getNodeValue();
            if (value == null) {
                value = "";
            }
            if ("style".equals(name)) {
                value = normalizeStyle(value);
                if (value.isEmpty()) {
                    continue;
                }
            }
            parts.add(name + "=" + value);
        }
        parts.sort(String::compareTo);
        return String.join(";", parts);
    }

    private static void copyAttributes(Element source, Element target, Set<String> ignore) {
        var attributes = source.getAttributes();
        if (attributes == null) {
            return;
        }
        for (int i = 0; i < attributes.getLength(); i++) {
            var attr = attributes.item(i);
            String name = attr.getNodeName();
            if (ignore.contains(name)) {
                continue;
            }
            String value = attr.getNodeValue();
            if (value == null) {
                continue;
            }
            String ns = attr.getNamespaceURI();
            if (ns == null || ns.isEmpty()) {
                target.setAttribute(name, value);
            } else {
                target.setAttributeNS(ns, name, value);
            }
        }
    }

    private static String buildLinePathData(List<Element> lines) {
        StringBuilder builder = new StringBuilder();
        boolean hasCurrent = false;
        double currentX = 0.0;
        double currentY = 0.0;
        for (Element line : lines) {
            Double x1 = parseNumber(line.getAttribute("x1"));
            Double y1 = parseNumber(line.getAttribute("y1"));
            Double x2 = parseNumber(line.getAttribute("x2"));
            Double y2 = parseNumber(line.getAttribute("y2"));
            if (x1 == null || y1 == null || x2 == null || y2 == null) {
                return null;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            if (!hasCurrent) {
                builder.append('M').append(formatNumber(x1)).append(' ').append(formatNumber(y1));
                hasCurrent = true;
            } else {
                String absMove = "M" + formatNumber(x1) + " " + formatNumber(y1);
                String relMove = "m" + formatNumber(x1 - currentX) + " " + formatNumber(y1 - currentY);
                if (relMove.length() < absMove.length()) {
                    builder.append(relMove);
                } else {
                    builder.append(absMove);
                }
            }
            currentX = x1;
            currentY = y1;
            boolean vertical = nearlyEqual(x1, x2);
            boolean horizontal = nearlyEqual(y1, y2);
            if (vertical) {
                String abs = "V" + formatNumber(y2);
                String rel = "v" + formatNumber(y2 - y1);
                builder.append(rel.length() < abs.length() ? rel : abs);
            } else if (horizontal) {
                String abs = "H" + formatNumber(x2);
                String rel = "h" + formatNumber(x2 - x1);
                builder.append(rel.length() < abs.length() ? rel : abs);
            } else {
                String abs = "L" + formatNumber(x2) + " " + formatNumber(y2);
                String rel = "l" + formatNumber(x2 - x1) + " " + formatNumber(y2 - y1);
                builder.append(rel.length() < abs.length() ? rel : abs);
            }
            currentX = x2;
            currentY = y2;
        }
        return builder.toString();
    }

    private static boolean shouldReplaceSingleElement(Element element,
                                                      String pathData,
                                                      Set<String> ignoredAttributes,
                                                      String originalTagName) {
        int originalLen = estimateElementLength(element, originalTagName);
        int pathLen = estimatePathLength(element, ignoredAttributes, pathData);
        return pathLen < originalLen;
    }

    private static int estimateElementLength(Element element, String tagName) {
        int length = 1 + tagName.length();
        var attributes = element.getAttributes();
        if (attributes != null) {
            for (int i = 0; i < attributes.getLength(); i++) {
                var attr = attributes.item(i);
                String name = attr.getNodeName();
                String value = attr.getNodeValue();
                length += 1 + name.length() + 2 + (value == null ? 0 : value.length()) + 1;
            }
        }
        return length + 2;
    }

    private static int estimatePathLength(Element source, Set<String> ignoredAttributes, String pathData) {
        int length = 1 + SVGConstants.SVG_PATH_TAG.length();
        var attributes = source.getAttributes();
        if (attributes != null) {
            for (int i = 0; i < attributes.getLength(); i++) {
                var attr = attributes.item(i);
                String name = attr.getNodeName();
                if (ignoredAttributes.contains(name)) {
                    continue;
                }
                String value = attr.getNodeValue();
                length += 1 + name.length() + 2 + (value == null ? 0 : value.length()) + 1;
            }
        }
        length += 1 + 1 + 2 + pathData.length() + 1;
        return length + 2;
    }

    private static String buildRectPathData(List<Element> rects) {
        StringBuilder builder = new StringBuilder();
        boolean hasCurrent = false;
        double currentX = 0.0;
        double currentY = 0.0;
        for (Element rect : rects) {
            double x = parseNumber(rect.getAttribute("x"), 0.0);
            double y = parseNumber(rect.getAttribute("y"), 0.0);
            Double width = parseNumber(rect.getAttribute("width"));
            Double height = parseNumber(rect.getAttribute("height"));
            if (width == null || height == null) {
                return null;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            if (!hasCurrent) {
                builder.append('M').append(formatNumber(x)).append(' ').append(formatNumber(y));
                hasCurrent = true;
            } else {
                String absMove = "M" + formatNumber(x) + " " + formatNumber(y);
                String relMove = "m" + formatNumber(x - currentX) + " " + formatNumber(y - currentY);
                if (relMove.length() < absMove.length()) {
                    builder.append(relMove);
                } else {
                    builder.append(absMove);
                }
            }
            currentX = x;
            currentY = y;
            builder.append('h').append(formatNumber(width));
            builder.append('v').append(formatNumber(height));
            builder.append('h').append(formatNumber(-width));
            builder.append('z');
        }
        return builder.toString();
    }

    private static boolean nearlyEqual(double a, double b) {
        return Math.abs(a - b) < 1e-9;
    }

    private static void minifyPaths(Element root) {
        visitElements(root, element -> {
            if (!"path".equals(element.getTagName())) {
                return;
            }
            String d = element.getAttribute("d");
            if (d == null || d.isBlank()) {
                return;
            }
            String minified = minifyPathData(d);
            if (!minified.equals(d)) {
                element.setAttribute("d", minified);
            }
        });
    }

    private static String minifyPathData(String data) {
        PathTokens tokens = parsePathTokens(data);
        if (tokens == null || tokens.hasOtherCommands()) {
            return data;
        }
        StringBuilder out = new StringBuilder(data.length());
        int i = 0;
        char activeCmd = 0;
        while (i < tokens.items.size()) {
            PathToken token = tokens.items.get(i++);
            if (token.type != PathTokenType.COMMAND) {
                return data;
            }
            char cmd = token.command;
            if (cmd == 'Z' || cmd == 'z') {
                if (out.length() > 0) {
                    out.append(' ');
                }
                out.append(cmd);
                activeCmd = 0;
                continue;
            }
            if (cmd != 'M' && cmd != 'm' && cmd != 'L' && cmd != 'l') {
                return data;
            }
            if (cmd == 'M' || cmd == 'm') {
                if (out.length() > 0) {
                    out.append(' ');
                }
                out.append(cmd);
                int pairs = 0;
                while (i < tokens.items.size() && tokens.items.get(i).type == PathTokenType.NUMBER) {
                    if (i + 1 >= tokens.items.size()
                            || tokens.items.get(i + 1).type != PathTokenType.NUMBER) {
                        return data;
                    }
                    String x = tokens.items.get(i++).text;
                    String y = tokens.items.get(i++).text;
                    if (pairs > 0) {
                        out.append(' ');
                    }
                    out.append(x).append(' ').append(y);
                    pairs++;
                }
                activeCmd = (cmd == 'M') ? 'L' : 'l';
                continue;
            }
            if (i >= tokens.items.size() || tokens.items.get(i).type != PathTokenType.NUMBER) {
                return data;
            }
            while (i < tokens.items.size() && tokens.items.get(i).type == PathTokenType.NUMBER) {
                if (i + 1 >= tokens.items.size()
                        || tokens.items.get(i + 1).type != PathTokenType.NUMBER) {
                    return data;
                }
                String x = tokens.items.get(i++).text;
                String y = tokens.items.get(i++).text;
                if (activeCmd != cmd) {
                    if (out.length() > 0) {
                        out.append(' ');
                    }
                    out.append(cmd);
                    activeCmd = cmd;
                    out.append(x).append(' ').append(y);
                } else {
                    out.append(' ').append(x).append(' ').append(y);
                }
            }
        }
        return out.toString();
    }

    private static PathTokens parsePathTokens(String data) {
        String trimmed = data.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        List<PathToken> tokens = new ArrayList<>();
        int i = 0;
        while (i < trimmed.length()) {
            char c = trimmed.charAt(i);
            if (Character.isWhitespace(c) || c == ',') {
                i++;
                continue;
            }
            if (isPathCommand(c)) {
                tokens.add(PathToken.command(c));
                i++;
                continue;
            }
            if (c == '-' || c == '+' || c == '.' || Character.isDigit(c)) {
                int start = i;
                i++;
                while (i < trimmed.length()) {
                    char ch = trimmed.charAt(i);
                    if (Character.isDigit(ch) || ch == '.') {
                        i++;
                        continue;
                    }
                    if (ch == 'e' || ch == 'E') {
                        i++;
                        if (i < trimmed.length()) {
                            char sign = trimmed.charAt(i);
                            if (sign == '+' || sign == '-') {
                                i++;
                            }
                        }
                        continue;
                    }
                    break;
                }
                String raw = trimmed.substring(start, i);
                tokens.add(PathToken.number(raw));
                continue;
            }
            return null;
        }
        return new PathTokens(tokens);
    }

    private static boolean isPathCommand(char c) {
        return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z');
    }

    private static void removeUnusedIds(Element root) {
        Set<String> referenced = new HashSet<>();
        visitElements(root, element -> {
            var attributes = element.getAttributes();
            if (attributes == null) {
                return;
            }
            for (int i = 0; i < attributes.getLength(); i++) {
                var attr = attributes.item(i);
                String value = attr.getNodeValue();
                if (value == null || value.isEmpty()) {
                    continue;
                }
                collectUrlReferences(value, referenced);
                String name = attr.getNodeName();
                if ((name.endsWith("href") || "clip-path".equals(name)) && value.startsWith("#")) {
                    referenced.add(value.substring(1));
                }
            }
            if ("style".equals(element.getTagName())) {
                String css = element.getTextContent();
                if (css != null && !css.isBlank()) {
                    collectUrlReferences(css, referenced);
                }
            }
        });

        List<Element> toRemove = new ArrayList<>();
        visitElements(root, element -> {
            if (!element.hasAttribute("id")) {
                if ("clipPath".equals(element.getTagName())) {
                    toRemove.add(element);
                }
                return;
            }
            String id = element.getAttribute("id");
            if (id == null || id.isEmpty()) {
                if ("clipPath".equals(element.getTagName())) {
                    toRemove.add(element);
                } else {
                    element.removeAttribute("id");
                }
                return;
            }
            if (!referenced.contains(id)) {
                if ("clipPath".equals(element.getTagName())) {
                    toRemove.add(element);
                } else {
                    element.removeAttribute("id");
                }
            }
        });
        for (Element element : toRemove) {
            var parent = element.getParentNode();
            if (parent != null) {
                parent.removeChild(element);
            }
        }
    }

    private static void collectUrlReferences(String value, Set<String> referenced) {
        int index = 0;
        while (index < value.length()) {
            int start = value.indexOf("url(#", index);
            if (start < 0) {
                break;
            }
            int idStart = start + 5;
            int end = value.indexOf(')', idStart);
            if (end > idStart) {
                referenced.add(value.substring(idStart, end));
                index = end + 1;
            } else {
                break;
            }
        }
    }

    private static void removeUnusedNamespaces(Element root) {
        AtomicReference<Boolean> usesXlink = new AtomicReference<>(false);
        visitElements(root, element -> {
            if (Boolean.TRUE.equals(usesXlink.get())) {
                return;
            }
            var attributes = element.getAttributes();
            if (attributes == null) {
                return;
            }
            for (int i = 0; i < attributes.getLength(); i++) {
                String name = attributes.item(i).getNodeName();
                if (name != null && name.startsWith("xlink:")) {
                    usesXlink.set(true);
                    break;
                }
            }
        });
        if (!Boolean.TRUE.equals(usesXlink.get())) {
            root.removeAttributeNS(SVGConstants.XMLNS_NAMESPACE_URI,
                    SVGConstants.XMLNS_PREFIX + ":" + SVGConstants.XLINK_PREFIX);
            root.removeAttribute("xmlns:xlink");
        }
    }

    private static boolean usesXlink(Element root) {
        AtomicReference<Boolean> usesXlink = new AtomicReference<>(false);
        visitElements(root, element -> {
            if (Boolean.TRUE.equals(usesXlink.get())) {
                return;
            }
            var attributes = element.getAttributes();
            if (attributes == null) {
                return;
            }
            for (int i = 0; i < attributes.getLength(); i++) {
                String name = attributes.item(i).getNodeName();
                if (name != null && name.startsWith("xlink:")) {
                    usesXlink.set(true);
                    break;
                }
            }
        });
        return Boolean.TRUE.equals(usesXlink.get());
    }

    private static void removeRedundantGroups(Element root) {
        List<Element> toUnwrap = new ArrayList<>();
        visitElements(root, element -> {
            if ("g".equals(element.getTagName()) && !element.hasAttributes()) {
                toUnwrap.add(element);
            }
        });
        for (Element group : toUnwrap) {
            var parent = group.getParentNode();
            if (parent == null) {
                continue;
            }
            var child = group.getFirstChild();
            while (child != null) {
                var next = child.getNextSibling();
                parent.insertBefore(child, group);
                child = next;
            }
            parent.removeChild(group);
        }
    }

    private static void mergeDefs(Element root) {
        List<Element> defs = new ArrayList<>();
        for (var child = root.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element element && "defs".equals(element.getTagName())) {
                defs.add(element);
            }
        }
        if (defs.size() < 2) {
            return;
        }
        Element primary = defs.get(0);
        for (int i = 1; i < defs.size(); i++) {
            Element extra = defs.get(i);
            var node = extra.getFirstChild();
            while (node != null) {
                var next = node.getNextSibling();
                primary.appendChild(node);
                node = next;
            }
            var parent = extra.getParentNode();
            if (parent != null) {
                parent.removeChild(extra);
            }
        }
    }

    private static void compactStyles(Element root) {
        Map<Element, String> stylesByElement = new IdentityHashMap<>();
        Map<String, Integer> styleCounts = new HashMap<>();
        visitElements(root, element -> {
            if (!element.hasAttribute("style")) {
                return;
            }
            String normalized = normalizeStyle(element.getAttribute("style"));
            if (normalized.isEmpty()) {
                element.removeAttribute("style");
                return;
            }
            element.setAttribute("style", normalized);
            stylesByElement.put(element, normalized);
            styleCounts.merge(normalized, 1, Integer::sum);
        });

        if (stylesByElement.isEmpty()) {
            return;
        }

        List<String> reusableStyles = styleCounts.entrySet().stream()
                .filter(entry -> entry.getValue() > 1)
                .map(Map.Entry::getKey)
                .sorted()
                .toList();

        if (reusableStyles.isEmpty()) {
            return;
        }

        Map<String, String> classByStyle = new LinkedHashMap<>();
        int classIndex = 0;
        for (String style : reusableStyles) {
            classByStyle.put(style, "s" + classIndex++);
        }

        for (Map.Entry<Element, String> entry : stylesByElement.entrySet()) {
            String style = entry.getValue();
            String className = classByStyle.get(style);
            if (className == null) {
                continue;
            }
            Element element = entry.getKey();
            String existing = element.getAttribute("class");
            if (existing == null || existing.isEmpty()) {
                element.setAttribute("class", className);
            } else {
                element.setAttribute("class", existing + " " + className);
            }
            element.removeAttribute("style");
        }

        Element styleElement = root.getOwnerDocument()
                .createElementNS(SVGConstants.SVG_NAMESPACE_URI, SVGConstants.SVG_STYLE_TAG);
        StringBuilder css = new StringBuilder();
        for (Map.Entry<String, String> entry : classByStyle.entrySet()) {
            css.append('.').append(entry.getValue()).append('{')
                    .append(entry.getKey()).append('}');
        }
        styleElement.appendChild(root.getOwnerDocument().createTextNode(css.toString()));

        Element defs = findOrCreateDefs(root);
        var firstChild = defs.getFirstChild();
        if (firstChild != null) {
            defs.insertBefore(styleElement, firstChild);
        } else {
            defs.appendChild(styleElement);
        }
    }

    private static void stripRootStyleDefaults(Element root) {
        if (!SVGConstants.SVG_SVG_TAG.equals(root.getTagName())) {
            return;
        }
        String style = root.getAttribute("style");
        if (style == null || style.isBlank()) {
            return;
        }
        Map<String, String> properties = parseStyle(style);
        if (properties.isEmpty()) {
            root.removeAttribute("style");
            return;
        }

        boolean removed = false;
        removed |= removeDefaultStyleProperty(properties, "color-rendering", "auto");
        removed |= removeDefaultStyleProperty(properties, "fill", "black", "#000", "#000000");
        removed |= removeDefaultStyleProperty(properties, "fill-opacity", "1");
        removed |= removeDefaultStyleProperty(properties, "font-style", "normal");
        removed |= removeDefaultStyleProperty(properties, "font-weight", "normal");
        removed |= removeDefaultStyleProperty(properties, "image-rendering", "auto");
        removed |= removeDefaultStyleProperty(properties, "shape-rendering", "auto");
        removed |= removeDefaultStyleProperty(properties, "stroke-dasharray", "none");
        removed |= removeDefaultStyleProperty(properties, "stroke-dashoffset", "0");
        removed |= removeDefaultStyleProperty(properties, "stroke-linejoin", "miter");
        removed |= removeDefaultStyleProperty(properties, "stroke-opacity", "1");
        removed |= removeDefaultStyleProperty(properties, "stroke-width", "1");
        removed |= removeDefaultStyleProperty(properties, "text-rendering", "auto");

        if (!removed) {
            return;
        }
        String updated = serializeStyle(properties);
        if (updated.isEmpty()) {
            root.removeAttribute("style");
        } else {
            root.setAttribute("style", updated);
        }
    }

    private static boolean removeDefaultStyleProperty(Map<String, String> properties,
                                                      String name,
                                                      String... defaults) {
        String value = properties.get(name);
        if (value == null) {
            return false;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        for (String def : defaults) {
            if (normalized.equals(def)) {
                properties.remove(name);
                return true;
            }
        }
        return false;
    }

    private static Element findOrCreateDefs(Element root) {
        Element defs = null;
        for (var child = root.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element element && "defs".equals(element.getTagName())) {
                defs = element;
                break;
            }
        }
        if (defs != null) {
            return defs;
        }
        defs = root.getOwnerDocument().createElementNS(SVGConstants.SVG_NAMESPACE_URI, SVGConstants.SVG_DEFS_TAG);
        var firstChild = root.getFirstChild();
        if (firstChild != null) {
            root.insertBefore(defs, firstChild);
        } else {
            root.appendChild(defs);
        }
        return defs;
    }

    private static String normalizeStyle(String style) {
        Map<String, String> properties = parseStyle(style);
        return serializeStyle(properties);
    }

    private static Map<String, String> parseStyle(String style) {
        if (style == null) {
            return new LinkedHashMap<>();
        }
        String trimmed = style.trim();
        if (trimmed.isEmpty()) {
            return new LinkedHashMap<>();
        }
        Map<String, String> properties = new LinkedHashMap<>();
        for (String rawPart : trimmed.split(";")) {
            String part = rawPart.trim();
            if (part.isEmpty()) {
                continue;
            }
            int colon = part.indexOf(':');
            if (colon <= 0 || colon == part.length() - 1) {
                continue;
            }
            String name = part.substring(0, colon).trim();
            String value = part.substring(colon + 1).trim();
            if (!name.isEmpty() && !value.isEmpty()) {
                properties.put(name, minifyStyleValue(value));
            }
        }
        return properties;
    }

    private static String serializeStyle(Map<String, String> properties) {
        if (properties.isEmpty()) {
            return "";
        }
        String[] names = properties.keySet().toArray(new String[0]);
        Arrays.sort(names);
        StringBuilder normalized = new StringBuilder();
        for (String name : names) {
            normalized.append(name).append(':').append(properties.get(name)).append(';');
        }
        return normalized.toString();
    }

    private static String minifyStyleValue(String value) {
        String trimmed = value.trim();
        if (trimmed.startsWith("'") && trimmed.endsWith("'") && trimmed.length() > 2) {
            String inner = trimmed.substring(1, trimmed.length() - 1);
            if (inner.indexOf(' ') < 0 && inner.indexOf('"') < 0) {
                trimmed = inner;
            }
        }
        return minifyRgb(trimmed);
    }

    private static String minifyRgb(String value) {
        if (!value.startsWith("rgb(") || !value.endsWith(")")) {
            return value;
        }
        String content = value.substring(4, value.length() - 1);
        String[] parts = content.split(",");
        if (parts.length != 3) {
            return value;
        }
        int[] rgb = new int[3];
        for (int i = 0; i < 3; i++) {
            String part = parts[i].trim();
            try {
                rgb[i] = Integer.parseInt(part);
            } catch (NumberFormatException e) {
                return value;
            }
            if (rgb[i] < 0 || rgb[i] > 255) {
                return value;
            }
        }
        String hex = String.format("%02x%02x%02x", rgb[0], rgb[1], rgb[2]);
        if (hex.charAt(0) == hex.charAt(1)
                && hex.charAt(2) == hex.charAt(3)
                && hex.charAt(4) == hex.charAt(5)) {
            return "#" + hex.charAt(0) + hex.charAt(2) + hex.charAt(4);
        }
        return "#" + hex;
    }

    private static Double parseNumber(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static double parseNumber(String value, double fallback) {
        Double parsed = parseNumber(value);
        return parsed != null ? parsed : fallback;
    }

    private static void visitElements(Element root, java.util.function.Consumer<Element> visitor) {
        visitor.accept(root);
        for (var child = root.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element element) {
                visitElements(element, visitor);
            }
        }
    }

    private static String formatNumber(double value) {
        long asLong = Math.round(value);
        if (Math.abs(value - asLong) < 1e-9) {
            return Long.toString(asLong);
        }
        String text = Double.toString(value);
        if (text.indexOf('E') >= 0 || text.indexOf('e') >= 0) {
            return text;
        }
        if (text.indexOf('.') >= 0) {
            while (text.endsWith("0")) {
                text = text.substring(0, text.length() - 1);
            }
            if (text.endsWith(".")) {
                text = text.substring(0, text.length() - 1);
            }
        }
        return text;
    }

    private record Rect(double x, double y, double width, double height) {
    }

    private enum PathTokenType {
        COMMAND,
        NUMBER
    }

    private static final class PathToken {
        private final PathTokenType type;
        private final char command;
        private final double number;
        private final String text;

        private PathToken(char command) {
            this.type = PathTokenType.COMMAND;
            this.command = command;
            this.number = Double.NaN;
            this.text = null;
        }

        private PathToken(String text) {
            this.type = PathTokenType.NUMBER;
            this.command = 0;
            this.number = Double.parseDouble(text);
            this.text = text;
        }

        private static PathToken command(char command) {
            return new PathToken(command);
        }

        private static PathToken number(String text) {
            return new PathToken(text);
        }
    }

    private static final class PathTokens {
        private final List<PathToken> items;
        private final boolean otherCommands;

        private PathTokens(List<PathToken> items) {
            this.items = items;
            boolean hasOther = false;
            for (PathToken token : items) {
                if (token.type == PathTokenType.COMMAND) {
                    char cmd = token.command;
                    if (cmd != 'M' && cmd != 'm' && cmd != 'L' && cmd != 'l' && cmd != 'Z' && cmd != 'z') {
                        hasOther = true;
                        break;
                    }
                }
            }
            this.otherCommands = hasOther;
        }

        private boolean hasOtherCommands() {
            return otherCommands;
        }
    }

    private record ClipSummary(boolean hasRenderable, boolean allHaveClip, String clipValue) {
        private static ClipSummary empty() {
            return new ClipSummary(false, true, null);
        }

        private static ClipSummary withClip(String clip) {
            return new ClipSummary(true, true, clip);
        }

        private static ClipSummary withoutClip() {
            return new ClipSummary(true, false, null);
        }

        private ClipSummary merge(ClipSummary other) {
            if (!other.hasRenderable) {
                return this;
            }
            if (!this.hasRenderable) {
                return other;
            }
            if (!this.allHaveClip || !other.allHaveClip) {
                return new ClipSummary(true, false, null);
            }
            if (!Objects.equals(this.clipValue, other.clipValue)) {
                return new ClipSummary(true, false, null);
            }
            return new ClipSummary(true, true, this.clipValue);
        }

        private boolean canHoist() {
            return hasRenderable && allHaveClip && clipValue != null;
        }
    }

    private static void removeScreenshotChrome(ChartFrame chartFrame) {
        callOnEdt(() -> {
            for (Component component : chartFrame.getComponents()) {
                if (component instanceof JLayer<?> layer) {
                    Component view = layer.getView();
                    if (view instanceof Container container) {
                        LayoutManager layout = container.getLayout();
                        if (layout instanceof BorderLayout borderLayout) {
                            Component north = borderLayout.getLayoutComponent(container, BorderLayout.NORTH);
                            if (north != null)
                                container.remove(north);
                            Component south = borderLayout.getLayoutComponent(container, BorderLayout.SOUTH);
                            if (south != null)
                                container.remove(south);
                            container.revalidate();
                            container.doLayout();
                        }
                    }
                    break;
                }
            }
            layoutRecursively(chartFrame);
            return null;
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
