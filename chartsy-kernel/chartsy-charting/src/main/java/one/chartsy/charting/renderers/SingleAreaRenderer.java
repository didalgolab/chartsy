package one.chartsy.charting.renderers;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Paint;
import java.awt.Rectangle;
import java.awt.Stroke;

import one.chartsy.charting.Chart;
import one.chartsy.charting.ChartRenderer;
import one.chartsy.charting.LegendEntry;
import one.chartsy.charting.PlotStyle;
import one.chartsy.charting.data.DataSet;
import one.chartsy.charting.renderers.internal.StackedDataSet;
import one.chartsy.charting.util.ColorUtil;

/// Single-series renderer that fills the area under a polyline.
///
/// For non-radar charts the fill closes against the chart's x-axis crossing, clamped to the
/// visible range. Radar charts are handled differently: filled segments close toward the plot
/// center so the generated polygon remains valid in radial coordinates.
///
/// When the parent renderer implements [SuperimposedRenderer] and automatic transparency is
/// enabled, auto-generated fill colors are rendered at `50%` alpha so overlapping filled children
/// remain distinguishable.
public class SingleAreaRenderer extends SinglePolylineRenderer {

    /// Polyline item variant that carries the closure baseline used by filled non-radar segments.
    ///
    /// The baseline is captured when the item is created so later polygon post-processing can add
    /// the closing points without recomputing axis state.
    class AreaItem extends SinglePolylineRenderer.PolyItem {
        double baselineY;

        AreaItem(int pointCapacity, int drawMode) {
            super(pointCapacity, drawMode);
            baselineY = getAxisBaselineY();
        }

        @Override
        public void draw(Graphics g, PlotStyle style) {
            if (getChart().getType() == Chart.RADAR) {
                if (super.drawMode == DRAW_MODE_POLYLINE) {
                    if (style.isFillOn())
                        style.fillPolygon(g, super.getXValues(), super.getYValues(), super.size());
                    if (style.isStrokeOn())
                        style.drawPolyline(g, super.getXValues(), super.getYValues(), super.size());
                } else if (super.drawMode == DRAW_MODE_POLYGON && style.isFillOn())
                    style.fillPolygon(g, super.getXValues(), super.getYValues(), super.size());
                else if (super.drawMode == DRAW_MODE_OUTLINE && style.isStrokeOn())
                    style.drawPolyline(g, super.getXValues(), super.getYValues(), super.size());
                return;
            }

            if (super.drawMode == DRAW_MODE_POLYLINE) {
                if (style.isFillOn())
                    style.plotPoints(g, super.getXValues(), super.getYValues(), super.size());
                else if (style.isStrokeOn())
                    style.drawPolyline(g, super.getXValues(), super.getYValues(), super.size());
            } else if (super.drawMode == DRAW_MODE_POLYGON && style.isFillOn())
                style.fillPolygon(g, super.getXValues(), super.getYValues(), super.size());
            else if (super.drawMode == DRAW_MODE_OUTLINE && style.isStrokeOn())
                style.drawPolyline(g, super.getXValues(), super.getYValues(), super.size());
        }
    }

    /// Polyline post-processor that converts connected line segments into fillable area polygons.
    ///
    /// Non-radar charts close each segment against the captured baseline. Radar charts instead
    /// close the segment toward the plot center, or back to its start point when the segment
    /// already spans the center line.
    class AreaItemAction extends SinglePolylineRenderer.PolyItemAction {

        AreaItemAction(SingleChartRenderer.ItemAction delegate) {
            super(delegate);
        }

        @Override
        protected void postProcessPolyItem(SingleChartRenderer.Points points, SinglePolylineRenderer.PolyItem item,
                                           PlotStyle style) {
            if (getChart().getType() != Chart.RADAR || !style.isFillOn())
                return;

            Rectangle plotRect = getPlotRect();
            double centerX = plotRect.x + plotRect.width / 2.0;
            double centerY = plotRect.y + plotRect.height / 2.0;

            if (runsAlongRadarCenterLine(item, centerX, centerY)) {
                if (!skipClosingStartPointOnCenterLine(item))
                    item.add(item.getX(0), item.getY(0));
                return;
            }

            if (item.lastInDataSet) {
                item.add(0, new double[]{centerX}, new double[]{centerY}, 1);
                if (!skipClosingStartPointAfterPrependingCenter(item))
                    item.add(item.getX(0), item.getY(0));
                return;
            }

            item.add(centerX, centerY);
            if (!skipClosingStartPointAfterAppendingCenter(item))
                item.add(item.getX(0), item.getY(0));
        }

