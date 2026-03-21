package one.chartsy.charting.renderers;

import one.chartsy.charting.ChartRenderer;
import one.chartsy.charting.data.DataSet;
import one.chartsy.charting.renderers.internal.IndexStacked100DataSet;
import one.chartsy.charting.renderers.internal.IndexStackedDataSet;
import one.chartsy.charting.renderers.internal.VirtualDataSet;
import one.chartsy.charting.renderers.internal.XStackedDataSets;

/// Mode adapter that rewires stacked composite-renderer children onto derived stacked datasets.
///
/// The adapter supports two stacking strategies:
/// - x-based stacking, backed by [XStackedDataSets], which aligns samples by x value and keeps one
///   shared virtual-dataset family for all children
/// - index-based stacking, which builds one incremental [VirtualDataSet] chain per child and
///   treats equal item positions as belonging to the same stack
///
/// Activating the config replaces every child renderer's source dataset with the derived stacked
/// view used for painting, picking, and editing. Deactivating it restores the original datasets and
/// disposes any shared x-aligned helper. If that rewiring changes the effective y range, the owning
/// chart is asked to recompute its data range.
///
/// Bars keep insertion-order painting so the first dataset stays at the visual base of each stack.
/// Polyline-style stacked renderers paint in reverse order so smaller cumulative traces remain
/// visible on top of later ones.
class StackedRendererConfig extends CompositeRendererConfig {
    private boolean stacked100Percent;
    private boolean stackedByIndex;
    private boolean diverging;
    private transient int dataSetChangeBatchDepth;
    private transient boolean refreshPending;
    private transient XStackedDataSets xStackedDataSets;

    StackedRendererConfig(
            CompositeChartRenderer renderer,
            boolean stacked100Percent,
            boolean stackedByIndex,
            boolean diverging) {
        super(renderer);
        this.stacked100Percent = stacked100Percent;
        this.stackedByIndex = stackedByIndex;
        this.diverging = diverging;
    }

    /// Returns the paint-order direction needed for the current stacked renderer family.
    @Override
    int getChildPaintOrderSign() {
        return (getRenderer() instanceof BarChartRenderer) ? 1 : -1;
    }

    /// Enables or disables `100%` normalization and rebuilds the derived stacked datasets.
    void setStacked100Percent(boolean stacked100Percent) {
        if (stacked100Percent != this.stacked100Percent) {
            this.stacked100Percent = stacked100Percent;
            rebuildStackedDataSets();
        }
    }

    @Override
    void dataSetsAdded(DataSet[] addedDataSets) {
        if (dataSetChangeBatchDepth <= 0)
            rebuildStackedDataSets();
        else
            refreshPending = true;
    }

    /// Switches stacked children between x-based and index-based grouping.
    void setStackedByIndex(boolean stackedByIndex) {
        if (stackedByIndex != this.stackedByIndex) {
            this.stackedByIndex = stackedByIndex;
            rebuildStackedDataSets();
        }
    }

    @Override
    void dataSetsRemoved(DataSet[] removedDataSets) {
        if (dataSetChangeBatchDepth <= 0)
            rebuildStackedDataSets();
        else
            refreshPending = true;
    }

    @Override
    void dataSetsChangesBatchStarting() {
        dataSetChangeBatchDepth++;
        if (xStackedDataSets != null)
            xStackedDataSets.dataSourceChangesStarting();
    }

    @Override
    void dataSetsChangesBatchEnding() {
        if (dataSetChangeBatchDepth <= 0)
            return;

        dataSetChangeBatchDepth--;
        if (dataSetChangeBatchDepth == 0 && refreshPending) {
            refreshPending = false;
            rebuildStackedDataSets();
            return;
        }
        if (xStackedDataSets != null)
            xStackedDataSets.dataSourceChangesEnding();
    }

    /// Builds and attaches the current stacked dataset views.
    @Override
    void activate() {
        rebuildStackedDataSets();
    }

