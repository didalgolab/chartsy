package one.chartsy.charting.renderers.internal;

import one.chartsy.charting.ChartRenderer;
import one.chartsy.charting.data.DataSet;
import one.chartsy.charting.event.DataSetListener;
import one.chartsy.charting.renderers.SingleChartRenderer;

/// Mutable attachment stored in one [DataSet] for one [SingleChartRenderer].
///
/// [SingleChartRenderer] uses this object as its per-dataset side table. It keeps the renderer's
/// currently installed [VirtualDataSet] together with the lazily created [DataSetListener] that
/// must follow whichever dataset view is active. Storing the attachment on the dataset itself lets
/// a renderer swap between source and derived datasets without maintaining a second lookup map.
public class DataSetRendererProperty {
    /// Removes the renderer-specific attachment from `dataSet`.
    public static void clearDataSetRendererProperty(SingleChartRenderer renderer, DataSet dataSet) {
        dataSet.putProperty(renderer, null, false);
    }

    /// Returns the renderer-specific attachment stored in `dataSet`.
    ///
    /// When `createIfAbsent` is `true`, the caller must supply a [SingleChartRenderer]. A new
    /// attachment is then allocated lazily the first time that renderer connects to the dataset.
    public static DataSetRendererProperty getDataSetRendererProperty(ChartRenderer renderer, DataSet dataSet,
                                                                     boolean createIfAbsent) {
        if (!createIfAbsent) {
            if (!(renderer instanceof SingleChartRenderer))
                return null;
            return (DataSetRendererProperty) dataSet.getProperty(renderer);
        }

        SingleChartRenderer singleRenderer = (SingleChartRenderer) renderer;
        DataSetRendererProperty property = (DataSetRendererProperty) dataSet.getProperty(singleRenderer);
        if (property == null) {
            property = new DataSetRendererProperty(singleRenderer);
            dataSet.putProperty(singleRenderer, property, false);
        }
        return property;
    }

    /// Returns the virtual dataset currently exposed by `renderer` for `dataSet`.
    ///
    /// @return the active virtual dataset, or `null` when the renderer uses `dataSet` directly
    public static VirtualDataSet getVirtualDataSet(ChartRenderer renderer, DataSet dataSet) {
        DataSetRendererProperty property = getDataSetRendererProperty(renderer, dataSet, false);
        if (property == null)
            return null;
        return property.virtualDataSet;
    }

    /// Installs `property` as the renderer-specific attachment stored in `dataSet`.
    public static void setDataSetRendererProperty(SingleChartRenderer renderer, DataSet dataSet,
                                                  DataSetRendererProperty property) {
        dataSet.putProperty(renderer, property, false);
    }

    /// Renderer that owns this attachment.
    public final SingleChartRenderer renderer;

    /// Derived dataset currently exposed by the renderer, or `null` when it uses the source data.
    public VirtualDataSet virtualDataSet;

    /// Listener that follows whichever dataset view is currently attached to the renderer.
    public DataSetListener listener;

    /// Creates an empty attachment for `renderer`.
    public DataSetRendererProperty(SingleChartRenderer renderer) {
        this.renderer = renderer;
    }
}