        @Override
        protected void processPolyItem(SingleChartRenderer.Points points, int pointIndex, SinglePolylineRenderer.PolyItem item,
                                       PlotStyle style) {
            if (getChart().getType() != Chart.RADAR)
                if (style.isFillOn()) {
                    double baselineY = ((SingleAreaRenderer.AreaItem) item).baselineY;
                    if (item.drawMode != PolyItem.DRAW_MODE_OUTLINE) {
                        item.add(item.getX(item.size() - 1), baselineY);
                        item.add(item.getX(0), baselineY);
                        item.add(item.getX(0), item.getY(0));
                    } else {
                        boolean firstInSeries = item.firstInSeries;
                        boolean lastInSeries = item.lastInSeries;
                        if (!firstInSeries) {
                            if (lastInSeries) {
                                item.add(item.getX(item.size() - 1), baselineY);
                                item.add(item.getX(0), baselineY);
                            } else {
                                double startX = item.getX(0);
                                double endX = item.getX(item.size() - 1);
                                super.processConnectedPolyItem(points, pointIndex, item, style);
                                item.setSize(2);
                                item.set(0, endX, baselineY);
                                item.set(1, startX, baselineY);
                            }
                        } else if (lastInSeries) {
                            item.add(item.getX(item.size() - 1), baselineY);
                            item.add(item.getX(0), baselineY);
                            item.add(item.getX(0), item.getY(0));
                        } else {
                            double[] baselineXValues = new double[2];
                            baselineXValues[0] = item.getX(item.size() - 1);
                            baselineXValues[1] = item.getX(0);
                            double[] baselineYValues = new double[2];
                            baselineYValues[0] = baselineY;
                            baselineYValues[1] = baselineY;
                            item.add(0, baselineXValues, baselineYValues, 2);
                        }
                    }
                }
            super.processConnectedPolyItem(points, pointIndex, item, style);
        }

        private static boolean runsAlongRadarCenterLine(SinglePolylineRenderer.PolyItem item, double centerX, double centerY) {
            double startX = item.getX(0) - centerX;
            double startY = item.getY(0) - centerY;
            double endX = item.getX(item.size() - 1) - centerX;
            double endY = item.getY(item.size() - 1) - centerY;
            double crossProduct = startX * endY - endX * startY;
            double tolerance = 1.0E-12 * (startX * startX + startY * startY) * (endX * endX + endY * endY);
            return crossProduct * crossProduct <= tolerance;
        }

        private static boolean skipClosingStartPointAfterAppendingCenter(SinglePolylineRenderer.PolyItem item) {
            return item.firstInDataSet && item.skipLastRayStroke;
        }

        private static boolean skipClosingStartPointAfterPrependingCenter(SinglePolylineRenderer.PolyItem item) {
            return item.lastInDataSet && item.skipFirstRayStroke;
        }

        private static boolean skipClosingStartPointOnCenterLine(SinglePolylineRenderer.PolyItem item) {
            return skipClosingStartPointAfterPrependingCenter(item) || skipClosingStartPointAfterAppendingCenter(item);
        }
    }

    static {
        ChartRenderer.register("SingleArea", SingleAreaRenderer.class);
    }

    /// Creates an area renderer that uses the inherited default polyline style.
    public SingleAreaRenderer() {
    }

    /// Creates an area renderer with an explicit base style.
    public SingleAreaRenderer(PlotStyle style) {
        super(style);
    }

    @Override
    SinglePolylineRenderer.PolyItem createPolyItem(int pointCapacity, int drawMode) {
        return new SingleAreaRenderer.AreaItem(pointCapacity, drawMode);
    }

    @Override
    PlotStyle createStyle(Paint fillPaint, Paint strokePaint, Stroke stroke) {
        PlotStyle resolvedStyle = super.createStyle(fillPaint, strokePaint, stroke);
        ChartRenderer parent = super.getParent();
        if (resolvedStyle != null)
            if (isFilled())
                if (parent instanceof SuperimposedRenderer superimposedParent)
                    if (superimposedParent.isSuperimposed())
                        if (superimposedParent.isAutoTransparency()) {
                            Color fillColor = resolvedStyle.getFillColor();
                            if (fillColor != null)
                                resolvedStyle = resolvedStyle.setFillPaint(ColorUtil.setAlpha(fillColor, 0.5f));
                        }
        return resolvedStyle;
    }

    @Override
    SinglePolylineRenderer.PolyItemAction createPolyItemAction(SingleChartRenderer.ItemAction action) {
        return new SingleAreaRenderer.AreaItemAction(action);
    }

    /// Returns the fill baseline for one logical point.
    ///
    /// Stacked datasets may supply the y-value directly below the current point; otherwise the
    /// renderer falls back to the current axis baseline.
    double getAreaBaselineY(int dataIndex) {
        DataSet dataSet = super.getRenderedDataSet();
        if (dataSet instanceof StackedDataSet stackedDataSet) {
            double previousY = stackedDataSet.getPreviousYData(dataIndex);
            if (!Double.isNaN(previousY))
                return previousY;
        }
        return super.getYAxis().getVisibleMin();
    }

    /// Draws a filled square swatch plus the inherited marker glyph.
    @Override
    public void drawLegendMarker(LegendEntry legend, Graphics g, int x, int y, int w, int h) {
        int markerExtent = Math.min(w, h);
        PlotStyle legendStyle = super.getLegendStyle();
        legendStyle.plotRect(g, x + (w - markerExtent) / 2, y + (h - markerExtent) / 2, markerExtent, markerExtent);
        super.drawLegendMarkerOverlay(legendStyle, g, x, y, w, h);
    }

    @Override
    public boolean isFilled() {
        return true;
    }

    private double getAxisBaselineY() {
        return super.getYAxis().getVisibleRange().clamp(super.getCoordinateSystem().getXCrossingValue());
    }
}

