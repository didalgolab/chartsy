package one.chartsy.charting.event;

/// Optional extension of `DataSourceListener` for batched structural updates.
///
/// `AbstractDataSource` uses these hooks to mirror its current batch depth with balanced
/// `startDataSourceChanges()` and `endDataSourceChanges()` callbacks. Implementations can use them
/// to defer expensive recalculation until the surrounding sequence of
/// `dataSourceChanged(DataSourceEvent)` callbacks has completed.
public interface DataSourceListener2 extends DataSourceListener {

    /// Signals that the current outermost batch of data-source changes has finished.
    void endDataSourceChanges();

    /// Signals that a new outermost batch of data-source changes is starting.
    void startDataSourceChanges();
}
