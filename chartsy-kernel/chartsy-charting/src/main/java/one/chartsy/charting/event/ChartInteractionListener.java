package one.chartsy.charting.event;

import java.util.EventListener;

import one.chartsy.charting.ChartInteractor;

/// Listener for semantic interaction events emitted by one [ChartInteractor].
///
/// Register implementations through
/// [ChartInteractor#addChartInteractionListener(ChartInteractionListener)] to receive picks,
/// highlight transitions, and other higher-level gesture results after an interactor finishes
/// interpreting raw AWT input.
///
/// ### API Note
///
/// Current charting code dispatches these callbacks synchronously on the same event-processing path
/// that recognized the interaction.
public interface ChartInteractionListener extends EventListener {

    /// Handles one interaction event emitted by the observed interactor.
    ///
    /// `event` may be a specialized subtype such as [ChartHighlightInteractionEvent].
    void interactionPerformed(ChartInteractionEvent event);
}
