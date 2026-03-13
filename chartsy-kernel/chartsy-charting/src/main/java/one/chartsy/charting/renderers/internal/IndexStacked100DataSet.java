package one.chartsy.charting.renderers.internal;

import one.chartsy.charting.DataInterval;
import one.chartsy.charting.DoublePoint;
import one.chartsy.charting.data.DataSet;
import one.chartsy.charting.data.DataSetPoint;
import one.chartsy.charting.util.DoubleArray;

/// Percentage-normalized stacked dataset for one member of an index-aligned stack.
///
/// Each y-value represents the cumulative percentage occupied by all child datasets from index `0`
/// through one selected child dataset at the same logical point. `StackedRendererConfig` builds
/// one instance per source dataset when 100%-stacked rendering is enabled, chaining them through
/// [AbstractIndexStackedDataSet] so renderers can still obtain the baseline below each series.
public class IndexStacked100DataSet extends AbstractIndexStackedDataSet {
    private final int referenceDataSetIndex;

    /// Creates the normalized stacked view for `referenceDataSetIndex`.
    public IndexStacked100DataSet(
            int referenceDataSetIndex,
            DataSet[] dataSets,
            IndexStacked100DataSet previousStackedDataSet
    ) {
        super(previousStackedDataSet);
        this.referenceDataSetIndex = referenceDataSetIndex;
        super.setDataSets(dataSets);
    }

    private static boolean isDefined(DataSet dataSet, double y) {
        Double undefValue = dataSet.getUndefValue();
        return (undefValue == null || y != undefValue.doubleValue()) && !Double.isNaN(y);
    }

    /// Pins the visible y-range to `0..100` while delegating the x-range to the reference dataset.
    @Override
    protected void computeLimits(DataInterval xRange, DataInterval yRange) {
        super.getDataSet(referenceDataSetIndex).getXRange(xRange);
        yRange.set(0.0, 100.0);
    }

    /// Computes cumulative stack percentages for the inclusive logical range.
    ///
    /// The final child dataset is optimized to either `100.0` or `Double.NaN` because it always
    /// ends at the top of a fully normalized stack when its source point is defined.
    @Override
    protected DoubleArray computeStackedData(DoubleArray stackedData, int firstIndex, int lastIndex) {
        int dataSetCount = super.getDataSetCount();
        int cachedSize = stackedData.size();
        DataSet lastDataSet = super.getDataSet(dataSetCount - 1);
        if (referenceDataSetIndex == dataSetCount - 1) {
            for (int index = firstIndex; index <= lastIndex; index++) {
                double y = lastDataSet.getYData(index);
                double normalizedY = isDefined(lastDataSet, y) ? 100.0 : Double.NaN;
                if (index >= cachedSize)
                    stackedData.add(normalizedY);
                else
                    stackedData.set(index, normalizedY);
            }
            return stackedData;
        }

        for (int index = firstIndex; index <= lastIndex; index++) {
            double lastY = lastDataSet.getYData(index);
            double normalizedY = Double.NaN;
            if (isDefined(lastDataSet, lastY)) {
                double total = 0.0;
                double cumulative = 0.0;
                for (int dataSetIndex = 0; dataSetIndex < dataSetCount; dataSetIndex++) {
                    DataSet dataSet = super.getDataSet(dataSetIndex);
                    double y = dataSet.getYData(index);
                    if (!isDefined(dataSet, y))
                        continue;

                    total += y;
                    if (dataSetIndex <= referenceDataSetIndex)
                        cumulative += y;
                }
                normalizedY = (total == 0.0) ? 0.0 : 100.0 * cumulative / total;
            }

            if (index >= cachedSize)
                stackedData.add(normalizedY);
            else
                stackedData.set(index, normalizedY);
        }
        return stackedData;
    }

    /// Returns the child dataset represented by this normalized stacked view.
    @Override
    protected DataSet getRefDataSet() {
        return super.getDataSet(referenceDataSetIndex);
    }

    /// Writes a percentage edit back into the underlying raw-value stack.
    ///
    /// Values below `0` are clamped to `0`. Values at or above `100` are handled by zeroing all
    /// other child datasets at the same logical index so the reference dataset occupies the full
    /// normalized stack height.
    @Override
    public void setData(int index, double x, double y) {
        double normalizedY = y;
        if (normalizedY < 100.0) {
            if (normalizedY < 0.0)
                normalizedY = 0.0;
            super.setData(index, x, normalizedY);
        } else {
            int dataSetCount = super.getDataSetCount();
            for (int dataSetIndex = 0; dataSetIndex < dataSetCount; dataSetIndex++) {
                if (dataSetIndex != referenceDataSetIndex)
                    super.getDataSet(dataSetIndex).setData(index, x, 0.0);
            }
        }
    }

    /// Converts an edited cumulative percentage back into the raw y-value of the reference dataset.
    ///
    /// The conversion solves for the reference value that would place the cumulative stack at
    /// `editablePoint.y` percent given the current sums below and above the reference dataset.
    @Override
    public void unmap(DataSetPoint point, DoublePoint editablePoint) {
        super.unmap(point);
        double belowSum = 0.0;
        double aboveSum = 0.0;
        double normalizedY = editablePoint.y;
        int dataSetCount = super.getDataSetCount();
        for (int dataSetIndex = 0; dataSetIndex < dataSetCount; dataSetIndex++) {
            if (dataSetIndex == referenceDataSetIndex)
                continue;

            DataSet dataSet = super.getDataSet(dataSetIndex);
            double y = dataSet.getYData(point.index);
            if (!isDefined(dataSet, y))
                continue;

            if (dataSetIndex < referenceDataSetIndex)
                belowSum += y;
            else
                aboveSum += y;
        }
        editablePoint.y = Math.max(0.0, (normalizedY * (belowSum + aboveSum) - 100.0 * belowSum) / (100.0 - normalizedY));
    }
}
