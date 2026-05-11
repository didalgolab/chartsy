package one.chartsy.charting.renderers.internal;

import one.chartsy.charting.DoublePoint;
import one.chartsy.charting.data.DataSet;
import one.chartsy.charting.data.DataSetPoint;
import one.chartsy.charting.event.DataSetContentsEvent;
import one.chartsy.charting.event.DataSetPropertyEvent;

/// Virtual dataset that interleaves two source datasets into consecutive point pairs.
///
/// `SingleHiLoRenderer` consumes this dataset as `[2*n, 2*n + 1]` pairs, where both points share
/// the same logical source index but come from different child datasets. That lets the renderer
/// treat two ordinary y-series as one hi/lo, open/close, or body/wick-style series without copying
/// the source data.
///
/// The dataset is capped at two children. When fewer than two datasets are installed, it behaves as
/// empty even though the backing child list may already contain one dataset.
public class HiLoDataSet extends VirtualDataSet {

    HiLoDataSet() {
        super.setMaxDataSetCount(2);
    }

    /// Creates a hi/lo view over `firstDataSet` and `secondDataSet`.
    public HiLoDataSet(DataSet firstDataSet, DataSet secondDataSet) {
        this();
        super.addDataSet(firstDataSet);
        super.addDataSet(secondDataSet);
    }

    /// Returns `2 * min(child size)` once both child datasets are present.
    @Override
    protected int computeDataCount() {
        int dataSetCount = super.getDataSetCount();
        return (dataSetCount <= 1) ? 0 : super.computeDataCount() * dataSetCount;
    }

    /// Translates child contents events into the interleaved virtual index space.
    ///
    /// When the originating child dataset is unknown, the translated event covers the full pair span
    /// for each affected source index.
    @Override
    public void dataSetContentsChanged(DataSetContentsEvent event) {
        super.dataSetContentsChanged(event);

        int dataSetCount = super.getDataSetCount();
        int dataSetIndex = super.getDataSetIndex(event.getDataSet());
        DataSetContentsEvent translatedEvent = (dataSetIndex < 0)
                ? new DataSetContentsEvent(
                        this,
                        event.getType(),
                        event.getFirstIdx() * dataSetCount,
                        (event.getLastIdx() + 1) * dataSetCount - 1)
                : new DataSetContentsEvent(
                        this,
                        event.getType(),
                        event.getFirstIdx() * dataSetCount + dataSetIndex,
                        event.getLastIdx() * dataSetCount + dataSetIndex);
        super.fireDataSetContentsEvent(translatedEvent);
    }

    /// Forwards child property changes unchanged.
    @Override
    protected void dataSetPropertyChanged(DataSetPropertyEvent event) {
        super.fireDataSetPropertyEvent(event);
    }

    /// Returns the two child names joined with `" - "`, or an empty string until both exist.
    @Override
    public String getName() {
        if (super.getDataSetCount() != 2)
            return "";
        return super.getDataSet(0).getName() + " - " + super.getDataSet(1).getName();
    }

    /// Returns `null`.
    ///
    /// Undefined child values are exposed as `Double.NaN`.
    @Override
    public Double getUndefValue() {
        return null;
    }

    /// Returns the x-value for the interleaved point at `index`.
    @Override
    public double getXData(int index) {
        int dataSetCount = super.getDataSetCount();
        if (dataSetCount < 2)
            return 0.0;
        return super.getDataSet(index % dataSetCount).getXData(index / dataSetCount);
    }

    /// Returns the y-value for the interleaved point at `index`.
    ///
    /// Child undefined-value sentinels are normalized to `Double.NaN`.
    @Override
    public double getYData(int index) {
        int dataSetCount = super.getDataSetCount();
        if (dataSetCount < 2)
            return 0.0;

        DataSet dataSet = super.getDataSet(index % dataSetCount);
        double y = dataSet.getYData(index / dataSetCount);
        Double undefValue = dataSet.getUndefValue();
        if (undefValue != null && y == undefValue.doubleValue())
            return Double.NaN;
        return y;
    }

    /// Returns `true`; edits are forwarded to the currently mapped child dataset.
    @Override
    public boolean isEditable() {
        return true;
    }

    /// Returns whether the first child dataset reports sorted x-values.
    @Override
    public boolean isXValuesSorted() {
        return super.getDataSet(0) != null && super.getDataSet(0).isXValuesSorted();
    }

    /// Maps a child dataset point into the interleaved virtual index space.
    @Override
    public void map(DataSetPoint point) {
        int dataSetIndex = super.getDataSetIndex(point.dataSet);
        if (dataSetIndex == -1)
            throw new IllegalArgumentException("Unknown data set");

        point.index = point.index * super.getDataSetCount() + dataSetIndex;
        point.dataSet = this;
    }

    /// Returns `true`; the interleaving preserves source index order.
    @Override
    public boolean mapsMonotonically() {
        return true;
    }

    /// Maps an interleaved virtual point back to the corresponding child dataset point.
    @Override
    public void unmap(DataSetPoint point) {
        int dataSetCount = super.getDataSetCount();
        point.dataSet = super.getDataSet(point.index % dataSetCount);
        point.index = point.index / dataSetCount;
    }

    /// Maps editable points back to the child dataset without changing the coordinates.
    @Override
    public void unmap(DataSetPoint point, DoublePoint editablePoint) {
        unmap(point);
    }
}
