package one.chartsy.charting.event;

import java.util.EventObject;

import one.chartsy.charting.data.DataSet;
import one.chartsy.charting.data.DataSource;

/// Describes a structural change in a `DataSource`.
///
/// The event is fired after the underlying source has already been mutated. `getFirstIdx()` and
/// `getLastIdx()` therefore describe the affected interval in the current source, while
/// `getOldDataSets()` exposes the full dataset array as it existed immediately before the change.
/// Removal listeners use that snapshot to recover the datasets that just disappeared.
public class DataSourceEvent extends EventObject {
    /// Event type indicating that one or more datasets were inserted into the source.
    public static final int DATASET_ADDED = 0;

    /// Event type indicating that one or more datasets were removed from the source.
    public static final int DATASET_REMOVED = 1;

    private static String typeToString(int type) {
        return switch (type) {
            case DATASET_ADDED -> "DATASET_ADDED";
            case DATASET_REMOVED -> "DATASET_REMOVED";
            default -> "UNKNOWN_TYPE";
        };
    }

    private final int type;
    private final int firstIndex;
    private final int lastIndex;

    private final DataSet[] oldDataSets;

    /// Creates a change event for a data-source mutation.
    ///
    /// `oldDataSets` is the source contents before the change. For removals, the removed datasets
    /// can be read back from the same index interval within that snapshot.
    public DataSourceEvent(DataSource dataSource, int type, int firstIndex, int lastIndex,
                           DataSet[] oldDataSets) {
        super(dataSource);
        this.type = type;
        this.firstIndex = firstIndex;
        this.lastIndex = lastIndex;
        this.oldDataSets = oldDataSets;
    }

    /// Returns the source that emitted this event.
    public final DataSource getDataSource() {
        return (DataSource) getSource();
    }

    /// Returns the first affected dataset index.
    public int getFirstIdx() {
        return firstIndex;
    }

    /// Returns the last affected dataset index.
    public int getLastIdx() {
        return lastIndex;
    }

    /// Returns the full dataset array snapshot from before the change was applied.
    public final DataSet[] getOldDataSets() {
        return oldDataSets;
    }

    /// Returns the event type constant.
    public final int getType() {
        return type;
    }

    @Override
    public String toString() {
        return getClass().getName() + " (" + typeToString(getType()) + ")";
    }
}
