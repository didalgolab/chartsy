package one.chartsy.ui.chart.internal;

import one.chartsy.base.DoubleDataset;
import one.chartsy.core.Range;
import one.chartsy.data.CandleSeries;
import one.chartsy.data.DoubleSeries;
import one.chartsy.study.StudyAxisDescriptor;
import one.chartsy.study.StudyColor;
import one.chartsy.study.StudyDescriptor;
import one.chartsy.study.StudyMarkerType;
import one.chartsy.study.StudyPlotDefinition;
import one.chartsy.study.StudyPlotDescriptor;
import one.chartsy.study.StudyPlotType;
import one.chartsy.study.StudyPresentationBuilder;
import one.chartsy.study.StudyPresentationContext;
import one.chartsy.study.StudyPresentationPlan;
import one.chartsy.ui.chart.ChartContext;
import one.chartsy.ui.chart.Plot;
import one.chartsy.ui.chart.data.VisualRange;
import one.chartsy.ui.chart.plot.BarPlot;
import one.chartsy.ui.chart.plot.FillPlot;
import one.chartsy.ui.chart.plot.HistogramPlot;
import one.chartsy.ui.chart.plot.HorizontalLinePlot;
import one.chartsy.ui.chart.plot.InsideFillPlot;
import one.chartsy.ui.chart.plot.LinePlot;
import one.chartsy.ui.chart.plot.Marker;
import one.chartsy.ui.chart.plot.ShapePlot;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Stroke;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SequencedMap;

public final class StudyPresentationFactory {
    private StudyPresentationFactory() {
    }

    public static StudyPresentationPlan createPlan(StudyDescriptor descriptor,
                                                   CandleSeries dataset,
                                                   SequencedMap<String, Object> uiParameters,
                                                   SequencedMap<String, DoubleSeries> outputs) {
        var neutralParameters = StudyParameterSupport.toPresentationParameters(descriptor, uiParameters);
        var neutralOutputs = new LinkedHashMap<String, Object>();
        outputs.forEach(neutralOutputs::put);

        StudyPresentationPlan defaultPlan = createDefaultPlan(descriptor, neutralParameters, neutralOutputs);
        if (!descriptor.hasCustomBuilder())
            return defaultPlan;

        StudyPresentationBuilder builder = instantiateBuilder(descriptor);
        StudyPresentationPlan plan = builder.build(
                StudyPresentationContext.of(descriptor, dataset, neutralParameters, neutralOutputs),
                defaultPlan
        );
        return plan != null ? plan : defaultPlan;
    }

    public static List<PlotEntry> createPlots(StudyPresentationPlan plan) {
        List<PlotEntry> plots = new ArrayList<>();
        Map<String, Integer> labelCounts = new LinkedHashMap<>();
        for (StudyPlotDefinition plotDefinition : plan.plots()) {
            if (!plotDefinition.visible())
                continue;

            String baseKey = plotDefinition.label();
            int count = labelCounts.merge(baseKey, 1, Integer::sum);
            String key = count == 1 ? baseKey : baseKey + " [" + count + ']';
            plots.add(new PlotEntry(key, toPlot(plotDefinition)));
        }
        return List.copyOf(plots);
    }

    public static VisualRange applyAxis(VisualRange computedRange, StudyAxisDescriptor axis) {
        Range range = mergeAxis(computedRange == null ? null : computedRange.range(), axis);
        boolean logarithmic = (computedRange != null && computedRange.isLogarithmic()) || (axis != null && axis.logarithmic());
        return new VisualRange(range, logarithmic);
    }

    public static Range applyAxis(Range computedRange, StudyAxisDescriptor axis) {
        return mergeAxis(computedRange, axis);
    }

