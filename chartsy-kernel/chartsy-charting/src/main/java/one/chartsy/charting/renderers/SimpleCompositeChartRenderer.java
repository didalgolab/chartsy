package one.chartsy.charting.renderers;

import one.chartsy.charting.ChartRenderer;
import one.chartsy.charting.data.DataSet;

/// Convenience [CompositeChartRenderer] for renderers that materialize one child per inserted
/// dataset.
///
/// The base implementation batches renderer-change notifications while datasets are added or
/// removed, attaches each new dataset to the child returned by [#createChild(DataSet)], and drops a
/// child once it no longer remains viewable after dataset removal.
public abstract class SimpleCompositeChartRenderer extends CompositeChartRenderer {

    public SimpleCompositeChartRenderer() {
    }

    /// Creates and attaches one child renderer for each dataset inserted into this renderer.
    @Override
    protected void dataSetsAdded(int fromIndex, int toIndex, DataSet[] oldDataSets) {
        super.startRendererChanges();
        block:
        try {
            for (int index = fromIndex; index <= toIndex; index++) {
                DataSet dataSet = super.getDataSource().get(index);
                ChartRenderer child = createChild(dataSet);
                if (child == null)
                    break block;
                child.getDataSource().add(child.getDataSource().size(), dataSet);
                super.insertChild(index, child);
            }
        } finally {
            super.endRendererChanges();
        }
    }

    /// Detaches datasets from their child renderers and removes children that become non-viewable.
    @Override
    protected void dataSetsRemoved(int fromIndex, int toIndex, DataSet[] oldDataSets) {
        super.startRendererChanges();
        try {
            boolean childRemoved = false;
            for (int index = fromIndex; index <= toIndex; index++) {
                DataSet dataSet = oldDataSets[index];
                ChartRenderer child = super.getChild(dataSet);
                if (child != null) {
                    int childDataSetIndex = child.getDataSetIndex(dataSet);
                    if (childDataSetIndex != -1) {
                        child.getDataSource().set(childDataSetIndex, null);
                        if (!child.isViewable()) {
                            super.removeChild(child);
                            childRemoved = true;
                        }
                    }
                }
            }
            if (childRemoved)
                super.updateChildren();
        } finally {
            super.endRendererChanges();
        }
    }
}
