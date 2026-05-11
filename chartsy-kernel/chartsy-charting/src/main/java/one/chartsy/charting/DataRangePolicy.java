package one.chartsy.charting;

/// Strategy that computes the data interval an axis should use for auto-ranging.
///
/// [Chart] applies one shared policy instance across all non-delegated axes. The policy sees the
/// chart, the axis being updated, and optionally a reusable interval from a previous invocation, so
/// implementations may either recycle mutable range objects for efficiency or return a fresh
/// interval each time.
///
/// The returned interval is still passed through [Chart#configureDataRange(Axis, DataInterval)] and
/// then through axis-specific auto-min, auto-max, and optional normalization steps, so the policy
/// should focus on the raw renderer- and dataset-derived range for the requested axis.
public interface DataRangePolicy {

    /// Computes the next auto data range for `axis`.
    ///
    /// During full chart updates, [Chart#updateDataRange()] invokes this method in axis order and
    /// passes the interval returned from the previous invocation back as `reusableRange`. During
    /// single-axis refreshes the same method may be called with `reusableRange == null`.
    ///
    /// @param chart the chart requesting an auto range
    /// @param axis the axis whose data range should be computed
    /// @param reusableRange mutable interval that may be emptied and reused, or `null`
    /// @return non-`null` interval describing the computed data range for `axis`
    DataInterval computeDataRange(Chart chart, Axis axis, DataInterval reusableRange);
}