    private static StudyPresentationPlan createDefaultPlan(StudyDescriptor descriptor,
                                                           Map<String, Object> parameters,
                                                           Map<String, Object> outputs) {
        List<StudyPlotDefinition> plots = new ArrayList<>();
        for (StudyPlotDescriptor descriptorPlot : descriptor.plots()) {
            boolean visible = resolveVisibility(descriptorPlot, parameters);
            StudyColor primaryColor = resolveColor(descriptorPlot.colorParameter(), parameters);
            StudyColor secondaryColor = resolveColor(descriptorPlot.secondaryColorParameter(), parameters);
            String stroke = resolveStroke(descriptorPlot.strokeParameter(), parameters);
            Object values = descriptorPlot.outputId().isBlank() ? null : outputs.get(descriptorPlot.outputId());
            Object secondaryValues = descriptorPlot.secondaryOutputId().isBlank() ? null : outputs.get(descriptorPlot.secondaryOutputId());

            plots.add(switch (descriptorPlot.type()) {
                case LINE -> StudyPlotDefinition.line(descriptorPlot.id(), descriptorPlot.label(), descriptorPlot.order(), values,
                        primaryColor, stroke, visible);
                case HISTOGRAM -> StudyPlotDefinition.histogram(descriptorPlot.id(), descriptorPlot.label(), descriptorPlot.order(), values,
                        primaryColor, secondaryColor, visible);
                case BAR -> StudyPlotDefinition.bar(descriptorPlot.id(), descriptorPlot.label(), descriptorPlot.order(), values,
                        primaryColor, visible);
                case HORIZONTAL_LINE -> StudyPlotDefinition.horizontal(descriptorPlot.id(), descriptorPlot.label(), descriptorPlot.order(), descriptorPlot.value1(),
                        primaryColor, stroke, visible);
                case FILL -> StudyPlotDefinition.fill(descriptorPlot.id(), descriptorPlot.label(), descriptorPlot.order(), values,
                        descriptorPlot.value1(), descriptorPlot.value2(), descriptorPlot.upper(), primaryColor, visible);
                case INSIDE_FILL -> StudyPlotDefinition.insideFill(descriptorPlot.id(), descriptorPlot.label(), descriptorPlot.order(), values,
                        secondaryValues, primaryColor, visible);
                case SHAPE -> StudyPlotDefinition.shape(descriptorPlot.id(), descriptorPlot.label(), descriptorPlot.order(), values,
                        primaryColor, visible, descriptorPlot.marker());
            });
        }
        return new StudyPresentationPlan(descriptor.axis(), plots);
    }

    private static StudyPresentationBuilder instantiateBuilder(StudyDescriptor descriptor) {
        try {
            return descriptor.builderType().getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Cannot instantiate study presentation builder for " + descriptor.id(), ex);
        }
    }

    private static boolean resolveVisibility(StudyPlotDescriptor plot, Map<String, Object> parameters) {
        if (plot.visibleParameter().isBlank())
            return true;
        Object visible = parameters.get(plot.visibleParameter());
        return !(visible instanceof Boolean bool) || bool;
    }

    private static StudyColor resolveColor(String parameterId, Map<String, Object> parameters) {
        if (parameterId == null || parameterId.isBlank())
            return null;
        Object value = parameters.get(parameterId);
        return switch (value) {
            case null -> null;
            case StudyColor color -> color;
            case Color color -> StudyParameterSupport.toStudyColor(color);
            case String text -> StudyColor.parse(text);
            default -> throw new IllegalArgumentException("Unsupported plot color value: " + value.getClass().getName());
        };
    }

    private static String resolveStroke(String parameterId, Map<String, Object> parameters) {
        if (parameterId == null || parameterId.isBlank())
            return "";
        Object value = parameters.get(parameterId);
        return value == null ? "" : StudyParameterSupport.toStrokeName(value);
    }

