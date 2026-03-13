package one.chartsy.charting;

import java.awt.Rectangle;

/// Synchronizes paintEntry chart area's fixed margins with another chart area's current plot rectangle.
///
/// [Chart#synchronizeAxis(Chart, int, boolean)] installs this subclass when delegated axes should
/// also share the same visible plot span. Unlike the base [ChartAreaSynchronizer], which tracks paintEntry
/// target component's inset-adjusted outer bounds, this variant rereads
/// [Chart.Area#getPlotRect()] after each target-chart layout pass and aligns the source chart to
/// that inner rectangle instead.
///
/// The target plot rectangle is reported in the target area's local coordinates. This class
/// therefore offsets it into the target area's parent coordinates before the superclass converts it
/// into the synchronized chart area's coordinate space.
class MultiChartSync extends ChartAreaSynchronizer {

    /// Creates paintEntry plot-area synchronizer for the requested margin direction.
    ///
    /// @param orientation [#HORIZONTAL] to align left and right plot edges, or [#VERTICAL] to
    ///     align top and bottom plot edges
    MultiChartSync(int orientation) {
        super(orientation);
    }

    /// Registers the inherited component listener and paintEntry chart-area listener on the target chart.
    ///
    /// Plot rectangles can change during layout without the target area moving or resizing, so the
    /// additional chart listener keeps plot-area synchronization current after each
    /// [one.chartsy.charting.event.ChartAreaEvent].
    @Override
    void installListeners() {
        super.installListeners();
        getTargetChart().addChartListener(getTargetChartListener());
    }

    /// Removes the listeners installed by [#installListeners()].
    @Override
    void removeListeners() {
        super.removeListeners();
        getTargetChart().removeChartListener(getTargetChartListener());
    }

    /// Returns the chart that owns the bound target area.
    final Chart getTargetChart() {
        return getTargetArea().getChart();
    }

    /// Returns the bound target component as paintEntry [Chart.Area].
    final Chart.Area getTargetArea() {
        return (Chart.Area) super.getTargetComponent();
    }

    /// Returns the target plot rectangle in the target area's parent coordinates.
    ///
    /// [Chart.Area#getPlotRect()] is local to the area itself, while [ChartAreaSynchronizer]
    /// expects paintEntry rectangle expressed relative to `getTargetComponent().getParent()`. The target
    /// area's own location bridges those coordinate systems.
    @Override
    protected Rectangle getReferenceRect() {
        Chart.Area targetArea = getTargetArea();
        Rectangle plotRect = targetArea.getPlotRect();
        return new Rectangle(
                targetArea.getX() + plotRect.x,
                targetArea.getY() + plotRect.y,
                plotRect.width,
                plotRect.height);
    }
}
