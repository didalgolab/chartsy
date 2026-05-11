package one.chartsy.ui.chart.internal.engine;

import one.chartsy.base.DoubleDataset;
import one.chartsy.data.DoubleSeries;
import one.chartsy.charting.ChartDecoration;
import one.chartsy.charting.PlotStyle;
import one.chartsy.charting.data.DefaultDataSet;
import one.chartsy.charting.graphic.Marker;
import one.chartsy.charting.renderers.SingleBarRenderer;
import one.chartsy.charting.renderers.SinglePolylineRenderer;
import one.chartsy.charting.renderers.SingleScatterRenderer;
import one.chartsy.study.StudyColor;
import one.chartsy.study.StudyMarkerType;
import one.chartsy.ui.chart.internal.StudyParameterSupport;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.Rectangle;
import java.awt.Stroke;
import java.awt.geom.GeneralPath;
import java.awt.geom.Point2D;

final class EngineStudyAdapter {
    private EngineStudyAdapter() {
    }

    static SinglePolylineRenderer lineRenderer(String name, PlotStyle style, boolean legended) {
        SinglePolylineRenderer renderer = new SinglePolylineRenderer(style);
        renderer.setName(name);
        renderer.setMarker(Marker.NONE);
        renderer.setLegended(legended);
        return renderer;
    }

    static SingleBarRenderer barRenderer(String name, PlotStyle style, double widthPercent, boolean legended) {
        SingleBarRenderer renderer = new SingleBarRenderer(style, widthPercent);
        renderer.setName(name);
        renderer.setLegended(legended);
        return renderer;
    }

    static SingleScatterRenderer scatterRenderer(String name, PlotStyle style, Marker marker, int markerSize, boolean legended) {
        SingleScatterRenderer renderer = new SingleScatterRenderer(marker, markerSize, style);
        renderer.setName(name);
        renderer.setMarkerSize(markerSize);
        renderer.setLegended(legended);
        return renderer;
    }

    static PlotStyle strokeStyle(Color color, Stroke stroke) {
        Stroke effectiveStroke = (stroke != null) ? stroke : one.chartsy.charting.PlotStyle.DEFAULT_STROKE;
        return new PlotStyle(effectiveStroke, color).setFillOn(false);
    }

    static PlotStyle fillStyle(Paint fillPaint) {
        return new PlotStyle(fillPaint).setStrokeOn(false);
    }

    static PlotStyle fillAndStrokeStyle(Color strokeColor, Paint fillPaint, Stroke stroke) {
        Stroke effectiveStroke = (stroke != null) ? stroke : one.chartsy.charting.PlotStyle.DEFAULT_STROKE;
        return new PlotStyle(effectiveStroke, strokeColor, fillPaint);
    }

    static ChartDecoration fill(DoubleDataset values,
                                int historicalSlots,
                                double floor,
                                double ceiling,
                                boolean upper,
                                Paint paint,
                                int plotOrder) {
        var decoration = new FillDecoration(EngineSeriesAdapter.adapt("Fill", values, historicalSlots), floor, ceiling, upper, fillStyle(paint));
        decoration.setDrawOrder(-1_000 + plotOrder);
        return decoration;
    }

    static ChartDecoration insideFill(DoubleDataset upper,
                                      int upperHistoricalSlots,
                                      DoubleDataset lower,
                                      int lowerHistoricalSlots,
                                      Paint paint,
                                      int plotOrder) {
        var decoration = new InsideFillDecoration(
                EngineSeriesAdapter.adapt("Upper", upper, upperHistoricalSlots),
                EngineSeriesAdapter.adapt("Lower", lower, lowerHistoricalSlots),
                fillStyle(paint));
        decoration.setDrawOrder(-1_000 + plotOrder);
        return decoration;
    }

    private static Color colorOrDefault(StudyColor color) {
        return color != null ? StudyParameterSupport.toAwtColor(color) : Color.BLACK;
    }

    private static Marker marker(StudyMarkerType markerType) {
        return switch (markerType) {
            case NONE -> Marker.NONE;
            case CIRCLE -> Marker.CIRCLE;
            case SQUARE -> Marker.SQUARE;
            case TRIANGLE_UP, TRIANGLE_DOWN -> Marker.TRIANGLE;
            case DIAMOND -> Marker.DIAMOND;
            case CROSS -> Marker.CROSS;
        };
    }

    private abstract static class SeriesDecoration extends ChartDecoration {
        protected final PlotStyle style;

        protected SeriesDecoration(PlotStyle style) {
            this.style = style;
        }

        protected final EngineCoordinateSystem coordinateSystem() {
            return new EngineCoordinateSystem(getChart());
        }
    }

    private static final class FillDecoration extends SeriesDecoration {
        private final DefaultDataSet values;
        private final double floor;
        private final double ceiling;
        private final boolean upper;

        private FillDecoration(DefaultDataSet values, double floor, double ceiling, boolean upper, PlotStyle style) {
            super(style);
            this.values = values;
            this.floor = floor;
            this.ceiling = ceiling;
            this.upper = upper;
        }

