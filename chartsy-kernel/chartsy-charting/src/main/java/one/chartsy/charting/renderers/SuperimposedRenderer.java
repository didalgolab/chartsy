package one.chartsy.charting.renderers;

/// Marks a composite renderer whose child renderers may share the same category slot.
///
/// This capability is used by filled child renderers to distinguish true overlap from
/// clustered or stacked layouts exposed by the same parent type. Implementations may
/// support several layout modes at runtime, so callers must check {@link
/// #isSuperimposed()} rather than assuming that every instance currently paints its
/// children on top of each other.
public interface SuperimposedRenderer {

    /// Returns whether filled child renderers should reduce their fill opacity
    /// automatically while this renderer is drawing overlapping series.
    ///
    /// Child renderers currently consult this flag only when {@link
    /// #isSuperimposed()} is `true`.
    ///
    /// @return `true` when filled children should lower their fill opacity while
    ///         overlapping sibling renderers remain visible
    boolean isAutoTransparency();

    /// Returns whether the current renderer mode draws multiple child renderers over
    /// the same category positions instead of separating or stacking them.
    ///
    /// Implementations expose this separately from their type because the same
    /// renderer class can switch between superimposed and non-superimposed modes.
    ///
    /// @return `true` when sibling child renderers are currently meant to occupy
    ///         the same category slot
    boolean isSuperimposed();

    /// Enables or disables automatic transparency for filled child renderers when
    /// overlap is active.
    ///
    /// Implementations may ignore this hint while {@link #isSuperimposed()} is
    /// `false`.
    ///
    /// @param autoTransparency `true` to let filled child renderers lower their fill
    ///                         opacity automatically in superimposed mode
    void setAutoTransparency(boolean autoTransparency);
}
