package one.chartsy.charting.renderers;

/// Marks a renderer whose horizontal footprint is configured as a share of the
/// current category span.
///
/// The built-in bar and Hi/Lo renderers convert that share into a concrete
/// data-space width using the active dataset's category spacing. Composite
/// renderers reuse the same percentage as a width budget for an entire category
/// slot and may redistribute that budget among child renderers when switching
/// between clustered and overlapping layouts.
public interface VariableWidthRenderer {

    /// Default category-share percentage used by the in-tree variable-width
    /// renderers when callers do not provide an explicit width.
    double DEFAULT_WIDTH = 80.0;

    /// Returns the current occupied width in data coordinates.
    ///
    /// This value is derived from the configured category-share percentage and
    /// the current category span. Composite implementations may report the total
    /// occupied span for their active layout rather than a single child width.
    ///
    /// @return the horizontal span that this renderer currently reserves in data
    ///         space
    double getWidth();

    /// Returns the configured category-share percentage.
    ///
    /// A value of `100` represents one full category span before any
    /// layout-specific redistribution such as clustering or overlap handling.
    ///
    /// @return the width budget expressed as a percentage of the current
    ///         category span
    double getWidthPercent();

    /// Returns whether X-range calculations should preserve category padding at
    /// the first and last visible categories.
    ///
    /// When this flag is `false`, the in-tree single-series implementations
    /// expand their X range only by half of the resolved item width. When it is
    /// `true`, they keep at least half of a category span at the outer borders so
    /// edge items remain spaced like interior categories.
    ///
    /// @return `true` when border categories should keep category-based outer
    ///         padding
    boolean isUseCategorySpacingAtBorders();

    /// Controls whether first and last visible categories keep category-based
    /// outer padding when this renderer contributes to X-range calculations.
    ///
    /// @param useCategorySpacingAtBorders `true` to preserve category spacing at
    ///                                    the plot borders instead of expanding
    ///                                    only by the resolved item width
    void setUseCategorySpacingAtBorders(boolean useCategorySpacingAtBorders);

    /// Sets the width budget as a percentage of the current category span.
    ///
    /// The in-tree single-series implementations expect values in the inclusive
    /// range `0..100`. Composite implementations may redistribute that budget
    /// across child renderers according to their current layout mode.
    ///
    /// @param widthPercent width budget expressed as a percentage of the current
    ///                     category span
    void setWidthPercent(double widthPercent);
}
