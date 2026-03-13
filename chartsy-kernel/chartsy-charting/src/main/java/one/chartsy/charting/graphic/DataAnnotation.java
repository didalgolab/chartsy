package one.chartsy.charting.graphic;

import java.awt.Graphics;
import java.awt.geom.Rectangle2D;

import one.chartsy.charting.ChartRenderer;
import one.chartsy.charting.DisplayPoint;

/// Paints one point-attached decoration together with the bounds that decoration occupies.
///
/// [ChartRenderer] implementations consult a `DataAnnotation` after they have resolved a
/// renderer-visible [DisplayPoint]. They use [#getBounds(DisplayPoint, Rectangle2D)] for culling
/// and dirty-region expansion, then call [#draw(Graphics, DisplayPoint)] for points that remain
/// visible.
///
/// `DisplayPoint` instances passed to implementations are usually mutable handles reused by the
/// renderer while it scans data. Implementations should therefore treat them as ephemeral
/// call-scoped views and copy any values they need to retain beyond the current method call.
///
/// ### Implementation Requirements
///
/// [#draw(Graphics, DisplayPoint)] and [#getBounds(DisplayPoint, Rectangle2D)] should stay
/// geometrically consistent for the same point so renderer culling and repaint regions match what
/// is actually painted.
public interface DataAnnotation {
    
    /// Paints the annotation for one renderer-visible point.
    ///
    /// @param g destination graphics
    /// @param displayPoint point whose current display-space location should be annotated
    void draw(Graphics g, DisplayPoint displayPoint);
    
    /// Returns the bounds of what [#draw(Graphics, DisplayPoint)] would paint.
    ///
    /// Implementations may update and return `bounds` in place when it is non-`null`. Callers
    /// should always use the returned reference.
    ///
    /// @param displayPoint point whose annotation bounds should be measured
    /// @param bounds optional reusable rectangle
    /// @return rectangle describing the painted annotation for `displayPoint`
    Rectangle2D getBounds(DisplayPoint displayPoint, Rectangle2D bounds);
}
