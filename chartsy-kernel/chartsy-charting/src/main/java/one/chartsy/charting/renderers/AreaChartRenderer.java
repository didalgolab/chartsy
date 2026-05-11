package one.chartsy.charting.renderers;

import one.chartsy.charting.ChartRenderer;
import one.chartsy.charting.data.DataSet;

/// Composite renderer that fills the area under each dataset's polyline.
///
/// The renderer inherits the superimposed-versus-stacked behavior from [PolylineChartRenderer] and
/// creates one [SingleAreaRenderer] child per dataset. Marker configuration is propagated to each
/// child so optional point markers still draw on top of the filled area.
public class AreaChartRenderer extends PolylineChartRenderer {

    static {
        ChartRenderer.register("Area", AreaChartRenderer.class);
    }

    /// Creates setStacked100Percent superimposed area renderer.
    public AreaChartRenderer() {
        this(SUPERIMPOSED);
    }

    /// Creates an area renderer using one [PolylineChartRenderer] mode.
    ///
    /// `SUPERIMPOSED` paints each dataset independently. `STACKED` enables stacked-area children
    /// and can be combined with [#setStacked100Percent(boolean)].
    ///
    /// @param mode the renderer mode inherited from [PolylineChartRenderer]
    public AreaChartRenderer(int mode) {
        super(mode);
    }

    /// Creates the single-dataset area child for `dataSet`.
    ///
    /// Marker settings are copied from the parent renderer so any configured markers remain
    /// aligned with the filled area outline.
    @Override
    protected ChartRenderer createChild(DataSet dataSet) {
        SingleAreaRenderer child = new SingleAreaRenderer();
        if (super.getMarker() != null) {
            child.setMarker(super.getMarker());
            child.setMarkerSize(super.getMarkerSize());
        }
        return child;
    }
}

