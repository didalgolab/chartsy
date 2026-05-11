package one.chartsy.ui.chart.internal.engine;

import one.chartsy.base.DoubleDataset;
import one.chartsy.charting.ChartDecoration;
import one.chartsy.charting.ChartRenderer;
import one.chartsy.charting.ChartRendererLegendItem;
import one.chartsy.ui.chart.LegendMarkerSpec;
import one.chartsy.ui.chart.PlotCoordinateSystem;
import one.chartsy.ui.chart.PlotDecoration;
import one.chartsy.ui.chart.PlotRenderContext;
import one.chartsy.ui.chart.PlotRenderTarget;
import one.chartsy.ui.chart.plot.Marker;
import one.chartsy.charting.renderers.SingleBarRenderer;
import one.chartsy.charting.renderers.SinglePolylineRenderer;
import one.chartsy.charting.renderers.SingleScatterRenderer;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.Rectangle;
import java.awt.Stroke;
import java.awt.geom.Point2D;
import java.util.List;
import java.util.function.IntFunction;

final class EnginePlotRenderTarget implements PlotRenderTarget {
    private final one.chartsy.charting.Chart chart;

    EnginePlotRenderTarget(one.chartsy.charting.Chart chart) {
        this.chart = chart;
    }

    @Override
    public void addLine(DoubleDataset values, Color color, Stroke stroke, PlotRenderContext context) {
        chart.addRenderer(
                EngineStudyAdapter.lineRenderer(context.legendName(),
                        EngineStudyAdapter.strokeStyle(color, stroke),
                        shouldRendererClaimLegend(context)),
                EngineSeriesAdapter.adapt(context.plotKey(), values, context.historicalSlots()));
    }

    @Override
    public void addHistogram(DoubleDataset values, Color positiveColor, Color negativeColor, PlotRenderContext context) {
        var split = EngineSeriesAdapter.splitBySign(context.plotKey(), values, context.historicalSlots());
        var positiveRenderer = EngineStudyAdapter.barRenderer(
                context.legendName(),
                EngineStudyAdapter.fillAndStrokeStyle(positiveColor, positiveColor, null),
                context.widthPercent(),
                shouldRendererClaimLegend(context));
        var negativeRenderer = EngineStudyAdapter.barRenderer(
                context.legendName(),
                EngineStudyAdapter.fillAndStrokeStyle(negativeColor, negativeColor, null),
                context.widthPercent(),
                false);
        negativeRenderer.setLegended(false);
        chart.addRenderer(positiveRenderer, split.positive());
        chart.addRenderer(negativeRenderer, split.negative());
    }

    @Override
    public void addBar(DoubleDataset values, Color color, PlotRenderContext context) {
        chart.addRenderer(
                EngineStudyAdapter.barRenderer(
                        context.legendName(),
                        EngineStudyAdapter.fillAndStrokeStyle(color, color, null),
                        context.widthPercent(),
                        shouldRendererClaimLegend(context)),
                EngineSeriesAdapter.adapt(context.plotKey(), values, context.historicalSlots()));
    }

    @Override
    public void addHorizontalLine(double value, Color color, Stroke stroke, PlotRenderContext context) {
        chart.addRenderer(
                EngineStudyAdapter.lineRenderer(
                        context.legendName(),
                        EngineStudyAdapter.strokeStyle(color, stroke),
                        shouldRendererClaimLegend(context)),
                EngineSeriesAdapter.constant(context.plotKey(), context.totalSlots(), value));
    }

    @Override
    public void addFill(DoubleDataset values, double floor, double ceiling, boolean upper, Paint paint, PlotRenderContext context) {
        chart.addDecoration(EngineStudyAdapter.fill(
                values,
                context.historicalSlots(),
                floor,
                ceiling,
                upper,
                paint,
                context.plotOrder()));
    }

