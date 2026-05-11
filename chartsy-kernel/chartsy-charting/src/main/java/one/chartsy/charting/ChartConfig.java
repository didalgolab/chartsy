package one.chartsy.charting;

import java.io.Serializable;

/// Internal strategy that defines one chart type's projector, default axis components, renderer
/// compatibility rules, and data-range normalization.
///
/// [Chart#setType(int)] creates a fresh configuration for the requested type and applies it to the
/// chart through [#applyToChart(Chart, boolean)]. Implementations may therefore keep temporary
/// chart-specific state while they are attached, but they are not intended to be shared across
/// charts.
abstract class ChartConfig implements Serializable {

    /// Returns the configuration strategy for one chart type id.
    ///
    /// Unknown ids fall back to [CartesianConfig] so legacy or partially configured charts still
    /// get a usable default geometry model.
    static ChartConfig forType(int type) {
        return switch (type) {
            case 2 -> new PolarConfig();
            case 3 -> new PieConfig();
            case 4 -> new RadarConfig();
            default -> new CartesianConfig();
        };
    }

    protected Chart chart;

    ChartConfig() {
    }

    /// Installs this configuration's default scales and grids on the attached chart.
    ///
    /// Axis index `-1` addresses the x axis. Non-negative indices address y axes in chart order.
    /// Factories are delegated back to [Chart#createScale(int)] and [Chart#createGrid(int)] so
    /// subclasses only need to specialize those hooks.
    final void installDefaultScalesAndGrids() {
        for (int axisIndex = -1; axisIndex < chart.getYAxisCount(); axisIndex++) {
            chart.setScaleForAxis(axisIndex, chart.createScale(axisIndex));
            chart.setGridForAxis(axisIndex, chart.createGrid(axisIndex));
        }
    }

    /// Attaches this configuration to `chart`, or detaches it when `chart` is `null`.
    ///
    /// Attaching recreates the chart projector, optionally installs the type's default scales and
    /// grids, and recomputes auto data ranges. Subclasses may override this hook when switching to
    /// or from a chart type requires additional transition work, such as replacing incompatible
    /// renderers.
    void applyToChart(Chart chart, boolean installDefaultScaleAndGrid) {
        this.chart = chart;
        if (chart != null) {
            chart.setProjectorInternal(createProjector(chart));
            if (installDefaultScaleAndGrid)
                installDefaultScalesAndGrids();
            chart.updateDataRange();
        }
    }

    /// Returns whether `renderer` can be attached while this configuration is active.
    ///
    /// The base implementation accepts every renderer. Specialized chart types can narrow the set.
    boolean supportsRenderer(ChartRenderer renderer) {
        return true;
    }

    /// Adjusts `dataRange` after the shared [DataRangePolicy] has produced an axis range.
    ///
    /// Implementations use this hook for chart-type-specific normalization, such as forcing a
    /// symmetric radial range or padding category spokes.
    void adjustDataRange(DataInterval dataRange, Axis axis) {
    }

    /// Creates the default grid for the axis identified by `axisIndex`.
    ///
    /// Returning `null` suppresses grid installation for that axis.
    protected Grid createGrid(int axisIndex) {
        return new Grid();
    }

    /// Creates the projector that should drive data/display transforms for `chart`.
    protected abstract ChartProjector createProjector(Chart chart);

    /// Creates the default scale for the axis identified by `axisIndex`.
    ///
    /// The base x-axis scale pins its origin tick at `0.0` by disabling automatic major-unit
    /// selection.
    protected Scale createScale(int axisIndex) {
        Scale scale = new Scale();
        if (axisIndex == -1)
            scale.setStepUnit(null, 0.0);
        return scale;
    }

    /// Creates the scale-configuration template for the axis identified by `axisIndex`.
    ///
    /// The default implementation uses rectangular semantics for both the x axis and y axes.
    protected ScaleConfiguration createScaleConfig(int axisIndex) {
        return new RectangularScaleConfiguration();
    }

    /// Creates the configuration template for the x-axis scale.
    protected final ScaleConfiguration createXScaleConfig() {
        return createScaleConfig(-1);
    }

    /// Creates the configuration template shared by y-axis scales.
    protected final ScaleConfiguration createYScaleConfig() {
        return createScaleConfig(0);
    }

    /// Returns the chart type id represented by this configuration.
    public abstract int getType();
}
