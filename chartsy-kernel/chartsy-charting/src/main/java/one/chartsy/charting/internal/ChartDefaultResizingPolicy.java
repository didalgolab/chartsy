package one.chartsy.charting.internal;

import java.awt.Rectangle;
import java.io.Serializable;

import one.chartsy.charting.Axis;
import one.chartsy.charting.Chart;
import one.chartsy.charting.ChartResizingPolicy;
import one.chartsy.charting.DataInterval;

/// Default [ChartResizingPolicy] for charts that want resize operations to preserve manual
/// cartesian axis windows.
///
/// The policy only has specialized behavior for [Chart#CARTESIAN] charts backed by
/// [CartesianProjector]. For those charts it asks the projector to translate the change in plot
/// width or height into a new axis visible range that keeps the currently anchored edge fixed. All
/// other cases fall back to the axis' current visible range unchanged.
public final class ChartDefaultResizingPolicy implements ChartResizingPolicy, Serializable {

    /// Creates the default cartesian resize policy.
    public ChartDefaultResizingPolicy() {
    }

    @Override
    public DataInterval computeVisibleRange(Chart chart, Axis axis, Rectangle previousDrawRect,
                                            Rectangle drawRect) {
        if (chart.getType() == Chart.CARTESIAN) {
            CartesianProjector projector = (CartesianProjector) chart.getProjector2D();
            DataInterval visibleRange = projector.computeVisibleRangeAfterResize(
                    previousDrawRect, drawRect, axis);
            if (visibleRange != null)
                return visibleRange;
        }
        return axis.getVisibleRange();
    }
}
