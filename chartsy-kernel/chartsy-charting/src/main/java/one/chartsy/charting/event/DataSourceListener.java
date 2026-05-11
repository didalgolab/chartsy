package one.chartsy.charting.event;

import java.util.EventListener;

/// Receives notifications when a `DataSource` changes its dataset membership or ordering.
///
/// Notifications arrive after the mutation has been applied. Listeners that need explicit
/// start/end markers around batched updates can additionally implement `DataSourceListener2`.
public interface DataSourceListener extends EventListener {

    /// Handles a structural change in the observed data source.
    void dataSourceChanged(DataSourceEvent event);
}
