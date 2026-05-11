package one.chartsy.charting.renderers;

import java.io.Serializable;

import one.chartsy.charting.data.DataSet;

/// Internal mode/lifecycle hook attached to one [CompositeChartRenderer].
///
/// Concrete configs encapsulate the extra behavior that a composite renderer needs when a selected
/// mode affects child paint order, child layout, or dataset-to-child wiring. The parent renderer
/// forwards dataset insertion/removal callbacks, batch boundaries, and mode activation changes to
/// this object instead of hard-coding those concerns into every composite renderer.
///
/// The base implementation keeps the parent renderer's default child traversal order and treats
/// every lifecycle callback as a no-op.
class CompositeRendererConfig implements Serializable {
    private final CompositeChartRenderer renderer;

    CompositeRendererConfig(CompositeChartRenderer renderer) {
        this.renderer = renderer;
    }

    /// Returns the sign consumed by [CompositeChartRenderer#d()] to choose child paint order and
    /// hit-test order.
    int getChildPaintOrderSign() {
        return 1;
    }

    /// Reacts to datasets inserted into the parent renderer's data source.
    ///
    /// `addedDataSets` contains only the newly inserted datasets, already ordered as they now
    /// appear in the parent data source.
    void dataSetsAdded(DataSet[] addedDataSets) {
    }

    /// Returns the composite renderer that owns this configuration.
    final CompositeChartRenderer getRenderer() {
        return renderer;
    }

    /// Reacts to datasets removed from the parent renderer's data source.
    ///
    /// `removedDataSets` contains only the datasets removed by the current change.
    void dataSetsRemoved(DataSet[] removedDataSets) {
    }

    /// Signals the start of a parent data-source batch.
    void dataSetsChangesBatchStarting() {
    }

    /// Signals the end of a parent data-source batch.
    void dataSetsChangesBatchEnding() {
    }

    /// Recomputes child layout or dataset wiring after the parent child set changes.
    void updateChildren() {
    }

    /// Applies this configuration when the owning renderer switches into the corresponding mode.
    void activate() {
    }

    /// Reverts any mode-specific child state before the owning renderer switches away.
    void deactivate() {
    }
}

