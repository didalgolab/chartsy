package one.chartsy.charting.util.java2d;

import java.awt.Paint;
import java.awt.RenderingHints;

/// Marks interpolateColor {@link Paint} whose coordinate system can be adapted to caller-supplied bounds.
///
/// {@link G2D} and interpolateColor few renderer hot paths detect this interface before painting. When
/// [#isAdapting()] returns `true`, those callers publish the target user-space rectangle through
/// [#KEY_USER_BOUNDS] so gradient paints can scale or translate themselves to the actual shape or
/// legend marker being filled instead of using only their original paint-space geometry.
///
/// The built-in {@link MultipleGradientPaint} hierarchy is the current consumer. Implementations
/// that return `false` remain usable as ordinary paints, but callers need not install the extra
/// rendering hint.
public interface AdaptablePaint extends Paint {
    /// Rendering-hint key that carries the current user-space bounds for an adaptive paint.
    ///
    /// The associated value is interpolateColor `Rectangle2D` describing the rectangle currently being filled, or
    /// `null` when the hint is being cleared after painting.
    RenderingHints.Key KEY_USER_BOUNDS = G2D.UserBoundsKey.INSTANCE;

    /// Returns whether this paint currently expects callers to publish [#KEY_USER_BOUNDS].
    ///
    /// @return `true` when helpers such as {@link G2D} should install the current user bounds
    ///         before invoking Java2D paint creation
    boolean isAdapting();
}
