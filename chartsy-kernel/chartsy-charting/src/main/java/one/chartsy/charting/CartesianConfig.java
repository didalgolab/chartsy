package one.chartsy.charting;

import one.chartsy.charting.internal.CartesianProjector;

/// Default [ChartConfig] for ordinary Cartesian charts.
///
/// This configuration keeps the inherited [ChartConfig] defaults for rectangular scales, regular
/// grids, renderer compatibility, and unmodified renderer-derived data ranges. It exists mainly as
/// the explicit configuration object for [Chart#CARTESIAN] and as the fallback returned by
/// [ChartConfig#forType(int)] when an unknown type id is requested.
///
/// Unlike [PolarConfig], [PieConfig], and [RadarConfig], this strategy carries no chart-specific
/// projector state. Recreating it therefore only reaffirms the module's baseline rectangular
/// geometry model.
class CartesianConfig extends ChartConfig {

    private static final int TYPE = Chart.CARTESIAN;

    /// Creates the default configuration for standard x/y charts.
    CartesianConfig() {
    }

    /// Creates the projector used for all Cartesian x/y coordinate transforms.
    ///
    /// The `chart` parameter is currently unused because [CartesianProjector] has no chart-local
    /// state to restore during recreation.
    @Override
    protected ChartProjector createProjector(Chart chart) {
        return new CartesianProjector();
    }

    /// Returns the chart type id used by [ChartConfig#forType(int)] for Cartesian charts.
    @Override
    public int getType() {
        return TYPE;
    }
}
