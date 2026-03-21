package one.chartsy.charting;

import java.util.Collections;

/// Supplies the legend rows contributed by one renderer or overlay.
///
/// [Legend] does not build legend rows on its own. During a rebuild it asks each legended
/// [ChartRenderer] for its current [LegendEntryProvider] and iterates the returned entries in
/// renderer order. This makes the provider the indirection point for dynamic legend content such as
/// composite renderers, pie-slice rows, or desktop-host overrides that replace the renderer's
/// default legend rows entirely.
///
/// Providers are expected to reflect current renderer state each time [#createLegendEntries()] is
/// called. Returning an empty iterable is the standard way to suppress legend rows for a renderer
/// without changing the surrounding rebuild flow.
@FunctionalInterface
public interface LegendEntryProvider {

    /// Reusable provider that never contributes any legend rows.
    ///
    /// [ChartRenderer] uses this as the default until a renderer installs its own provider or its
    /// `createLegendEntries()` override becomes the active source.
    LegendEntryProvider EMPTY = Collections::emptySet;

    /// Creates the legend rows for the current rebuild pass.
    ///
    /// The returned iterable is consumed immediately by [Legend] while it rebuilds renderer-owned
    /// rows. Implementations may return any iterable shape, including an empty one.
    ///
    /// @return the legend rows to attach for the current renderer state
    Iterable<LegendEntry> createLegendEntries();
}
