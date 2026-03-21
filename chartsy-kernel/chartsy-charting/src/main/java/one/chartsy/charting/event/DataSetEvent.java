package one.chartsy.charting.event;

import one.chartsy.charting.data.DataSet;

/// Common view shared by dataset change events.
///
/// Code that only needs the affected dataset can depend on this interface instead of branching on
/// whether the concrete payload describes contents changes or property changes.
public interface DataSetEvent {

    /// Returns the dataset that emitted the event.
    DataSet getDataSet();
}
