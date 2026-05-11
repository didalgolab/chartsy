package one.chartsy.charting;

import java.awt.Graphics;
import java.awt.geom.Rectangle2D;

/// Defines a paintable chart layer managed by the [Chart] drawable pipeline.
///
/// A drawable participates in chart painting independently of dataset renderers. During
/// [Chart.Area]'s paint pass, drawables with a negative [#getDrawOrder()] are painted before
/// renderers, and drawables with zero or positive draw order are painted after renderers.
/// Drawables that share the same draw order are processed as a group; if any of them are
/// [ChartDecoration] instances, their `beforeDraw(Graphics)` and `afterDraw(Graphics)` hooks wrap
/// the group's main [#draw(Graphics)] calls.
///
/// [Chart] keeps drawables sorted by [#getDrawOrder()] via
/// [one.chartsy.charting.internal.ChartDrawableComparator]. The same contract also drives clip
/// rejection and repaint invalidation through [#getBounds(Rectangle2D)].
///
/// Implementations should return bounds in chart-area coordinates, preferably reuse the supplied
/// rectangle when one is provided, and report an empty rectangle when detached or unable to paint.
/// Existing implementations are UI-thread-confined and do not provide their own synchronization.
public interface ChartDrawable {

    /// Paints this drawable during the current chart pass.
    ///
    /// [Chart] calls this only after checking [#isVisible()] and after the drawable's bounds have
    /// been compared with the current clip. The supplied graphics context belongs to the active
    /// chart paint operation and must not be disposed by the implementation.
    ///
    /// @param graphics the graphics context clipped to the chart's current drawing region
    void draw(Graphics graphics);

    /// Returns the area that [#draw(Graphics)] may touch.
    ///
    /// [Chart] uses this rectangle both to skip off-screen drawables and to compute repaint areas
    /// when drawable state changes. The result must conservatively cover all pixels the drawable
    /// can emit in chart-area coordinates, including strokes, labels, and decoration adornments.
    ///
    /// @param bounds receives the result when non-null so callers can avoid allocating a new
    ///     rectangle
    /// @return `bounds` when non-null, otherwise a rectangle describing the current paint
    ///     footprint
    Rectangle2D getBounds(Rectangle2D bounds);

    /// Returns this drawable's relative z-order inside the owning [Chart].
    ///
    /// Lower values are painted first. Negative values render before chart renderers; zero and
    /// positive values render after them. Equal values share the same grouped pass.
    ///
    /// @return the ordering key used when the drawable is inserted into the chart
    int getDrawOrder();

    /// Returns whether this drawable should participate in the current paint cycle.
    ///
    /// When this method returns `false`, [Chart] skips the drawable's paint work and, for
    /// [ChartDecoration], also skips the paired pre-draw and post-draw callbacks.
    ///
    /// @return `true` when the drawable currently contributes visible output
    boolean isVisible();
}
