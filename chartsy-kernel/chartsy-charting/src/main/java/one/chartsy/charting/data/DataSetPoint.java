package one.chartsy.charting.data;

import java.io.Serializable;

/// Mutable handle pointing at one logical entry in a `DataSet`.
///
/// Renderer, picker, and virtual-dataset code reuses this type in place while mapping and
/// unmapping points between coordinate systems or derived datasets. The public `dataSet` and
/// `index` fields are therefore intentional: callers often populate or rewrite them directly as a
/// lightweight transport object rather than allocating a new wrapper each time.
public class DataSetPoint implements Serializable, Cloneable {

    /// Dataset currently addressed by this handle.
    public DataSet dataSet;

    /// Logical index within [#dataSet].
    ///
    /// Mapping code commonly uses `-1` to mean that the point could not be resolved.
    public int index;

    /// Creates a detached handle for later reuse.
    public DataSetPoint() {
    }

    /// Creates a handle targeting `dataSet[index]`.
    public DataSetPoint(DataSet dataSet, int index) {
        this.dataSet = dataSet;
        this.index = index;
    }

    /// Returns a shallow copy of this handle.
    ///
    /// The cloned point refers to the same dataset instance and logical index.
    @Override
    public DataSetPoint clone() {
        try {
            return (DataSetPoint) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new InternalError();
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof DataSetPoint point)) {
            return false;
        }
        return point.dataSet == dataSet && point.index == index;
    }

    /// Returns this point as a one-element `DataPoints` batch.
    ///
    /// The returned batch snapshots the current x and y values into dedicated one-element arrays
    /// but retains this point's dataset reference and logical index.
    public final DataPoints getData() {
        return new DataPoints(
                dataSet,
                1,
                new double[] { getXData() },
                new double[] { getYData() },
                new int[] { index });
    }

    /// Returns the dataset currently addressed by this handle.
    public DataSet getDataSet() {
        return dataSet;
    }

    /// Returns the logical index currently addressed by this handle.
    public int getIndex() {
        return index;
    }

    /// Returns the current x value of the addressed dataset entry.
    public final double getXData() {
        return dataSet.getXData(index);
    }

    /// Returns the current y value of the addressed dataset entry.
    public final double getYData() {
        return dataSet.getYData(index);
    }

    @Override
    public int hashCode() {
        return index;
    }

    /// Writes new coordinates back to the addressed dataset entry.
    ///
    /// When `index` is `-1`, the point is treated as unresolved and the call is ignored.
    public void setData(double x, double y) {
        if (index != -1) {
            dataSet.setData(index, x, y);
        }
    }

    @Override
    public String toString() {
        return "Point#" + index + " (" + getXData() + "," + getYData() + ") in " + dataSet;
    }
}
