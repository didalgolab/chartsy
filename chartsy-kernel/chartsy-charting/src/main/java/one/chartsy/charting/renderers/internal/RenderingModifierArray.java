package one.chartsy.charting.renderers.internal;

import java.util.Iterator;

import one.chartsy.charting.graphic.DataAnnotation;
import one.chartsy.charting.graphic.DataRenderingHint;

/// Describes per-item rendering modifiers attached to one renderer-visible dataset view.
///
/// Implementations expose two sparse modifier channels keyed by logical item index:
/// - [DataAnnotation] for point-attached decorations
/// - [DataRenderingHint] for point-specific style overrides
///
/// A `null` lookup result means that no explicit modifier was assigned to that slot.
interface RenderingModifierArray {

    /// Returns the annotation stored for `itemIndex`.
    ///
    /// @param itemIndex logical item index in the current dataset view
    /// @return explicit annotation for `itemIndex`, or `null` when no annotation was assigned
    DataAnnotation getAnnotation(int itemIndex);

    /// Returns the explicitly assigned annotations currently held by this array.
    ///
    /// Unassigned slots are skipped.
    ///
    /// @return iterator over explicit point annotations
    Iterator<DataAnnotation> getAnnotations();

    /// Returns the rendering hint stored for `itemIndex`.
    ///
    /// @param itemIndex logical item index in the current dataset view
    /// @return explicit rendering hint for `itemIndex`, or `null` when no hint was assigned
    DataRenderingHint getRenderingHint(int itemIndex);

    /// Returns the explicitly assigned rendering hints currently held by this array.
    ///
    /// Unassigned slots are skipped.
    ///
    /// @return iterator over explicit point rendering hints
    Iterator<DataRenderingHint> getRenderingHints();

    /// Returns whether at least one explicit annotation is currently stored.
    ///
    /// @return `true` when any point annotation is present
    boolean holdsAnnotation();

    /// Returns whether at least one explicit rendering hint is currently stored.
    ///
    /// @return `true` when any point rendering hint is present
    boolean holdsRenderingHint();

    /// Stores or clears the annotation for `itemIndex`.
    ///
    /// Passing `null` removes the explicit annotation from that slot.
    ///
    /// @param itemIndex  logical item index in the current dataset view
    /// @param annotation annotation to store, or `null` to clear the slot
    void setAnnotation(int itemIndex, DataAnnotation annotation);

    /// Stores or clears the rendering hint for `itemIndex`.
    ///
    /// Passing `null` removes the explicit hint from that slot.
    ///
    /// @param itemIndex     logical item index in the current dataset view
    /// @param renderingHint rendering hint to store, or `null` to clear the slot
    void setRenderingHint(int itemIndex, DataRenderingHint renderingHint);
}
