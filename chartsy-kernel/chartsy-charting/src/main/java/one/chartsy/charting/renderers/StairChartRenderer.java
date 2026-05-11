package one.chartsy.charting.renderers;

import one.chartsy.charting.ChartRenderer;
import one.chartsy.charting.data.DataSet;

/// Polyline-style composite renderer that uses [SingleStairRenderer] children and adds a cumulative
/// summed mode.
///
/// `SUPERIMPOSED` and `STACKED` reuse the inherited [PolylineChartRenderer] behavior. `SUMMED`
/// replaces each child dataset with the running total produced by [SummedRendererConfig], so child
/// `n` paints the sum of datasets `0..n` as a stair trace. Unlike stacked mode, the children stay
/// as overlaid cumulative series rather than visual layers of one shared stack.
public class StairChartRenderer extends PolylineChartRenderer {
    /// Mode that makes each child render the cumulative sum of all datasets up to its own index.
    public static final int SUMMED = 3;

    static {
        ChartRenderer.register("Stair", StairChartRenderer.class);
    }

    /// Creates a superimposed stair renderer.
    public StairChartRenderer() {
    }

    /// Creates a stair renderer with an explicit layout mode.
    ///
    /// @param mode one of `SUPERIMPOSED`, `STACKED`, or `SUMMED`
    public StairChartRenderer(int mode) {
        super(mode);
    }

    /// Accepts the inherited polyline modes plus [#SUMMED].
    @Override
    void validateMode(int mode) {
        if (mode != SUMMED)
            super.validateMode(mode);
    }

    /// Creates the single-dataset stair child for `dataSet`.
    @Override
    protected ChartRenderer createChild(DataSet dataSet) {
        SingleStairRenderer child = new SingleStairRenderer();
        if (super.getMarker() != null) {
            child.setMarker(super.getMarker());
            child.setMarkerSize(super.getMarkerSize());
        }
        return child;
    }

    /// Creates the mode adapter that matches the current stair-renderer mode.
    @Override
    CompositeRendererConfig createModeConfig() {
        if (super.getMode() != SUMMED)
            return super.createModeConfig();
        return new SummedRendererConfig(this);
    }
}