    @Override
    public void addInsideFill(DoubleDataset upper, DoubleDataset lower, Paint paint, PlotRenderContext context) {
        chart.addDecoration(EngineStudyAdapter.insideFill(
                upper,
                context.historicalSlots(),
                lower,
                context.historicalSlots(),
                paint,
                context.plotOrder()));
    }

    @Override
    public void addScatter(DoubleDataset values, IntFunction<Marker> markerProvider, Color color, int markerSize, PlotRenderContext context) {
        Marker marker = markerProvider != null ? markerProvider.apply(0) : Marker.NONE;
        chart.addRenderer(
                EngineStudyAdapter.scatterRenderer(
                        context.legendName(),
                        EngineStudyAdapter.fillAndStrokeStyle(color, color, null),
                        toEngineMarker(marker),
                        markerSize,
                        shouldRendererClaimLegend(context)),
                EngineSeriesAdapter.adapt(context.plotKey(), values, context.historicalSlots()));
    }

    @Override
    public void addDecoration(PlotDecoration decoration, PlotRenderContext context) {
        chart.addDecoration(new PlotDecorationAdapter(decoration, context));
    }

    @Override
    public void addLegendEntry(String label, LegendMarkerSpec markerSpec) {
        ChartRenderer renderer = createLegendProxyRenderer(label, markerSpec);
        renderer.setLegendEntryProvider(() -> List.of(new ChartRendererLegendItem(renderer)));
        renderer.setLegended(true);
        chart.addRenderer(renderer);
    }

    private boolean shouldRendererClaimLegend(PlotRenderContext context) {
        return context.legended();
    }

    private static ChartRenderer createLegendProxyRenderer(String label, LegendMarkerSpec markerSpec) {
        ChartRenderer renderer = switch (markerSpec.kind()) {
            case LINE -> new SinglePolylineRenderer(EngineStudyAdapter.strokeStyle(
                    markerSpec.primaryColor(),
                    markerSpec.stroke()));
            case BAR -> new SingleBarRenderer(EngineStudyAdapter.fillAndStrokeStyle(
                    markerSpec.primaryColor(),
                    markerSpec.primaryColor(),
                    markerSpec.stroke()));
            case MARKER -> new SingleScatterRenderer(
                    toEngineMarker(markerSpec.marker()),
                    markerSpec.markerSize(),
                    EngineStudyAdapter.fillAndStrokeStyle(
                            markerSpec.primaryColor(),
                            markerSpec.primaryColor(),
                            markerSpec.stroke()));
        };
        renderer.setName((label == null || label.isBlank()) ? "Plot" : label);
        return renderer;
    }

    private static one.chartsy.charting.graphic.Marker toEngineMarker(Marker marker) {
        if (marker == null || marker == Marker.NONE)
            return one.chartsy.charting.graphic.Marker.NONE;
        return one.chartsy.charting.graphic.Marker.CIRCLE;
    }

    private static final class PlotDecorationAdapter extends ChartDecoration {
        private final PlotDecoration decoration;
        private final PlotRenderContext context;

        private PlotDecorationAdapter(PlotDecoration decoration, PlotRenderContext context) {
            this.decoration = decoration;
            this.context = context;
        }

        @Override
        public void draw(Graphics g) {
            if (!(g instanceof Graphics2D g2))
                return;
            decoration.paint(g2, context, new EnginePlotCoordinateSystem(getChart()));
        }
    }

    private static final class EnginePlotCoordinateSystem implements PlotCoordinateSystem {
        private final EngineCoordinateSystem delegate;

        private EnginePlotCoordinateSystem(one.chartsy.charting.Chart chart) {
            this.delegate = new EngineCoordinateSystem(chart);
        }

        @Override
        public Rectangle plotBounds() {
            return delegate.plotRect();
        }

        @Override
        public Point2D.Double toDisplay(double xValue, double yValue) {
            return delegate.toDisplay(xValue, yValue);
        }
    }
}
