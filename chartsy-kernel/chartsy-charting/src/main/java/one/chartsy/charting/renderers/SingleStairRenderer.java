package one.chartsy.charting.renderers;

import one.chartsy.charting.ChartRenderer;
import one.chartsy.charting.PlotStyle;
import one.chartsy.charting.data.DataPoints;
import one.chartsy.charting.data.DataSet;

/// Single-series renderer that draws one dataset as a staircase.
///
/// The renderer inherits marker overlay and fill behavior from [SingleAreaRenderer], but reshapes
/// each connected run into horizontal-then-vertical steps before the inherited polyline pipeline
/// paints, fills, or hit-tests it.
///
/// Stair geometry only makes sense for nondecreasing x-values. If a dataset advertises sorted
/// x-values, rendering proceeds immediately. Otherwise this renderer performs a quick monotonicity
/// scan and skips rendering when the underlying data would make the step trace run backward.
public class SingleStairRenderer extends SingleAreaRenderer {

    /// Polyline adapter that rewrites straight line runs into step geometry.
    ///
    /// Each source segment becomes a horizontal edge followed by the vertical transition to the
    /// next sample. Filled passes are additionally closed against the current x-axis crossing so
    /// the inherited area logic can reuse the transformed run as a polygon.
    class StairItemAction extends SinglePolylineRenderer.PolyItemAction {

        StairItemAction(SingleChartRenderer.ItemAction delegate) {
            super(delegate);
        }

        @Override
        protected void processPolyItem(SingleChartRenderer.Points points, int pointIndex,
                                       SinglePolylineRenderer.PolyItem item, PlotStyle style) {
            int sourcePointCount = item.sourcePointCount;
            int segmentCount = sourcePointCount - 1;
            double[] sourceXValues = item.getXValuesClone();
            double[] sourceYValues = item.getYValuesClone();

            boolean outlineRun = item.drawMode == SinglePolylineRenderer.PolyItem.DRAW_MODE_OUTLINE;
            int extraPointCount = outlineRun ? segmentCount : segmentCount + 2;
            item.add(new double[extraPointCount], new double[extraPointCount], extraPointCount);

            int writeIndex = writeStepVertices(item, sourceXValues, sourceYValues, segmentCount);

            double baselineY = getCoordinateSystem().getXCrossingValue();
            if (outlineRun)
                addBoundaryBaselineConnector(points, pointIndex, item, sourceXValues[0],
                        sourceXValues[segmentCount], baselineY);
            else
                appendFillClosure(item, writeIndex, sourceXValues[0], sourceXValues[segmentCount], baselineY);

            super.processConnectedPolyItem(points, pointIndex, item, style);
        }

        private void addBoundaryBaselineConnector(SingleChartRenderer.Points points, int pointIndex,
                                                  SinglePolylineRenderer.PolyItem item, double firstX, double lastX,
                                                  double baselineY) {
            if (points.getDataIndex(pointIndex) == 0)
                item.add(0, new double[]{firstX}, new double[]{baselineY}, 1);
            else if (points.getDataIndex(pointIndex) == points.getDataSet().size() - 1)
                item.add(lastX, baselineY);
        }

        private static void appendFillClosure(SinglePolylineRenderer.PolyItem item, int writeIndex, double firstX,
                                              double lastX, double baselineY) {
            item.set(writeIndex++, lastX, baselineY);
            item.set(writeIndex, firstX, baselineY);
        }

        private static int writeStepVertices(SinglePolylineRenderer.PolyItem item, double[] xValues, double[] yValues,
                                             int segmentCount) {
            int writeIndex = 0;
            for (int segmentIndex = 0; segmentIndex < segmentCount; segmentIndex++) {
                item.set(writeIndex++, xValues[segmentIndex], yValues[segmentIndex]);
                item.set(writeIndex++, xValues[segmentIndex + 1], yValues[segmentIndex]);
            }
            item.set(writeIndex++, xValues[segmentCount], yValues[segmentCount]);
            return writeIndex;
        }
    }

    static {
        ChartRenderer.register("SingleStair", SingleStairRenderer.class);
    }

    /// Creates a stair renderer that uses the inherited default style resolution.
    public SingleStairRenderer() {
    }

    /// Creates a stair renderer with an explicit base style.
    public SingleStairRenderer(PlotStyle style) {
        super(style);
    }

    /// Wraps downstream item processing with the stair-step geometry adapter.
    @Override
    SinglePolylineRenderer.PolyItemAction createPolyItemAction(SingleChartRenderer.ItemAction action) {
        return new StairItemAction(action);
    }

    /// Emits items only when the current dataset can be drawn as a left-to-right stair trace.
    ///
    /// Decreasing x-values would make the horizontal segments fold back over earlier samples, so
    /// the batch is dropped rather than emitting self-overlapping geometry.
    @Override
    void forEachItem(SingleChartRenderer.Points points, SingleChartRenderer.ItemAction action) {
        if (hasNonDecreasingXValues(points.getDataSet()))
            super.forEachItem(points, action);
    }

    /// Returns whether `dataSet` can be traversed without a backward horizontal step.
    ///
    /// Datasets that already advertise sorted x-values short-circuit the scan. Otherwise this
    /// method inspects one loaded batch, accepts ties, and rejects any strict decrease.
    private boolean hasNonDecreasingXValues(DataSet dataSet) {
        if (dataSet.isXValuesSorted())
            return true;

        DataPoints data = dataSet.getData();
        if (data == null)
            return true;

        try {
            if (data.size() <= 1)
                return true;

            double previousX = data.getX(0);
            for (int index = 1, pointCount = data.size(); index < pointCount; index++) {
                double currentX = data.getX(index);
                if (previousX > currentX)
                    return false;
                previousX = currentX;
            }
            return true;
        } finally {
            data.dispose();
        }
    }
}
