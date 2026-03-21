package one.chartsy.charting.event;

import java.util.EventListener;

/// Receives change notifications from a `DataSet`.
///
/// Contents notifications may be fine-grained index ranges, coarse `FULL_UPDATE` invalidations, or
/// explicit batch boundary markers. Listeners that cache derived geometry should therefore be ready
/// to either update incrementally or fall back to full recomputation when the event type demands
/// it.
public interface DataSetListener extends EventListener {

    /// Called when point contents, labels, or batch/full-update markers change.
    void dataSetContentsChanged(DataSetContentsEvent event);

    /// Called when dataset-level properties change.
    void dataSetPropertyChanged(DataSetPropertyEvent event);
}
