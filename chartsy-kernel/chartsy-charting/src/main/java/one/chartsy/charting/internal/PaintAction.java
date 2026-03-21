package one.chartsy.charting.internal;

import java.awt.Dimension;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;

import one.chartsy.charting.Chart;
import one.chartsy.charting.Legend;

/// Coordinates one off-screen paint pass for a chart-related [JComponent].
///
/// [Chart], [Chart.Area], and [Legend] use this strategy from their `toImage(...)` export paths
/// when the component being rendered may need temporary Swing hierarchy state or size adjustments
/// before it can paint reliably into an image. Implementations may attach either the component
/// itself or an owning root, such as the surrounding chart, to a hidden container, resize the
/// component to the destination image size, and then remove any temporary attachment once painting
/// completes.
///
/// The caller performs a single export pass in this order:
/// 1. [#checkHierarchy(JComponent)]
/// 2. [#prepareComponent(JComponent, Dimension)]
/// 3. paint the component
/// 4. [#disposeComponent(JComponent)]
///
/// Current call sites execute the whole sequence on the Swing event-dispatch thread through
/// [SwingUtilities#invokeAndWait(Runnable)] and construct a fresh strategy instance per export.
public interface PaintAction {

    /// Ensures the supplied component is in a paintable Swing hierarchy for the current export
    /// pass.
    ///
    /// Implementations may attach the component or an owning root component to a temporary
    /// container when the live hierarchy is incomplete. This hook runs before the export helper
    /// finalizes size-dependent state for the image.
    ///
    /// @param c component whose off-screen paint pass is about to start
    void checkHierarchy(JComponent c);

    /// Applies size-dependent state before the component paints into the destination image.
    ///
    /// The supplied size matches the final image size in pixels. Implementations can use it to
    /// resize the painted component and, when required, revalidate a larger owning chart so nested
    /// bounds match the off-screen surface.
    ///
    /// @param c component that will paint into the destination image
    /// @param size final destination image size in pixels
    void prepareComponent(JComponent c, Dimension size);

    /// Releases any temporary hierarchy state installed for the current export pass.
    ///
    /// This hook is intended to undo transient attachment work performed by
    /// [#checkHierarchy(JComponent)] after the component has painted successfully.
    ///
    /// @param c component that has just been painted off-screen
    void disposeComponent(JComponent c);
}
