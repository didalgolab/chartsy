package one.chartsy.charting.data;

import one.chartsy.charting.DataInterval;
import one.chartsy.charting.DataWindow;
import one.chartsy.charting.event.DataSetListener;

/// Describes a chartable x/y series together with metadata and change notifications.
///
/// The charting pipeline reads datasets in two different ways: by scalar access through
/// `getXData(int)` and `getYData(int)`, and by materializing temporary `DataPoints` batches for
/// projection, clipping, and hit testing. Implementations in this module typically return mutable
/// scratch `DataPoints` objects, so callers should dispose them when they are no longer needed
/// unless an implementation documents different ownership.
public interface DataSet {

    /// Registers a listener for contents and property changes emitted by this dataset.
    void addDataSetListener(DataSetListener listener);

    /// Returns the current contents as a `DataPoints` batch.
    ///
    /// The returned batch usually uses the dataset's logical index space for its source indices.
    DataPoints getData();

    /// Returns a `DataPoints` batch spanning the requested logical index range.
    ///
    /// Implementations commonly interpret both bounds as inclusive and may clamp them to the
    /// available data or return `null` when the requested range is empty.
    DataPoints getDataBetween(int firstIndex, int lastIndex);

    /// Returns the points relevant to `window`.
    ///
    /// `marginPoints` lets renderers request a few neighboring points beyond the visible x-range so
    /// line and area renderers can preserve continuity at the clip edge. When
    /// `keepOutsideYRange` is `true`, implementations may keep points selected for x-range
    /// continuity even if their y-values lie outside the window.
    DataPoints getDataInside(DataWindow window, int marginPoints, boolean keepOutsideYRange);

    /// Returns the caller-facing label for the point at `index`.
    ///
    /// Datasets that do not expose per-point labels may return `null` or an empty string.
    String getDataLabel(int index);

    /// Returns the display name of this dataset, if any.
    String getName();

    /// Returns an implementation-specific property value associated with `key`.
    Object getProperty(Object key);

    /// Returns the y-value sentinel that represents undefined data, if this dataset uses one.
    ///
    /// Renderers use this to suppress gaps or special missing-data markers without reserving a
    /// fixed numeric value globally.
    Double getUndefValue();

    /// Returns the x value at logical position `index`.
    double getXData(int index);

    /// Returns the x-range of this dataset.
    ///
    /// Implementations may reuse and overwrite `reuse` when it is non-null.
    DataInterval getXRange(DataInterval reuse);

    /// Returns the y value at logical position `index`.
    double getYData(int index);

    /// Returns the y-range of this dataset.
    ///
    /// Implementations may reuse and overwrite `reuse` when it is non-null.
    DataInterval getYRange(DataInterval reuse);

    /// Returns whether this dataset supports in-place mutation through [#setData(int, double, double)].
    boolean isEditable();

    /// Returns whether iterating by logical index also visits x values in sorted order.
    ///
    /// Renderers and clipping code use this to choose faster range-selection paths.
    boolean isXValuesSorted();

    /// Stores an implementation-specific property value.
    ///
    /// Implementations may notify listeners when `fireEvent` is `true` and the effective value
    /// changes.
    void putProperty(Object key, Object value, boolean fireEvent);

    /// Unregisters a listener previously added with [#addDataSetListener(DataSetListener)].
    void removeDataSetListener(DataSetListener listener);

    /// Updates the point at `index` when this dataset is editable.
    void setData(int index, double x, double y);

    /// Returns the number of logical points currently exposed by this dataset.
    int size();
}
