package one.chartsy.charting.renderers;

import java.util.logging.Level;
import java.util.logging.Logger;

import one.chartsy.charting.ChartRenderer;
import one.chartsy.charting.data.DataSet;
import one.chartsy.charting.graphic.Marker;

/// Composite renderer that creates one [SingleScatterRenderer] child per dataset and paints every
/// dataset as a marker-only series.
///
/// The marker shape is chosen when the renderer is constructed and copied into each new child. The
/// shared marker size can still be changed later, and that update is propagated to every current
/// child renderer so existing datasets repaint with the new size.
public class ScatterChartRenderer extends SimpleCompositeChartRenderer {
    private static final Logger logger = Logger.getLogger(ScatterChartRenderer.class.getName());
    private static final int DEFAULT_MARKER_SIZE = 3;

    static {
        ChartRenderer.register("Scatter", ScatterChartRenderer.class);
    }

    private final Marker marker;
    private int markerSize;

    /// Creates a scatter renderer that uses square markers with size `3`.
    public ScatterChartRenderer() {
        this(Marker.SQUARE, DEFAULT_MARKER_SIZE);
    }

    /// Creates a scatter renderer with an explicit marker template.
    ///
    /// @param marker marker copied into every new child renderer
    /// @param markerSize marker size shared by all current and future child renderers
    public ScatterChartRenderer(Marker marker, int markerSize) {
        this.marker = marker;
        this.markerSize = markerSize;
    }

    /// Creates the single-dataset scatter child for `dataSet`.
    @Override
    protected ChartRenderer createChild(DataSet dataSet) {
        return new SingleScatterRenderer(marker, markerSize, null);
    }

    /// Returns the marker size currently shared by this renderer's children.
    public final int getMarkerSize() {
        return markerSize;
    }

    /// Returns the child scatter renderer currently responsible for `dataSet`.
    ///
    /// @return the matching child, or `null` when this renderer does not currently display
    ///         `dataSet`
    public final SingleScatterRenderer getScatter(DataSet dataSet) {
        return (SingleScatterRenderer) super.getChild(dataSet);
    }

    /// Updates the marker size for every current and future child scatter renderer.
    public void setMarkerSize(int markerSize) {
        this.markerSize = markerSize;
        for (int childIndex = 0; childIndex < super.getChildCount(); childIndex++) {
            ChartRenderer child = super.getChild(childIndex);
            if (!(child instanceof SingleScatterRenderer scatter)) {
                logger.log(Level.SEVERE, "Child is not setStacked100Percent scatter renderer");
                break;
            }
            scatter.setMarkerSize(markerSize);
        }
    }
}

