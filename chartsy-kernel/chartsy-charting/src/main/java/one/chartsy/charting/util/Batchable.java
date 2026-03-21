package one.chartsy.charting.util;

/// Defines a balanced begin/end protocol for grouping multiple model updates into one logical
/// change batch.
///
/// In the charting data layer this contract is used by data sets and data sources so callers can
/// wrap multi-step mutations and let implementations defer or coalesce listener notifications until
/// the outermost batch ends.
///
/// Calls are expected to be nested and balanced. Typical usage is `startBatch()` before edits and
/// `endBatch()` in a `finally` block.
public interface Batchable {
    
    /// Ends one active batch scope.
    ///
    /// When this closes the outermost scope, implementations may emit deferred notifications for
    /// the accumulated changes.
    void endBatch();
    
    /// Starts a new batch scope.
    ///
    /// Repeated calls may nest the scope, in which case only the matching outermost
    /// [#endBatch()] usually triggers final batch-completion handling.
    void startBatch();
}
