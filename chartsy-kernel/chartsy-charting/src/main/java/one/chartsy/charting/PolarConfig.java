package one.chartsy.charting;

import one.chartsy.charting.internal.PolarProjector;

/// Configures the chart engine for general polar plotting.
///
/// This strategy keeps the normal chart renderer pipeline intact, but swaps the rectangular
/// geometry model for a [PolarProjector], a circular x-axis scale configuration, and radial y-axis
/// scale configurations. The chart therefore interprets x values as angles and y values as
/// distance from the polar origin without opting into the pie-specific renderer restrictions from
/// [PieConfig] or the spoke-padding rules from [RadarConfig].
///
/// When the active polar projector uses symmetric radial mapping, y-axis auto ranges are widened to
/// the same absolute magnitude above and below zero so the visual origin stays centered.
class PolarConfig extends ChartConfig {

    private static final int TYPE = 2;

    /// Creates the default configuration for general polar charts.
    PolarConfig() {
    }

    /// Normalizes y-axis ranges for symmetric polar projection.
    ///
    /// Polar charts leave angular ranges unchanged. Radial ranges are only adjusted when the
    /// active [PolarProjector] is symmetric, in which case the larger absolute endpoint becomes the
    /// shared distance from zero in both directions.
    @Override
    void adjustDataRange(DataInterval dataRange, Axis axis) {
        if (axis.isYAxis())
            if (((PolarProjector) super.chart.getProjector()).isSymmetric()) {
                double maxDistanceFromOrigin =
                        Math.max(Math.abs(dataRange.getMin()), Math.abs(dataRange.getMax()));
                dataRange.set(-maxDistanceFromOrigin, maxDistanceFromOrigin);
            }
    }

    /// Creates the projector that converts angular and radial data values into display geometry.
    ///
    /// The projector inherits the chart's stored starting angle and angular range so polar layout
    /// survives chart-type switches and projector recreation.
    @Override
    protected ChartProjector createProjector(Chart chart) {
        PolarProjector projector = new PolarProjector();
        projector.setStartingAngle(chart.getStoredStartingAngleOrZero());
        projector.setRange(chart.getStoredAngleRange());
        return projector;
    }

    /// Selects the default scale configuration for one axis in polar mode.
    ///
    /// The x axis is rendered as a circular angular scale. Every y axis uses radial line geometry.
    @Override
    protected ScaleConfiguration createScaleConfig(int axisIndex) {
        if (axisIndex == -1)
            return new CircularScaleConfiguration();
        return new RadialScaleConfiguration();
    }

    /// Returns the chart type id used by [ChartConfig#forType(int)] for polar charts.
    @Override
    public int getType() {
        return TYPE;
    }
}
