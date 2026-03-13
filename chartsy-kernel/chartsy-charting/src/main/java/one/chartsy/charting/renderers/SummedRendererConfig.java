package one.chartsy.charting.renderers;

import one.chartsy.charting.ChartRenderer;
import one.chartsy.charting.data.DataSet;
import one.chartsy.charting.renderers.internal.VirtualDataSet;
import one.chartsy.charting.renderers.internal.XSummedDataSets;

/// Mode adapter for [StairChartRenderer#SUMMED] that makes each child render a running total.
///
/// [XSummedDataSets] owns one virtual dataset per child. The child at index `n` therefore paints
/// the cumulative sum of datasets `0..n`, while picking and editing still route through the mapped
/// source dataset. Batch notifications are forwarded into the shared summed-dataset family so
/// repeated source updates can be coalesced before the chart range is recomputed.
///
/// Children paint in reverse order so the lower-order running totals remain visible on top of the
/// largest cumulative trace.
class SummedRendererConfig extends CompositeRendererConfig {
    private transient int dataSetChangeBatchDepth;
    private transient boolean refreshPending;
    private transient XSummedDataSets summedDataSets;

    SummedRendererConfig(CompositeChartRenderer renderer) {
        super(renderer);
    }

    /// Returns the reverse traversal order that keeps earlier cumulative traces visible.
    @Override
    int getChildPaintOrderSign() {
        return -1;
    }

    @Override
    void dataSetsAdded(DataSet[] addedDataSets) {
        if (dataSetChangeBatchDepth <= 0)
            rebuildSummedDataSets();
        else
            refreshPending = true;
    }

    @Override
    void dataSetsRemoved(DataSet[] removedDataSets) {
        if (dataSetChangeBatchDepth <= 0)
            rebuildSummedDataSets();
        else
            refreshPending = true;
    }

    @Override
    void dataSetsChangesBatchStarting() {
        dataSetChangeBatchDepth++;
        if (summedDataSets != null)
            summedDataSets.dataSourceChangesStarting();
    }

    @Override
    void dataSetsChangesBatchEnding() {
        if (dataSetChangeBatchDepth <= 0)
            return;

        dataSetChangeBatchDepth--;
        if (dataSetChangeBatchDepth == 0 && refreshPending) {
            refreshPending = false;
            rebuildSummedDataSets();
            return;
        }
        if (summedDataSets != null)
            summedDataSets.dataSourceChangesEnding();
    }

    /// Builds and attaches the current summed dataset family.
    @Override
    void activate() {
        rebuildSummedDataSets();
    }

    /// Restores the original datasets on every child renderer and disposes the summed helpers.
    @Override
    void deactivate() {
        CompositeChartRenderer renderer = getRenderer();
        XSummedDataSets previousSummedDataSets = summedDataSets;
        DataSet[] dataSets = renderer.getDataSource().toArray();
        boolean dataRangeChanged = false;
        for (DataSet dataSet : dataSets) {
            ChartRenderer child = renderer.getChild(dataSet);
            if (child instanceof SingleChartRenderer singleChild)
                dataRangeChanged |= singleChild.setVirtualDataSet(dataSet, null);
        }
        summedDataSets = null;
        if (previousSummedDataSets != null)
            previousSummedDataSets.dispose();
        if (dataRangeChanged && renderer.getChart() != null)
            renderer.getChart().updateDataRange();
    }

    /// Recomputes the cumulative summed datasets and rebinds every child renderer to them.
    private void rebuildSummedDataSets() {
        CompositeChartRenderer renderer = getRenderer();
        XSummedDataSets previousSummedDataSets = summedDataSets;
        boolean dataRangeChanged = false;
        int dataSetCount = renderer.getDataSource().size();
        if (dataSetCount <= 0) {
            summedDataSets = null;
        } else {
            DataSet[] dataSets = renderer.getDataSource().toArray();
            summedDataSets = new XSummedDataSets(dataSets, renderer);
            VirtualDataSet[] virtualDataSets = summedDataSets.getSummedDataSets();
            for (int dataSetIndex = 0; dataSetIndex < dataSetCount; dataSetIndex++) {
                ChartRenderer child = renderer.getChild(dataSets[dataSetIndex]);
                dataRangeChanged |= SingleChartRenderer.setVirtualDataSetIfSupported(child, dataSets[dataSetIndex], virtualDataSets[dataSetIndex]);
            }
            for (int batchIndex = dataSetChangeBatchDepth; batchIndex > 0; batchIndex--)
                summedDataSets.dataSourceChangesStarting();
        }
        if (previousSummedDataSets != null)
            previousSummedDataSets.dispose();
        if (dataRangeChanged && renderer.getChart() != null)
            renderer.getChart().updateDataRange();
    }
}
