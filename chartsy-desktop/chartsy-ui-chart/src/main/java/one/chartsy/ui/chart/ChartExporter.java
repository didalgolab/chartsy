/* Copyright 2025 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.ui.chart;

import one.chartsy.Candle;
import one.chartsy.FrontEndSupport;
import one.chartsy.SymbolIdentity;
import one.chartsy.SymbolResource;
import one.chartsy.TimeFrame;
import one.chartsy.data.CandleSeries;
import one.chartsy.data.DataQuery;
import one.chartsy.data.provider.DataProvider;
import one.chartsy.kernel.ImageResource;
import org.apache.batik.dom.GenericDOMImplementation;
import org.apache.batik.svggen.SVGGeneratorContext;
import org.apache.batik.svggen.SVGGraphics2D;
import org.apache.batik.util.SVGConstants;
import org.openide.util.Lookup;
import org.openide.util.lookup.ServiceProvider;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.w3c.dom.DOMImplementation;
import org.w3c.dom.Document;

import javax.imageio.ImageIO;
import javax.swing.JLayer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.LayoutManager;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.zip.GZIPOutputStream;

public final class ChartExporter {

    @FunctionalInterface
    public interface ExportFormat<T> {

        ExportFormat<ImageResource> PNG = new Of<>("PNG", ".png");
        ExportFormat<ImageResource> SVG = new Of<>("SVG", ".svg");
        ExportFormat<Resource> SVGZ = new Of<>("SVGZ", ".svgz");
        ExportFormat<ChartDescription> YAML = new Of<>("YAML", ".yaml", ".yml");
        ExportFormat<ChartBundle> BUNDLE = new Of<>("BUNDLE");

        T export(DataProvider provider, CandleSeries dataset, ExportOptions options);

        default String name() {
            return getClass().getSimpleName();
        }

        default List<String> fileExtensions() {
            return List.of();
        }

        record Of<T>(String name, List<String> fileExtensions) implements ExportFormat<T> {

            public Of(String name, String... fileExtensions) {
                this(name, List.of(fileExtensions));
            }

            public Of {
                if (name == null || name.isBlank())
                    throw new IllegalArgumentException("name is blank");
                if (fileExtensions == null)
                    throw new IllegalArgumentException("fileExtensions is null");
            }

            @Override
            public T export(DataProvider provider, CandleSeries dataset, ExportOptions options) {
                ExportFormat<?> impl = Services.getByName(name);
                if (impl == null)
                    throw new IllegalStateException("Export format service not found: " + name);

                @SuppressWarnings("unchecked")
                ExportFormat<T> typed = (ExportFormat<T>) impl;
                return typed.export(provider, dataset, options);
            }
        }
    }

    private ChartExporter() { }

    public static void export(Path output, DataProvider provider, DataQuery<Candle> query) throws IOException {
        export(output, provider, query, ExportOptions.DEFAULT);
    }

    public static void export(Path output, DataProvider provider, SymbolResource<Candle> resource) throws IOException {
        export(output, provider, resource, ExportOptions.DEFAULT);
    }

    public static void export(Path output, DataProvider provider, SymbolIdentity symbol) throws IOException {
        export(output, provider, symbol, ExportOptions.DEFAULT);
    }

    public static void export(Path output, DataProvider provider, DataQuery<Candle> query, ExportOptions options) throws IOException {
        export(output, provider, loadCandles(provider, query), options);
    }

    public static void export(Path output, DataProvider provider, CandleSeries dataset) throws IOException {
        export(output, provider, dataset, ExportOptions.DEFAULT);
    }

    public static void export(Path output, DataProvider provider, CandleSeries dataset, ExportOptions options) throws IOException {
        ExportFormat<?> format = resolveFormat(output, options);
        Object exported = export(provider, dataset, options, format);
        writeExported(output, exported);
    }

    public static void export(Path output, DataProvider provider, SymbolResource<Candle> resource, ExportOptions options) throws IOException {
        if (resource == null)
            throw new IllegalArgumentException("resource is null");
        export(output, provider, DataQuery.of(resource), options);
    }

    public static void export(Path output, DataProvider provider, SymbolIdentity symbol, ExportOptions options) throws IOException {
        if (symbol == null)
            throw new IllegalArgumentException("symbol is null");
        SymbolResource<Candle> resource = SymbolResource.of(symbol, TimeFrame.Period.DAILY);
        export(output, provider, resource, options);
    }

    public static <T> T export(DataProvider provider, DataQuery<Candle> query, ExportFormat<T> format) {
        return export(provider, query, ExportOptions.DEFAULT, format);
    }

    public static <T> T export(DataProvider provider, SymbolResource<Candle> resource, ExportFormat<T> format) {
        return export(provider, resource, ExportOptions.DEFAULT, format);
    }

    public static <T> T export(DataProvider provider, SymbolIdentity symbol, ExportFormat<T> format) {
        return export(provider, symbol, ExportOptions.DEFAULT, format);
    }

    public static <T> T export(DataProvider provider, DataQuery<Candle> query, ExportOptions options, ExportFormat<T> format) {
        CandleSeries dataset = loadCandles(provider, query);
        return export(provider, dataset, options, format);
    }

    public static <T> T export(DataProvider provider, SymbolResource<Candle> resource, ExportOptions options, ExportFormat<T> format) {
        return export(provider, DataQuery.of(resource), options, format);
    }

    public static <T> T export(DataProvider provider, SymbolIdentity symbol, ExportOptions options, ExportFormat<T> format) {
        SymbolResource<Candle> resource = SymbolResource.of(symbol, TimeFrame.Period.DAILY);
        return export(provider, resource, options, format);
    }

    public static <T> T export(DataProvider provider, CandleSeries dataset, ExportFormat<T> format) {
        return export(provider, dataset, ExportOptions.DEFAULT, format);
    }

    public static <T> T export(DataProvider provider, CandleSeries dataset, ExportOptions options, ExportFormat<T> format) {
        if (provider == null)
            throw new IllegalArgumentException("provider is null");
        if (dataset == null)
            throw new IllegalArgumentException("dataset is null");
        if (options == null)
            throw new IllegalArgumentException("options is null");

        return format.export(provider, dataset, options);
    }

    private static ExportFormat<?> resolveFormat(Path output, ExportOptions options) {
        return options.getFormat().orElseGet(() -> forFileName(output));
    }

    static ExportFormat<?> forFileName(Path path) {
        String name = (path.getFileName() != null) ? path.getFileName().toString() : path.toString();
        int dot = name.lastIndexOf('.');
        if (dot < 0)
            throw new IllegalArgumentException("File extension missing for chart export: " + path);

        String extWithDot = name.substring(dot).toLowerCase(Locale.ROOT);
        for (ExportFormat<?> fileFormat : Services.fileFormats()) {
            if (fileFormat.fileExtensions().contains(extWithDot))
                return fileFormat;
        }
        throw new IllegalArgumentException("Unsupported chart export extension: " + extWithDot);
    }

    private static void writeExported(Path output, Object value) throws IOException {
        switch (value) {
            case Resource resource -> Files.write(output, resource.getContentAsByteArray(),
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            case String text -> Files.writeString(output, text);
            case ChartDescription desc -> Files.writeString(output, desc.toYamlString());
            case null, default ->
                    throw new IllegalArgumentException("Unsupported export result type: "
                            + (value == null ? "null" : value.getClass().getName()));
        }
    }

    static ChartDescription createChartDescription(CandleSeries dataset) {
        return ChartDescription.from(dataset);
    }

    static ChartFrame createExportChartFrame(DataProvider provider,
                                             DataQuery<Candle> query,
                                             ExportOptions options) {
        CandleSeries dataset = loadCandles(provider, query);
        ChartTemplate template = basicChartTemplate();
        Dimension size = options.getDimensions();
        return createChartFrame(provider, dataset, template, size);
    }

    public static CandleSeries loadCandles(DataProvider provider, DataQuery<Candle> query) {
        return provider.query(Candle.class, query)
                .collectSortedList()
                .as(CandleSeries.of(query.resource()));
    }

    static ChartFrame createChartFrame(DataProvider provider,
                                       CandleSeries dataset,
                                       ChartTemplate template,
                                       Dimension size) {
        return FrontEndSupport.getDefault().execute(() -> {
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

            removeScreenshotChrome(chartFrame);
            return chartFrame;
        });
    }

    static ChartTemplate basicChartTemplate() {
        ChartTemplate template = new ChartTemplate("Basic Chart");

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
        template.setChart(one.chartsy.kernel.ServiceManager.of(Chart.class).get("Candle Stick"));

        var overlays = one.chartsy.kernel.ServiceManager.of(Overlay.class);
        template.addOverlay(overlays.get("FRAMA, Leading"));
        template.addOverlay(overlays.get("FRAMA, Trailing"));
        template.addOverlay(overlays.get("Sfora"));
        template.addOverlay(overlays.get("Volume"));
        template.addOverlay(overlays.get("Sentiment Bands"));

        var indicators = one.chartsy.kernel.ServiceManager.of(Indicator.class);
        template.addIndicator(indicators.get("Fractal Dimension"));

        return template;
    }

    private static Dimension applyDimensions(ChartFrame chartFrame, ExportOptions options) {
        Dimension size = options.getDimensions();
        if (!size.equals(chartFrame.getSize())) {
            chartFrame.setPreferredSize(size);
            chartFrame.setSize(size);
            layoutRecursively(chartFrame);
        }
        return size;
    }

    private static void layoutRecursively(Component component) {
        component.doLayout();
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                layoutRecursively(child);
            }
        }
    }

    private static void removeScreenshotChrome(ChartFrame chartFrame) {
        FrontEndSupport.getDefault().execute(() -> {
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

    static final class Services {
        private static final Map<String, ExportFormat<?>> FORMATS = loadFormats();
        private static final List<ExportFormat<?>> FILE_FORMATS = FORMATS.values().stream()
                .filter(f -> !f.fileExtensions().isEmpty())
                .toList();

        private Services() { }

        static ExportFormat<?> getByName(String name) {
            return FORMATS.get(normalizeName(name));
        }

        static List<ExportFormat<?>> fileFormats() {
            return FILE_FORMATS;
        }

        private static Map<String, ExportFormat<?>> loadFormats() {
            Map<String, ExportFormat<?>> formats = new HashMap<>();

            for (ExportFormat<?> format : Lookup.getDefault().lookupAll(ExportFormat.class)) {
                String name = normalizeName(Objects.requireNonNull(format.name(), "ExportFormat.name() is null"));
                ExportFormat<?> prev = formats.putIfAbsent(name, format);
                if (prev != null) {
                    throw new IllegalStateException("Duplicate ExportFormat service for name " + name + ": "
                            + prev.getClass().getName() + " and " + format.getClass().getName());
                }
            }
            return Map.copyOf(formats);
        }

        private static String normalizeName(String name) {
            return name.trim().toUpperCase(Locale.ROOT);
        }
    }

    @ServiceProvider(service = ExportFormat.class)
    public static final class PngExportFormat implements ExportFormat<ImageResource> {
        @Override
        public String name() {
            return "PNG";
        }

        @Override
        public List<String> fileExtensions() {
            return List.of(".png");
        }

        @Override
        public ImageResource export(DataProvider provider, CandleSeries dataset, ExportOptions options) {
            return new ImageResource(toPng(provider, dataset, options), dataset.getResource().symbol().name());
        }

        private static byte[] toPng(DataProvider provider, CandleSeries dataset, ExportOptions options) {
            ChartTemplate template = basicChartTemplate();
            Dimension size = options.getDimensions();
            ChartFrame chartFrame = createChartFrame(provider, dataset, template, size);
            return toPng(chartFrame, options);
        }

        private static byte[] toPng(ChartFrame chartFrame, ExportOptions options) {
            applyDimensions(chartFrame, options);
            BufferedImage image = FrontEndSupport.getDefault().paintComponent(chartFrame);
            try (var out = new ByteArrayOutputStream(128 * 1024)) {
                ImageIO.write(image, "png", out);
                return out.toByteArray();
            } catch (IOException e) {
                throw new IllegalStateException("Failed to encode chart PNG", e);
            }
        }
    }

    public abstract static class SvgExportFormat implements ExportFormat<ImageResource> {
        @Override
        public final String name() {
            return "SVG";
        }

        @Override
        public final List<String> fileExtensions() {
            return List.of(".svg");
        }

        @Override
        public final ImageResource export(DataProvider provider, CandleSeries dataset, ExportOptions options) {
            ChartTemplate template = basicChartTemplate();
            Dimension size = options.getDimensions();
            ChartFrame chartFrame = createChartFrame(provider, dataset, template, size);
            String svg = toSvg(chartFrame, options);
            return new ImageResource(svg.getBytes(StandardCharsets.UTF_8), dataset.getResource().symbol().name());
        }

        protected abstract String toSvg(ChartFrame chartFrame, ExportOptions options);
    }

    @ServiceProvider(service = ExportFormat.class)
    public static final class BatikSvgExportFormat extends SvgExportFormat {
        private static final int SVG_DECIMAL_PRECISION = 2;

        @Override
        protected String toSvg(ChartFrame chartFrame, ExportOptions options) {
            DOMImplementation impl = GenericDOMImplementation.getDOMImplementation();
            Document document = impl.createDocument(SVGConstants.SVG_NAMESPACE_URI, "svg", null);
            SVGGeneratorContext generatorContext = SVGGeneratorContext.createDefault(document);
            generatorContext.setComment(null);
            generatorContext.setPrecision(SVG_DECIMAL_PRECISION);

            SVGGraphics2D g = new SVGGraphics2D(generatorContext, false);
            Dimension size = applyDimensions(chartFrame, options);
            g.setSVGCanvasSize(size);
            FrontEndSupport.getDefault().paintComponent(chartFrame, g);

            try (StringWriter writer = new StringWriter(1024 * 32)) {
                SvgWriter.writeSvg(writer, g);
                return writer.toString();
            } catch (IOException e) {
                throw new IllegalStateException("Failed to render SVG output", e);
            }
        }
    }

    @ServiceProvider(service = ExportFormat.class)
    public static final class SvgzExportFormat implements ExportFormat<Resource> {
        @Override
        public String name() {
            return "SVGZ";
        }

        @Override
        public List<String> fileExtensions() {
            return List.of(".svgz");
        }

        @Override
        public Resource export(DataProvider provider, CandleSeries dataset, ExportOptions options) {
            ImageResource svg = ExportFormat.SVG.export(provider, dataset, options);
            try (var out = new ByteArrayOutputStream(64 * 1024);
                 var gz = new GZIPOutputStream(out)) {
                gz.write(svg.getContentAsByteArray());
                gz.finish();
                return new ByteArrayResource(out.toByteArray());
            } catch (IOException e) {
                throw new IllegalStateException("Failed to encode SVGZ output", e);
            }
        }
    }

    @ServiceProvider(service = ExportFormat.class)
    public static final class YamlExportFormat implements ExportFormat<ChartDescription> {
        @Override
        public String name() {
            return "YAML";
        }

        @Override
        public List<String> fileExtensions() {
            return List.of(".yaml", ".yml");
        }

        @Override
        public ChartDescription export(DataProvider provider, CandleSeries dataset, ExportOptions options) {
            return createChartDescription(dataset);
        }
    }

    @ServiceProvider(service = ExportFormat.class)
    public static final class ChartBundleExportFormat implements ExportFormat<ChartBundle> {
        @Override
        public String name() {
            return "BUNDLE";
        }

        @Override
        public ChartBundle export(DataProvider provider, CandleSeries dataset, ExportOptions options) {
            return new ChartBundle(
                    ExportFormat.PNG.export(provider, dataset, options),
                    ExportFormat.YAML.export(provider, dataset, options)
            );
        }
    }
}
