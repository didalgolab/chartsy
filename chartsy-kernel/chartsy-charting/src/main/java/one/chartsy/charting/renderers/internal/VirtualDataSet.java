package one.chartsy.charting.renderers.internal;

import one.chartsy.charting.DoublePoint;
import one.chartsy.charting.data.CombinedDataSet;
import one.chartsy.charting.data.DataSetPoint;

/// Base class for derived datasets that project points from other datasets.
///
/// `VirtualDataSet` gives renderers a uniform dataset surface while still allowing picks, labels,
/// bounds calculations, and edits to be translated back to the underlying source datasets.
/// Translation happens through mutable [DataSetPoint] handles:
/// - [#map(DataSetPoint)] turns a source-dataset point into the corresponding point in this
///   virtual dataset
/// - [#unmap(DataSetPoint)] performs the inverse translation
///
/// [#mapsMonotonically()] tells renderers whether increasing source indices stay increasing after
/// mapping into this dataset. When that is true, callers such as `SingleChartRenderer` can map
/// only the range endpoints. Otherwise they must translate points one by one.
public abstract class VirtualDataSet extends CombinedDataSet {

    /// Creates an empty virtual dataset.
    public VirtualDataSet() {
    }

    @Override
    public String getDataLabel(int index) {
        DataSetPoint point = new DataSetPoint(this, index);
        unmap(point);
        int sourceIndex = point.getIndex();
        if (sourceIndex < 0)
            return null;
        return point.getDataSet().getDataLabel(sourceIndex);
    }

    @Override
    public double getXData(int index) {
        DataSetPoint point = new DataSetPoint(this, index);
        unmap(point);
        return point.getXData();
    }

    @Override
    public double getYData(int index) {
        DataSetPoint point = new DataSetPoint(this, index);
        unmap(point);
        return point.getYData();
    }

    /// Maps `point` from one of the backing datasets into this virtual dataset.
    ///
    /// Implementations rewrite `point.dataSet` to `this` and update `point.index` to the
    /// corresponding virtual index, or to `-1` when no mapping exists.
    public abstract void map(DataSetPoint point);

    /// Returns whether the index mapping preserves ordering.
    ///
    /// When this returns `true`, renderers may map only the endpoints of a source index range and
    /// assume the virtual indices between them remain in the same order.
    public abstract boolean mapsMonotonically();

    /// Writes new coordinates through this virtual dataset into the mapped backing dataset point.
    ///
    /// The default implementation first checks [#isEditable()], then resolves `index` through
    /// [#unmap(DataSetPoint, DoublePoint)] so implementations can translate both the target point
    /// and the proposed coordinates before the backing dataset receives the edit.
    @Override
    public void setData(int index, double x, double y) {
        if (isEditable()) {
            DoublePoint point = new DoublePoint(x, y);
            DataSetPoint dataSetPoint = new DataSetPoint(this, index);
            unmap(dataSetPoint, point);
            dataSetPoint.setData(point.x, point.y);
        }
    }

    /// Maps `point` from this virtual dataset back to the corresponding backing dataset point.
    ///
    /// Implementations rewrite `point.dataSet` and `point.index` in place. `point.index` should be
    /// set to `-1` when the virtual point cannot be resolved.
    public abstract void unmap(DataSetPoint point);

    /// Maps an edit target and proposed coordinates back to the backing dataset.
    ///
    /// This overload underpins editable virtual datasets whose visible coordinates differ from the
    /// stored source coordinates. Implementations may rewrite both `point` and `editablePoint`.
    public abstract void unmap(DataSetPoint point, DoublePoint editablePoint);
}
