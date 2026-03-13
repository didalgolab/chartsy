package one.chartsy.charting.event;

import java.beans.PropertyChangeEvent;

import one.chartsy.charting.data.DataSet;

/// Property-change event emitted by a `DataSet`.
///
/// This reuses the standard `PropertyChangeEvent` payload so listeners can inspect the property
/// name and old/new values while still recovering the emitting dataset through `DataSetEvent`.
public class DataSetPropertyEvent extends PropertyChangeEvent implements DataSetEvent {

    /// Re-targets an existing property event at another dataset while preserving its payload.
    public DataSetPropertyEvent(DataSet dataSet, DataSetPropertyEvent event) {
        this(dataSet, event.getPropertyName(), event.getOldValue(), event.getNewValue());
    }

    /// Creates a property event for `dataSet`.
    public DataSetPropertyEvent(DataSet dataSet, String propertyName, Object oldValue, Object newValue) {
        super(dataSet, propertyName, oldValue, newValue);
    }

    /// Returns the dataset that emitted the property change.
    @Override
    public DataSet getDataSet() {
        return (DataSet) super.getSource();
    }
}
