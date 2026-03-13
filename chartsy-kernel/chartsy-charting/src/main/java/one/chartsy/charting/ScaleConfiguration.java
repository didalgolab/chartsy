package one.chartsy.charting;

import java.awt.Graphics;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.io.Serializable;

/// Strategy that adapts one [Scale] to the geometry rules of a specific chart type.
///
/// `Scale` owns axis model state, prepared tick caches, label and title renderers, and annotation
/// lifecycle. `ScaleConfiguration` supplies the chart-type-specific parts of that pipeline:
/// - the rendered axis shape and its occupied bounds,
/// - display-space angles used for tick, label, title, and annotation placement,
/// - hit testing for the decorated scale,
/// - item-density estimation along the effective axis length, and
/// - any extra cache invalidation required before bounds queries or painting.
///
/// A configuration is attached to at most one scale at a time through [#setScale(Scale)].
/// Implementations may install a specialized `Scale.Steps` instance on attachment and may keep
/// additional geometry caches derived from the owning chart. They therefore follow the same
/// mutability and thread-safety model as the owning scale.
abstract class ScaleConfiguration implements Serializable {

    /// Returns the built-in configuration used for one internal chart-type id.
    ///
    /// Unknown ids currently return `null`.
    static ScaleConfiguration forChartType(int chartType) {
        return switch (chartType) {
            case 1 -> new RectangularScaleConfiguration();
            case 2 -> new CircularScaleConfiguration();
            case 3 -> new RadialScaleConfiguration();
            default -> null;
        };
    }

    ScaleConfiguration() {
    }

    /// Returns the crossing policy to use when the scale has no explicit crossing.
    abstract Axis.Crossing getAutoCrossing();

    /// Returns the tick length for either major or minor tick marks.
    abstract int getTickSize(boolean majorTick);

    /// Returns the display-space angle used to place decorations around `value`.
    ///
    /// Implementations use this angle for tick direction, label anchoring, title placement, and
    /// annotation layout.
    abstract double getAxisAngle(double value);

    /// Estimates how many items of the supplied extent fit along the rendered axis.
    ///
    /// `width` and `height` describe one item's occupied box before any implementation-specific
    /// axis-orientation math is applied. `spacing` is the requested gap between neighbors.
    abstract int estimateVisibleItemCount(int width, int height, int spacing);

    /// Applies any final geometry adjustments needed after base bounds collection.
    ///
    /// Implementations typically mutate `bounds` in place to account for translated axes, linked
    /// parallel scales, or other chart-type-specific offsets.
    abstract void adjustBounds(Rectangle2D bounds);

    /// Attaches this configuration to `scale`, or detaches it when `scale` is `null`.
    ///
    /// Attachment may replace the scale's `Scale.Steps` specialization and refresh any
    /// implementation-specific state derived from the owning scale.
    abstract void setScale(Scale scale);

    /// Marks any implementation-specific caches as stale.
    abstract void invalidate();

    /// Returns whether the implementation-specific caches already match the current scale state.
    abstract boolean isUpToDate();

    /// Computes the anchor point used to place the scale title.
    ///
    /// The supplied `titleAnchor` is a reusable output parameter in chart-area display space.
    protected abstract void computeTitleLocation(DoublePoint titleAnchor);

    /// Returns whether the rendered scale should treat `point` as a hit.
    public abstract boolean contains(Point2D point);

    /// Verifies that any required geometry caches are available.
    ///
    /// Implementations typically throw when callers try to query bounds or draw before the owning
    /// chart has refreshed the scale caches.
    abstract void requireUpToDate();

    /// Paints the scale into `g`.
    protected abstract void draw(Graphics g);

    /// Ensures that any required geometry caches are prepared.
    abstract void ensureUpToDate();

    /// Returns the bounds of the axis shape itself, excluding later adjustment steps.
    abstract Rectangle2D getAxisBounds(Rectangle2D bounds);

    /// Returns the effective display-space length of the rendered axis.
    protected abstract int getScaleLength();
}
