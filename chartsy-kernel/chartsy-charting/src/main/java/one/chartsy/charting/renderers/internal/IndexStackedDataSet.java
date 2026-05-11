package one.chartsy.charting.renderers.internal;

import one.chartsy.charting.DoublePoint;
import one.chartsy.charting.data.DataSet;
import one.chartsy.charting.data.DataSetPoint;
import one.chartsy.charting.util.DoubleArray;

/// Cumulative stacked dataset for a prefix of index-aligned source datasets.
///
/// `StackedRendererConfig` builds these virtual datasets incrementally for non-normalized stacked
/// rendering: the second rendered series receives a view over the first two source datasets, the
/// third series receives a view over the first three, and so on. Each y-value is therefore the raw
/// cumulative stack height up to the current reference dataset.
public class IndexStackedDataSet extends AbstractIndexStackedDataSet {

    /// Creates a cumulative stacked view over `dataSets`.
    public IndexStackedDataSet(DataSet[] dataSets, IndexStackedDataSet previousStackedDataSet) {
        super(previousStackedDataSet);
        super.setDataSets(dataSets);
    }

    private static boolean isDefined(DataSet dataSet, double y) {
        Double undefValue = dataSet.getUndefValue();
        return (undefValue == null || y != undefValue.doubleValue()) && !Double.isNaN(y);
    }

    /// Computes cumulative stacked y-values for the inclusive logical range.
    ///
    /// Undefined or `NaN` values in intermediate datasets are skipped. When the final child dataset
    /// is undefined at an index, the resulting stacked value is `Double.NaN`.
    @Override
    protected DoubleArray computeStackedData(DoubleArray stackedData, int firstIndex, int lastIndex) {
        int dataSetCount = super.getDataSetCount();
        int cachedSize = stackedData.size();
        DataSet lastDataSet = super.getDataSet(dataSetCount - 1);
        for (int index = firstIndex; index <= lastIndex; index++) {
            double lastY = lastDataSet.getYData(index);
            double stackedY = Double.NaN;
            if (isDefined(lastDataSet, lastY)) {
                double sum = 0.0;
                for (int dataSetIndex = 0; dataSetIndex < dataSetCount; dataSetIndex++) {
                    DataSet dataSet = super.getDataSet(dataSetIndex);
                    double y = dataSet.getYData(index);
                    if (isDefined(dataSet, y))
                        sum += y;
                }
                stackedY = sum;
            }

            if (index >= cachedSize)
                stackedData.add(stackedY);
            else
                stackedData.set(index, stackedY);
        }
        return stackedData;
    }

    /// Returns the baseline directly below this cumulative series.
    ///
    /// For the second rendered series, there is no earlier cumulative virtual dataset yet, so the
    /// baseline falls back to the raw first child dataset.
    @Override
    public double getPreviousYData(int index) {
        AbstractIndexStackedDataSet previousStackedDataSet = this;
        while (previousStackedDataSet.previousStackedDataSet != null) {
            previousStackedDataSet = previousStackedDataSet.previousStackedDataSet;
            double y = previousStackedDataSet.getYData(index);
            Double undefValue = previousStackedDataSet.getUndefValue();
            if ((undefValue == null || y != undefValue.doubleValue()) && !Double.isNaN(y))
                return y;
        }

        if (super.getDataSetCount() > 0) {
            DataSet dataSet = previousStackedDataSet.getDataSet(0);
            double y = dataSet.getYData(index);
            Double undefValue = dataSet.getUndefValue();
            if ((undefValue == null || y != undefValue.doubleValue()) && !Double.isNaN(y))
                return y;
        }
        return Double.NaN;
    }

    /// Returns the last child dataset, which represents the visible series for this stack level.
    @Override
    protected DataSet getRefDataSet() {
        int dataSetCount = super.getDataSetCount();
        return (dataSetCount <= 0) ? null : super.getDataSet(dataSetCount - 1);
    }

    /// Converts a desired stacked y-value back into the raw value of the reference dataset.
    ///
    /// The current values of all other child datasets at the same logical index are subtracted from
    /// the edited stacked height, and the result is clamped to `0`.
    @Override
    public void unmap(DataSetPoint point, DoublePoint editablePoint) {
        super.unmap(point);
        int referenceDataSetIndex = super.getDataSetIndex(point.dataSet);
        double otherYSum = 0.0;
        int dataSetCount = super.getDataSetCount();
        for (int dataSetIndex = 0; dataSetIndex < dataSetCount; dataSetIndex++) {
            if (dataSetIndex == referenceDataSetIndex)
                continue;

            DataSet dataSet = super.getDataSet(dataSetIndex);
            double y = dataSet.getYData(point.index);
            if (isDefined(dataSet, y))
                otherYSum += y;
        }
        editablePoint.x = point.getXData();
        editablePoint.y = Math.max(0.0, editablePoint.y - otherYSum);
    }
}