    private static Plot toPlot(StudyPlotDefinition plot) {
        Color primaryColor = plot.primaryColor() != null ? StudyParameterSupport.toAwtColor(plot.primaryColor()) : Color.BLACK;
        Color secondaryColor = plot.secondaryColor() != null ? StudyParameterSupport.toAwtColor(plot.secondaryColor()) : primaryColor;
        Stroke stroke = plot.stroke() == null || plot.stroke().isBlank() ? null : StudyParameterSupport.toStroke(plot.stroke());

        return switch (plot.type()) {
            case LINE -> new LinePlot(asDoubleSeries(plot.values(), plot.id()), primaryColor, stroke);
            case HISTOGRAM -> new HistogramPlot(asDoubleDataset(plot.values(), plot.id()), primaryColor, secondaryColor);
            case BAR -> new BarPlot(asDoubleDataset(plot.values(), plot.id()), primaryColor);
            case HORIZONTAL_LINE -> new HorizontalLinePlot(plot.value1(), primaryColor, stroke);
            case FILL -> new FillPlot(asDoubleSeries(plot.values(), plot.id()), plot.value1(), plot.value2(), plot.upper(), primaryColor);
            case INSIDE_FILL -> new InsideFillPlot(asDoubleDataset(plot.values(), plot.id()), asDoubleDataset(plot.secondaryValues(), plot.id() + "Secondary"), primaryColor);
            case SHAPE -> new ShapePlot(ignored -> marker(plot.marker()), asDoubleDataset(plot.values(), plot.id()), primaryColor);
        };
    }

    private static DoubleSeries asDoubleSeries(Object value, String plotId) {
        if (value instanceof DoubleSeries series)
            return series;
        throw new IllegalArgumentException("Plot " + plotId + " requires DoubleSeries values");
    }

    private static DoubleDataset asDoubleDataset(Object value, String plotId) {
        return switch (value) {
            case DoubleSeries series -> series.values();
            case DoubleDataset dataset -> dataset;
            default -> throw new IllegalArgumentException("Plot " + plotId + " requires DoubleDataset values");
        };
    }

    private static Marker marker(StudyMarkerType markerType) {
        return switch (markerType) {
            case NONE -> Marker.NONE;
            case CIRCLE -> (g, x, y, size, style) -> {
                g.setPaint(style);
                g.fillOval(x - size / 2, y - size / 2, size, size);
            };
            case SQUARE -> (g, x, y, size, style) -> {
                g.setPaint(style);
                g.fillRect(x - size / 2, y - size / 2, size, size);
            };
            case TRIANGLE_UP -> polygonMarker(0, -1, -1, 1, 1, 1);
            case TRIANGLE_DOWN -> polygonMarker(-1, -1, 1, -1, 0, 1);
            case DIAMOND -> polygonMarker(0, -1, -1, 0, 0, 1, 1, 0);
            case CROSS -> (g, x, y, size, style) -> {
                Graphics2D g2 = (Graphics2D) g;
                Stroke oldStroke = g2.getStroke();
                g2.setPaint(style);
                g2.setStroke(new BasicStroke(Math.max(1f, size / 6f)));
                int radius = size / 2;
                g2.drawLine(x - radius, y - radius, x + radius, y + radius);
                g2.drawLine(x - radius, y + radius, x + radius, y - radius);
                g2.setStroke(oldStroke);
            };
        };
    }

    private static Marker polygonMarker(int... normalizedCoordinates) {
        return (g, x, y, size, style) -> {
            g.setPaint(style);
            int half = Math.max(2, size / 2);
            Polygon polygon = new Polygon();
            for (int i = 0; i < normalizedCoordinates.length; i += 2) {
                polygon.addPoint(x + normalizedCoordinates[i] * half, y + normalizedCoordinates[i + 1] * half);
            }
            g.fillPolygon(polygon);
        };
    }

    private static Range mergeAxis(Range computedRange, StudyAxisDescriptor axis) {
        Range.Builder builder = new Range.Builder();
        if (computedRange != null && !computedRange.isEmpty()) {
            if (Double.isFinite(computedRange.min()))
                builder.add(computedRange.min());
            if (Double.isFinite(computedRange.max()))
                builder.add(computedRange.max());
        }
        if (axis != null) {
            if (!Double.isNaN(axis.min()))
                builder.add(axis.min());
            if (!Double.isNaN(axis.max()))
                builder.add(axis.max());
        }
        Range merged = builder.toRange();
        return merged.isEmpty() && computedRange != null ? computedRange : merged;
    }

    public record PlotEntry(String key, Plot plot) {
        public PlotEntry {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(plot, "plot");
        }
    }
}
