package one.chartsy.charting;

import one.chartsy.charting.internal.RadarProjector;

/// Specializes [PolarConfig] for radar and spider-chart presentation.
///
/// Radar mode interprets x values as discrete spoke indices rather than a continuous angular
/// domain. It therefore pads the x-axis range to include the closing spoke, defaults the projector
/// to a top-oriented starting angle, draws radar grids in the chart foreground color, and hides
/// the radial axis lines so the data polygons and grid web dominate the presentation.
class RadarConfig extends PolarConfig {

    private static final int TYPE = 4;

    /// Creates the default configuration for radar charts.
    RadarConfig() {
    }

    /// Pads the x-axis category range so the last spoke can close back to the first one.
    ///
    /// Radar renderers and the [RadarProjector] treat the visible x range as integer spoke
    /// positions. Extending the upper bound by `1.0` gives the closing segment a final category
    /// slot without changing y-axis normalization inherited from [PolarConfig].
    @Override
    void adjustDataRange(DataInterval dataRange, Axis axis) {
        if (axis.isXAxis())
            if (!dataRange.isEmpty()) {
                dataRange.max = dataRange.max + 1.0;
            }
    }

    /// Creates the grid used for both radar spokes and concentric polygon rings.
    ///
    /// The grid adopts the chart foreground as its major paint so the radar scaffold matches the
    /// chart's axis and label styling.
    @Override
    protected Grid createGrid(int axisIndex) {
        Grid grid = new Grid();
        grid.setMajorPaint(super.chart.getForeground());
        return grid;
    }

    /// Creates the projector that renders radar spokes and closed polygons.
    ///
    /// Radar charts default their first spoke to the upward direction when no explicit starting
    /// angle was stored on the chart.
    @Override
    protected ChartProjector createProjector(Chart chart) {
        RadarProjector projector = new RadarProjector();
        projector.setStartingAngle(chart.getStoredStartingAngleOrRightAngle());
        return projector;
    }

    /// Creates the default scale for one radar axis.
    ///
    /// The x axis uses whole-step categories with both tick levels hidden because spoke placement
    /// comes from category spacing rather than visible tick marks. Y-axis scales are also hidden so
    /// the polygon web supplied by [Grid] becomes the primary radial reference.
    @Override
    protected Scale createScale(int axisIndex) {
        Scale scale = new Scale();
        if (axisIndex != -1)
            scale.setAxisVisible(false);
        else
            scale.setStepUnit(1.0, 0.0);
        scale.setMajorTickVisible(false);
        scale.setMinorTickVisible(false);
        return scale;
    }

    /// Returns the chart type id used by [ChartConfig#forType(int)] for radar charts.
    @Override
    public int getType() {
        return TYPE;
    }
}
