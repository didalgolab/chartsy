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
import org.apache.batik.dom.GenericDOMImplementation;
import org.apache.batik.svggen.SVGGeneratorContext;
import org.apache.batik.svggen.SVGGraphics2D;
import org.apache.batik.util.SVGConstants;
import org.openide.util.Lookup;
import org.openide.util.lookup.ServiceProvider;
import org.w3c.dom.DOMImplementation;
import org.w3c.dom.Document;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import javax.swing.JLayer;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.LayoutManager;

public interface ChartExporter {

    enum Format {
        PNG,
        SVG;

        public static Format forFileName(Path path) {
            String name = (path.getFileName() != null) ? path.getFileName().toString() : path.toString();
            int dot = name.lastIndexOf('.');
            if (dot < 0)
                throw new IllegalArgumentException("File extension missing for chart export: " + path);
            String ext = name.substring(dot + 1).toLowerCase(Locale.ROOT);
            return switch (ext) {
                case "png" -> PNG;
                case "svg", "svgz" -> SVG;
                default -> throw new IllegalArgumentException("Unsupported chart export extension: " + ext);
            };
        }
    }

    Format format();

    void export(Path output, ChartFrame chartFrame, ExportOptions options) throws IOException;

    static void export(Path output, DataProvider provider, DataQuery<Candle> query) throws IOException {
        export(output, provider, query, ExportOptions.DEFAULT);
    }

    static void export(Path output, DataProvider provider, SymbolResource<Candle> resource) throws IOException {
        export(output, provider, resource, ExportOptions.DEFAULT);
    }

    static void export(Path output, DataProvider provider, SymbolIdentity symbol) throws IOException {
        export(output, provider, symbol, ExportOptions.DEFAULT);
    }

    static void export(Path output,
                       DataProvider provider,
                       DataQuery<Candle> query,
                       ExportOptions options) throws IOException {
        ChartFrame chartFrame = createExportChartFrame(provider, query, options);
        ChartExporter exporter = forFormat(resolveFormat(output, options));
        exporter.export(output, chartFrame, options);
    }

    static void export(Path output,
                       DataProvider provider,
                       SymbolResource<Candle> resource,
                       ExportOptions options) throws IOException {
        export(output, provider, DataQuery.of(resource), options);
    }

    static void export(Path output,
                       DataProvider provider,
                       SymbolIdentity symbol,
                       ExportOptions options) throws IOException {
        SymbolResource<Candle> resource = SymbolResource.of(symbol, TimeFrame.Period.DAILY).withDataType(Candle.class);
        export(output, provider, resource, options);
    }

    static ChartExporter forFormat(Format format) {
        return Holder.EXPORTERS.get(format);
    }

    final class Holder {
        private static final Map<Format, ChartExporter> EXPORTERS = loadExporters();

        private static Map<Format, ChartExporter> loadExporters() {
            EnumMap<Format, ChartExporter> exporters = new EnumMap<>(Format.class);
            for (ChartExporter exporter : Lookup.getDefault().lookupAll(ChartExporter.class)) {
                Format format = exporter.format();
                if (exporters.containsKey(format)) {
                    throw new IllegalStateException("Duplicate chart exporter for format " + format + ": "
                            + exporter.getClass().getName());
                }
                exporters.put(format, exporter);
            }
            for (Format format : Format.values()) {
                if (!exporters.containsKey(format)) {
                    throw new IllegalStateException("Chart exporter for format " + format
                            + " not found. Available: " + exporters.keySet());
                }
            }
            return Map.copyOf(exporters);
        }
    }

    @ServiceProvider(service = ChartExporter.class)
    final class ToPNG implements ChartExporter {

        @Override
        public Format format() {
            return Format.PNG;
        }

        @Override
        public void export(Path output, ChartFrame chartFrame, ExportOptions options) throws IOException {
            applyDimensions(chartFrame, options);
            BufferedImage image = FrontEndSupport.getDefault().paintComponent(chartFrame);
            ImageIO.write(image, "png", output.toFile());
        }
    }

    @ServiceProvider(service = ChartExporter.class)
    final class ToSVG implements ChartExporter {
        private static final int SVG_DECIMAL_PRECISION = 2;

        @Override
        public Format format() {
            return Format.SVG;
        }

        @Override
        public void export(Path output, ChartFrame chartFrame, ExportOptions options) throws IOException {
            DOMImplementation impl = GenericDOMImplementation.getDOMImplementation();
            Document document = impl.createDocument(SVGConstants.SVG_NAMESPACE_URI, "svg", null);
            SVGGeneratorContext generatorContext = SVGGeneratorContext.createDefault(document);
            generatorContext.setComment(null);
            generatorContext.setPrecision(SVG_DECIMAL_PRECISION);

            SVGGraphics2D g = new SVGGraphics2D(generatorContext, false);
            Dimension size = applyDimensions(chartFrame, options);
            g.setSVGCanvasSize(size);
            FrontEndSupport.getDefault().paintComponent(chartFrame, g);
            SvgWriter.writeSvg(output, g);
        }
    }

    static ChartFrame createExportChartFrame(DataProvider provider,
                                             DataQuery<Candle> query,
                                             ExportOptions options) {
        CandleSeries dataset = loadCandles(provider, query);
        ChartTemplate template = basicChartTemplate();
        Dimension size = options.getDimensions();
        ChartFrame chartFrame = createChartFrame(provider, dataset, template, size);
        removeScreenshotChrome(chartFrame);
        return chartFrame;
    }

    static CandleSeries loadCandles(DataProvider provider, DataQuery<Candle> query) {
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
            chartData.setChart(java.util.Objects.requireNonNull(template.getChart(), "template.chart"));

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
        template.setChart(one.chartsy.kernel.ServiceManager.of(Chart.class).get("Candle Stick"));

        var overlays = one.chartsy.kernel.ServiceManager.of(Overlay.class);
        template.addOverlay(overlays.get("FRAMA, Leading"));
        template.addOverlay(overlays.get("FRAMA, Trailing"));
        template.addOverlay(overlays.get("Sfora"));
        template.addOverlay(overlays.get("Volume"));
        //template.addOverlay(overlays.get("Sentiment Bands"));

        var indicators = one.chartsy.kernel.ServiceManager.of(Indicator.class);
        template.addIndicator(indicators.get("Fractal Dimension"));

        return template;
    }

    private static Format resolveFormat(Path output, ExportOptions options) {
        return options.getFormat().orElseGet(() -> Format.forFileName(output));
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

    private static void layoutRecursively(java.awt.Component component) {
        component.doLayout();
        if (component instanceof java.awt.Container container) {
            for (java.awt.Component child : container.getComponents()) {
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
}
