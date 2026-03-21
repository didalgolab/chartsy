package one.chartsy.charting.event;

import java.util.EventObject;

import one.chartsy.charting.data.DataSet;

/// Describes a structural or label-oriented change in a `DataSet`.
///
/// The event combines a type code with an affected logical index range. Most event types use the
/// stored range directly. `FULL_UPDATE` is special: [#getLastIdx()] resolves against the dataset's
/// current `size()` so senders can invalidate the entire dataset without computing the trailing
/// index up front.
public class DataSetContentsEvent extends EventObject implements DataSetEvent {

    /// Marks the start of a logical mutation batch.
    ///
    /// Listeners can defer expensive recomputation until the matching [#BATCH_END].
    public static final int BATCH_BEGIN = -2;

    /// Marks the end of a logical mutation batch.
    public static final int BATCH_END = -1;

    /// Announces that data at the given range is about to change in place.
    public static final int BEFORE_DATA_CHANGED = 1;

    /// Announces that data at the given range has changed in place.
    public static final int AFTER_DATA_CHANGED = 2;

    /// Announces that data values in the given range changed and dependent caches should refresh.
    public static final int DATA_CHANGED = 3;

    /// Announces that new data was appended or inserted in the given range.
    public static final int DATA_ADDED = 4;

    /// Announces that labels associated with the given range changed.
    public static final int DATA_LABEL_CHANGED = 5;

    /// Announces that listeners should treat the entire dataset as dirty.
    public static final int FULL_UPDATE = 6;

    private final int firstIndex;
    private final int lastIndex;
    private final int type;

    /// Creates a `FULL_UPDATE` event for `dataSet`.
    public DataSetContentsEvent(DataSet dataSet) {
        this(dataSet, FULL_UPDATE, 0, 0);
    }

    /// Re-targets an existing contents event at another dataset while preserving its payload.
    public DataSetContentsEvent(DataSet dataSet, DataSetContentsEvent event) {
        this(dataSet, event.getType(), event.getFirstIdx(), event.getLastIdx());
    }

    /// Creates an event whose affected range contains exactly one logical index.
    public DataSetContentsEvent(DataSet dataSet, int type, int index) {
        this(dataSet, type, index, index);
    }

    /// Creates an event with an explicit type and logical index range.
    public DataSetContentsEvent(DataSet dataSet, int type, int firstIndex, int lastIndex) {
        super(dataSet);
        this.type = type;
        this.firstIndex = firstIndex;
        this.lastIndex = lastIndex;
    }

    /// Returns the dataset that emitted the event.
    @Override
    public DataSet getDataSet() {
        return (DataSet) super.getSource();
    }

    /// Returns the first logical index affected by this event.
    public int getFirstIdx() {
        return firstIndex;
    }

    /// Returns the last logical index affected by this event.
    ///
    /// For `FULL_UPDATE`, this resolves to `getDataSet().size() - 1` when the dataset reference is
    /// still available.
    public int getLastIdx() {
        if (type == FULL_UPDATE && getDataSet() != null) {
            return getDataSet().size() - 1;
        }
        return lastIndex;
    }

    /// Returns the event type constant.
    public int getType() {
        return type;
    }

    @Override
    public String toString() {
        return super.toString() + " (" + type + ") <" + firstIndex + "," + lastIndex + ">";
    }
}
