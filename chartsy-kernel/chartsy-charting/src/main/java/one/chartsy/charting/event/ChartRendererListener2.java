package one.chartsy.charting.event;

import one.chartsy.charting.Chart;

/// Batch-aware extension of [ChartRendererListener].
///
/// [Chart] uses this interface when a series of renderer mutations should be treated as one
/// logical update. Listeners added while a batch is already open receive synthetic
/// [#startRendererChanges()] callbacks for the current nesting depth, and listeners removed before
/// the batch closes receive matching synthetic [#endRendererChanges()] callbacks.
public interface ChartRendererListener2 extends ChartRendererListener {

    /// Begins one nesting level of a renderer-change batch.
    ///
    /// Implementations can defer expensive recomputation until the matching
    /// [#endRendererChanges()] closes the outermost level.
    void startRendererChanges();

    /// Ends one nesting level of a renderer-change batch.
    ///
    /// When nesting reaches zero, listeners should flush any work deferred since the first
    /// [#startRendererChanges()] in that batch.
    void endRendererChanges();
}
