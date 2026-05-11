package one.chartsy.charting;

import java.io.Serializable;

import one.chartsy.charting.data.DataSource;

/// Default auto-range policy used by [Chart] when callers do not install a custom
/// [DataRangePolicy].
///
/// The policy gathers raw ranges from every viewable top-level renderer:
/// - x axes merge each renderer's [ChartRenderer#getXRange(DataInterval)]
/// - y axes recurse through renderer trees and keep only the y-range contributions that resolve to
///   the requested axis
///
/// When an entire renderer subtree resolves to one y axis, the subtree's aggregate
/// [ChartRenderer#getYRange(DataInterval)] is used directly. Mixed-axis renderer trees are visited
/// child by child so each target axis sees only the branches that actually belong to it.
///
/// After collection, the range is passed through [Chart#configureDataRange(Axis, DataInterval)],
/// explicit axis min/max overrides are re-applied, and [#adjustRange(Chart, Axis, DataInterval)]
/// can widen or snap the result. The base implementation adjusts only y-axis ranges.
public class DefaultDataRangePolicy implements DataRangePolicy, Serializable {
    private static final double ADJUSTABLE_RANGE_LIMIT = Double.MAX_VALUE / 2.0;

    /// Returns the resolved y-axis number for `renderer` when every viewable descendant agrees.
    ///
    /// `-1` indicates either that the branch is not assigned to any y axis or that viewable
    /// descendants disagree and must be visited individually.
    private static int findUniformYAxisNumber(ChartRenderer renderer) {
        int yAxisNumber = renderer.getYAxisNumber();
        if (yAxisNumber < 0) {
            return -1;
        }

        for (int childIndex = 0, childCount = renderer.getChildCount(); childIndex < childCount; childIndex++) {
            ChartRenderer child = renderer.getChild(childIndex);
            if (child != null && child.isViewable() && findUniformYAxisNumber(child) != yAxisNumber) {
                return -1;
            }
        }
        return yAxisNumber;
    }

    /// Adds the y-range contributions from `renderer` that belong to `axis`.
    private static void collectYAxisRange(ChartRenderer renderer, Chart chart, Axis axis, DataInterval dataRange) {
        int yAxisNumber = findUniformYAxisNumber(renderer);
        if (yAxisNumber >= 0) {
            if (yAxisNumber < chart.getYAxisCount() && chart.getYAxis(yAxisNumber) == axis) {
                dataRange.add(renderer.getYRange(null));
            }
            return;
        }

        for (int childIndex = 0, childCount = renderer.getChildCount(); childIndex < childCount; childIndex++) {
            ChartRenderer child = renderer.getChild(childIndex);
            if (child != null && child.isViewable()) {
                collectYAxisRange(child, chart, axis, dataRange);
            }
        }
    }

    /// Returns whether `dataRange` leaves enough numeric headroom for scale-based adjustment.
    private static boolean isAdjustableRange(DataInterval dataRange) {
        return !dataRange.isEmpty()
                && dataRange.getMin() > -ADJUSTABLE_RANGE_LIMIT
                && dataRange.getMax() < ADJUSTABLE_RANGE_LIMIT;
    }

    private static boolean containsAnyData(DataSource dataSource) {
        for (int dataSetIndex = dataSource.size() - 1; dataSetIndex >= 0; dataSetIndex--) {
            if (dataSource.get(dataSetIndex).size() > 0) {
                return true;
            }
        }
        return false;
    }

    public DefaultDataRangePolicy() {
    }

    /// Expands or normalizes the collected auto range before the owning axis adopts it.
    ///
    /// The base implementation adjusts only y-axis ranges:
    /// - flat y ranges are widened around the coordinate system's current x-axis crossing so charts
    ///   with real data do not collapse to zero height
    /// - the owning [Scale]'s [StepsDefinition] is then allowed to snap the range to its preferred
    ///   step boundaries
    ///
    /// Extremely large finite values are left unchanged to avoid overflow in later normalization.
    protected void adjustRange(Chart chart, Axis axis, DataInterval dataRange) {
        if (dataRange.getLength() == 0.0 && axis.isYAxis()) {
            DataSource dataSource = chart.getDataSource();
            if (dataSource == null || !containsAnyData(dataSource)) {
                return;
            }

            dataRange.add(chart.getCoordinateSystem(axis).getXCrossingValue());
            if (dataRange.getLength() == 0.0) {
                dataRange.add(0.0);
            }
            if (dataRange.getLength() == 0.0) {
                dataRange.expand(1.0);
            }
        }

        if (!isAdjustableRange(dataRange)) {
            return;
        }

        Scale scale = chart.getScale(axis);
        if (scale != null) {
            scale.getStepsDefinition().adjustRange(null, dataRange);
        }
    }

    /// {@inheritDoc}
    ///
    /// The supplied `reusableRange` is cleared and reused when present. After raw renderer ranges
    /// are merged, chart-specific configuration and explicit axis min/max overrides are applied
    /// before [#adjustRange(Chart, Axis, DataInterval)] is consulted.
    @Override
    public DataInterval computeDataRange(Chart chart, Axis axis, DataInterval reusableRange) {
        DataInterval dataRange = (reusableRange != null) ? reusableRange : new DataInterval();
        dataRange.empty();

        for (int rendererIndex = 0, rendererCount = chart.getRendererCount(); rendererIndex < rendererCount; rendererIndex++) {
            ChartRenderer renderer = chart.getRenderer(rendererIndex);
            if (!renderer.isViewable()) {
                continue;
            }

            if (axis.isXAxis()) {
                dataRange.add(renderer.getXRange(null));
            } else {
                collectYAxisRange(renderer, chart, axis, dataRange);
            }
        }

        chart.configureDataRange(axis, dataRange);
        if (!axis.isAutoDataMin()) {
            dataRange.setMin(axis.getDataMin());
        }
        if (!axis.isAutoDataMax()) {
            dataRange.setMax(axis.getDataMax());
        }
        if (shouldAdjust(chart, axis)) {
            adjustRange(chart, axis, dataRange);
        }
        return dataRange;
    }

    /// Returns whether [#adjustRange(Chart, Axis, DataInterval)] should run for `axis`.
    ///
    /// Subclasses override this when they want the raw collected range after chart configuration
    /// and explicit axis min/max overrides, but without the default y-axis widening and step-based
    /// snapping.
    protected boolean shouldAdjust(Chart chart, Axis axis) {
        return axis.isYAxis();
    }
}