    /// Restores the original datasets on every child renderer.
    @Override
    void deactivate() {
        CompositeChartRenderer renderer = getRenderer();
        DataSet[] dataSets = renderer.getDataSource().toArray();
        boolean dataRangeChanged = false;
        for (DataSet dataSet : dataSets) {
            ChartRenderer child = renderer.getChild(dataSet);
            if (child instanceof SingleChartRenderer singleChild)
                dataRangeChanged |= singleChild.setVirtualDataSet(dataSet, null);
        }
        if (dataRangeChanged && renderer.getChart() != null)
            renderer.getChart().updateDataRange();
    }

    /// Recomputes the derived stacked datasets and rebinds every child renderer to the new view.
    ///
    /// When a dataset batch is already open, the newly created shared x-based helper is advanced
    /// into the same batch depth so later source notifications stay balanced.
    private void rebuildStackedDataSets() {
        CompositeChartRenderer renderer = getRenderer();
        XStackedDataSets previousStackedDataSets = xStackedDataSets;
        boolean dataRangeChanged = false;
        int dataSetCount = renderer.getDataSource().size();
        if (dataSetCount > 0) {
            DataSet[] dataSets = renderer.getDataSource().toArray();
            if (!stackedByIndex) {
                xStackedDataSets = new XStackedDataSets(dataSets, renderer, stacked100Percent, diverging);
                Object[] stackedDataSets = xStackedDataSets.getStackedDataSets();
                for (int dataSetIndex = 0; dataSetIndex < dataSetCount; dataSetIndex++) {
                    ChartRenderer child = renderer.getChild(dataSets[dataSetIndex]);
                    dataRangeChanged |= SingleChartRenderer.setVirtualDataSetIfSupported(
                            child,
                            dataSets[dataSetIndex],
                            (VirtualDataSet) stackedDataSets[dataSetIndex]);
                }
                for (int batchIndex = dataSetChangeBatchDepth; batchIndex > 0; batchIndex--)
                    xStackedDataSets.dataSourceChangesStarting();
            } else {
                if (stacked100Percent) {
                    IndexStacked100DataSet stackedDataSet = null;
                    for (int dataSetIndex = 0; dataSetIndex < dataSetCount; dataSetIndex++) {
                        stackedDataSet = new IndexStacked100DataSet(dataSetIndex, dataSets, stackedDataSet);
                        ChartRenderer child = renderer.getChild(dataSets[dataSetIndex]);
                        dataRangeChanged |= SingleChartRenderer.setVirtualDataSetIfSupported(child, dataSets[dataSetIndex], (VirtualDataSet) stackedDataSet);
                    }
                } else {
                    ChartRenderer firstChild = renderer.getChild(dataSets[0]);
                    dataRangeChanged |= SingleChartRenderer.setVirtualDataSetIfSupported(firstChild, dataSets[0], null);
                    IndexStackedDataSet stackedDataSet = null;
                    for (int dataSetIndex = 1; dataSetIndex < dataSetCount; dataSetIndex++) {
                        DataSet[] stackedMembers = new DataSet[dataSetIndex + 1];
                        for (int memberIndex = 0; memberIndex <= dataSetIndex; memberIndex++)
                            stackedMembers[memberIndex] = dataSets[memberIndex];
                        stackedDataSet = new IndexStackedDataSet(stackedMembers, stackedDataSet);
                        ChartRenderer child = renderer.getChild(dataSets[dataSetIndex]);
                        dataRangeChanged |= SingleChartRenderer.setVirtualDataSetIfSupported(child, dataSets[dataSetIndex], (VirtualDataSet) stackedDataSet);
                    }
                }
                xStackedDataSets = null;
            }
        }
        if (previousStackedDataSets != null)
            previousStackedDataSets.dispose();
        if (dataRangeChanged && renderer.getChart() != null)
            renderer.getChart().updateDataRange();
    }

    /// Enables or disables diverging positive/negative stack separation.
    public void setDiverging(boolean diverging) {
        if (diverging != this.diverging) {
            this.diverging = diverging;
            rebuildStackedDataSets();
        }
    }
}