        @Override
        public void draw(Graphics g) {
            if (!(g instanceof Graphics2D g2) || values.size() < 2)
                return;

            double min = Math.min(floor, ceiling);
            double max = Math.max(floor, ceiling);
            if (upper)
                max = Math.max(max, getChart().getYAxis(0).getVisibleMax());
            else {
                min = Math.max(floor, ceiling);
                max = Math.min(floor, ceiling);
            }

            double boundaryValue = min;
            var fillRange = new one.chartsy.core.Range.Builder().add(min).add(max).toRange();
            if (fillRange.isEmpty())
                return;
            var coordinateSystem = coordinateSystem();
            Rectangle plotRect = coordinateSystem.plotRect();
            if (plotRect == null || plotRect.isEmpty())
                return;

            Graphics2D fillGraphics = (Graphics2D) g2.create();
            try {
                fillGraphics.clip(plotRect);
                for (int index = 1; index < values.size(); index++) {
                    double value1 = values.getYData(index - 1);
                    double value2 = values.getYData(index);
                    if (Double.isNaN(value1) || Double.isNaN(value2))
                        continue;

                    SegmentClip clippedSegment = clipSegment(value1, value2, fillRange);
                    if (clippedSegment == null)
                        continue;

                    double x1 = values.getXData(index - 1);
                    double x2 = values.getXData(index);
                    double clippedX1 = interpolate(x1, x2, clippedSegment.startT());
                    double clippedX2 = interpolate(x1, x2, clippedSegment.endT());
                    double clippedValue1 = interpolate(value1, value2, clippedSegment.startT());
                    double clippedValue2 = interpolate(value1, value2, clippedSegment.endT());

                    Point2D.Double upperStart = coordinateSystem.toDisplay(clippedX1, clippedValue1);
                    Point2D.Double upperEnd = coordinateSystem.toDisplay(clippedX2, clippedValue2);
                    Point2D.Double lowerEnd = coordinateSystem.toDisplay(clippedX2, boundaryValue);
                    Point2D.Double lowerStart = coordinateSystem.toDisplay(clippedX1, boundaryValue);

                    GeneralPath path = new GeneralPath();
                    path.moveTo((float) lowerStart.x, (float) lowerStart.y);
                    path.lineTo((float) upperStart.x, (float) upperStart.y);
                    path.lineTo((float) upperEnd.x, (float) upperEnd.y);
                    path.lineTo((float) lowerEnd.x, (float) lowerEnd.y);
                    path.closePath();
                    style.fill(fillGraphics, path);
                }
            } finally {
                fillGraphics.dispose();
            }
        }
    }

    private static final class InsideFillDecoration extends SeriesDecoration {
        private final DefaultDataSet upper;
        private final DefaultDataSet lower;

        private InsideFillDecoration(DefaultDataSet upper, DefaultDataSet lower, PlotStyle style) {
            super(style);
            this.upper = upper;
            this.lower = lower;
        }

        @Override
        public void draw(Graphics g) {
            if (!(g instanceof Graphics2D g2))
                return;

            var coordinateSystem = coordinateSystem();
            Rectangle plotRect = coordinateSystem.plotRect();
            if (plotRect == null || plotRect.isEmpty())
                return;
            Point2D.Double previousUpper = null;
            Point2D.Double previousLower = null;
            int size = Math.min(upper.size(), lower.size());
            Graphics2D fillGraphics = (Graphics2D) g2.create();
            try {
                fillGraphics.clip(plotRect);
                for (int index = 0; index < size; index++) {
                    double upperValue = upper.getYData(index);
                    double lowerValue = lower.getYData(index);
                    if (Double.isNaN(upperValue) || Double.isNaN(lowerValue)) {
                        previousUpper = null;
                        previousLower = null;
                        continue;
                    }

                    Point2D.Double currentUpper = pointAt(coordinateSystem, upper, index, upperValue);
                    Point2D.Double currentLower = pointAt(coordinateSystem, lower, index, lowerValue);
                    if (previousUpper != null && previousLower != null) {
                        GeneralPath path = new GeneralPath();
                        path.moveTo((float) previousUpper.x, (float) previousUpper.y);
                        path.lineTo((float) currentUpper.x, (float) currentUpper.y);
                        path.lineTo((float) currentLower.x, (float) currentLower.y);
                        path.lineTo((float) previousLower.x, (float) previousLower.y);
                        path.closePath();
                        style.fill(fillGraphics, path);
                    }

                    previousUpper = currentUpper;
                    previousLower = currentLower;
                }
            } finally {
                fillGraphics.dispose();
            }
        }
    }

    private static Point2D.Double pointAt(EngineCoordinateSystem coordinateSystem, DefaultDataSet dataSet, int index, double value) {
        return coordinateSystem.toDisplay(dataSet.getXData(index), value);
    }

    private static double interpolate(double start, double end, double t) {
        return start + (end - start) * t;
    }

    private static SegmentClip clipSegment(double value1, double value2, one.chartsy.core.Range fillRange) {
        if (fillRange.isEmpty())
            return null;
        if (Double.compare(value1, value2) == 0) {
            if (!fillRange.contains(value1))
                return null;
            return new SegmentClip(0.0, 1.0);
        }

        double atMin = (fillRange.min() - value1) / (value2 - value1);
        double atMax = (fillRange.max() - value1) / (value2 - value1);
        double startT = Math.max(0.0, Math.min(atMin, atMax));
        double endT = Math.min(1.0, Math.max(atMin, atMax));
        if (endT < 0.0 || startT > 1.0)
            return null;
        startT = Math.max(0.0, startT);
        endT = Math.min(1.0, endT);
        if (startT > endT)
            return null;
        return new SegmentClip(startT, endT);
    }

    private record SegmentClip(double startT, double endT) {
    }
}
