package one.chartsy.charting;

import java.awt.BasicStroke;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.Paint;
import java.awt.Graphics;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.Dimension2D;
import java.awt.geom.Rectangle2D;

import one.chartsy.charting.internal.CartesianProjector;
import one.chartsy.charting.internal.PolarProjector;
import one.chartsy.charting.util.GraphicUtil;
import one.chartsy.charting.util.MathUtil;

/// Default scale-configuration strategy for any axis that renders as a straight line between two
/// display-space points.
///
/// [ChartConfig] uses this configuration for ordinary cartesian x and y axes, and
/// [RadialScaleConfiguration] inherits it for radial spokes because they also reduce to a straight
/// line once the current projector and visible range are known. The class owns the shared logic
/// for:
/// - resolving the axis' display-space endpoints and angle,
/// - computing stacking offsets for parallel cartesian axes,
/// - deriving title placement and tick-density estimates from the cached line geometry, and
/// - drawing single-device-pixel axes crisply under transformed `Graphics2D` output.
///
/// Geometry and offset state are cached separately. Callers are expected to invalidate those
/// caches through [#b()] and then let [#e()] rebuild them before draw or bounds queries. The cache
/// guards deliberately fail fast when a caller asks for geometry before the owning [Chart] has
/// brought its scales up to date.
class RectangularScaleConfiguration extends DefaultScaleConfiguration {

    /// Cached start/end points of the rendered axis line in display coordinates.
    private transient DoublePoints axisPoints;

    /// Cached angle of [#axisPoints] in degrees, measured in display space.
    private transient double axisAngle;

    /// Whether [#axisPoints] and [#axisAngle] reflect the chart's current scale state.
    private transient boolean geometryUpToDate;

    /// Cached horizontal translation applied when multiple parallel axes are stacked.
    private transient int xOffset;

    /// Cached vertical translation applied when multiple parallel axes are stacked.
    private transient int yOffset;

    /// Whether [#xOffset] and [#yOffset] reflect the chart's current scale state.
    private transient boolean offsetUpToDate;

    /// Creates the default straight-axis scale configuration.
    RectangularScaleConfiguration() {
    }

    /// Converts one-pixel axis lines into device-aligned fill rectangles.
    ///
    /// [#drawAxis(Graphics)] uses this helper when the current [PlotStyle] resolves to a
    /// one-device-pixel [BasicStroke]. The helper tracks both the graphics transform and the
    /// device's default transform so HiDPI scaling still lands the axis on exact device pixels.
    private static final class DevicePixelSnapper {
        private final double scaleX;
        private final double scaleY;
        private final double translateX;
        private final double translateY;

        /// Captures the effective device and user transforms from `g2`.
        private DevicePixelSnapper(Graphics2D g2) {
            var transform = g2.getTransform();
            GraphicsConfiguration configuration = g2.getDeviceConfiguration();
            double graphicsScaleX = 1.0;
            double graphicsScaleY = 1.0;
            if (configuration != null) {
                var defaultTransform = configuration.getDefaultTransform();
                graphicsScaleX = Math.abs(defaultTransform.getScaleX());
                graphicsScaleY = Math.abs(defaultTransform.getScaleY());
            }
            double transformScaleX = Math.abs(transform.getScaleX());
            double transformScaleY = Math.abs(transform.getScaleY());
            scaleX = normalizeScale(Math.max(transformScaleX, graphicsScaleX));
            scaleY = normalizeScale(Math.max(transformScaleY, graphicsScaleY));
            translateX = transform.getTranslateX();
            translateY = transform.getTranslateY();
        }

        /// Normalizes missing or degenerate scale factors to `1.0`.
        private static double normalizeScale(double scale) {
            if (!Double.isFinite(scale) || scale <= 0.0)
                return 1.0;
            return scale;
        }

        /// Snaps one user-space x coordinate to the nearest device pixel.
        private int snapX(double userX) {
            return (int) Math.round(userX * scaleX + translateX);
        }

        /// Snaps one user-space y coordinate to the nearest device pixel.
        private int snapY(double userY) {
            return (int) Math.round(userY * scaleY + translateY);
        }

        /// Converts a device-pixel rectangle back into user coordinates for painting.
        private Rectangle2D toUserRect(int deviceX, int deviceY, int deviceWidth, int deviceHeight) {
            return new Rectangle2D.Double(
                    (deviceX - translateX) / scaleX,
                    (deviceY - translateY) / scaleY,
                    deviceWidth / scaleX,
                    deviceHeight / scaleY
            );
        }
    }
    
    /// Returns the default crossing edge for this scale when no explicit crossing was configured.
    ///
    /// The x axis defaults to [Axis#MIN_VALUE]. For cartesian y axes, the primary y axis defaults
    /// to [Axis#MIN_VALUE] and additional y axes default to [Axis#MAX_VALUE] so parallel axes
    /// stack on opposite plot edges by default.
    @Override
    Axis.Crossing getAutoCrossing() {
        Chart chart = super.scale.getChart();
        if (chart == null || super.scale.getAxis().isXAxis())
            return Axis.MIN_VALUE;

        int yAxisIndex = 0;
        while (yAxisIndex < chart.getYAxisCount() && chart.getYScale(yAxisIndex) != super.scale)
            yAxisIndex++;
        return (yAxisIndex != 0) ? Axis.MAX_VALUE : Axis.MIN_VALUE;
    }
    
    /// Computes the angle used for tick, label, and title placement at one scale value.
    ///
    /// Cartesian axes use the crossed axis geometry when available. Axes attached to a dual axis
    /// derive a perpendicular direction from the current crossing edge, projector reversal, and
    /// axis reversal. Polar radial sides rotate the cached axis angle by `±90` degrees.
    @Override
    double getAxisAngle(double value) {
        requireScaleGeometry();
        if (!super.scale.requiresDynamicPointPreparation()) {
            if (usesCartesianProjector())
                return getCartesianAxisAngle();

            return switch (super.scale.getRadialSide()) {
                case 1 -> getCachedAxisAngle() + 90.0;
                case 2 -> getCachedAxisAngle() - 90.0;
                default -> throw new IllegalStateException("invalid radial side");
            };
        }

        Axis axis = super.scale.getAxis();
        Axis dualAxis = super.scale.getDualAxis();
        double crossingValue = super.scale.getCrossingValue();
        boolean crossingAtFarEdge = !dualAxis.isReversed()
                ? crossingValue == dualAxis.getVisibleMax()
                : crossingValue == dualAxis.getVisibleMin();
        boolean invertedPerpendicular =
                (axis.isXAxis() && !super.scale.getChart().isProjectorReversed())
                        || (axis.isYAxis() && super.scale.getChart().isProjectorReversed());
        double angleOffset = invertedPerpendicular
                ? (crossingAtFarEdge ? 90.0 : -90.0)
                : (crossingAtFarEdge ? -90.0 : 90.0);
        return axis.isReversed() ? getCachedAxisAngle() - angleOffset : getCachedAxisAngle() + angleOffset;
    }
    
    /// Estimates how many rotated labels or ticks of the supplied extent fit along the axis line.
    @Override
    int estimateVisibleItemCount(int width, int height, int spacing) {
        requireScaleGeometry();
        double angle = getCachedAxisAngle() + super.scale.getLabelRotation();
        double sin = MathUtil.sinDeg(angle);
        double cos = MathUtil.cosDeg(angle);
        int scaleLength = getScaleLength();
        double projectedSize = Math.abs(width * cos) + Math.abs(height * sin) + spacing;
        return (int) Math.max(Math.round(scaleLength / projectedSize) + 1L, 2L);
    }
    
    /// Expands `bounds` with the cached axis offset and optional annotation bounds.
    ///
    /// Parallel cartesian axes are translated by the cached offset before drawing. Bounds queries
    /// must therefore apply the same offset so hit testing and export code see the rendered
    /// location instead of the unshifted geometry.
    @Override
    synchronized void adjustBounds(Rectangle2D bounds) {
        super.adjustBounds(bounds);
        requireScaleOffset();
        int xOffset = getXOffset();
        int yOffset = getYOffset();
        if (xOffset == 0 && yOffset == 0) {
            if (super.scale.shouldDrawLinkedScale())
                GraphicUtil.addToRect(bounds, super.scale.getNextParallelScale().getBoundsUsingCache(null));
            return;
        }

        bounds.setRect(bounds.getX() + xOffset, bounds.getY() + yOffset, bounds.getWidth(), bounds.getHeight());
        if (super.scale.shouldDrawLinkedScale()) {
            Rectangle2D annotationBounds = super.scale.getNextParallelScale().getBoundsUsingCache(null);
            annotationBounds.setRect(annotationBounds.getX() + xOffset, annotationBounds.getY() + yOffset,
                    annotationBounds.getWidth(), annotationBounds.getHeight());
            GraphicUtil.addToRect(bounds, annotationBounds);
        }
    }
    
    /// Returns the display-space angle reported by the cartesian projector for this scale's side.
    private double getCartesianAxisAngle() {
        double axisAngle = getCartesianProjector().getAxisAngle(super.scale.getDualAxis());
        return switch (super.scale.getSide()) {
            case -1 -> axisAngle + 180.0;
            case 1 -> axisAngle;
            default -> throw new IllegalStateException("invalid side");
        };
    }
    
    /// Invalidates both cached axis geometry and cached stacking offsets.
    @Override
    void invalidate() {
        invalidateScaleGeometry();
        invalidateScaleOffset();
        super.invalidate();
    }
    
    /// Returns whether both straight-axis caches already match the chart's current scale state.
    @Override
    boolean isUpToDate() {
        return isGeometryUpToDate() && isOffsetUpToDate();
    }
    
    /// Computes the anchor point used to paint the scale title.
    ///
    /// Normal cases reuse the cached axis endpoints. When the visible range collapses to a single
    /// value, the method falls back to the plot rectangle and axis/projector orientation so the
    /// title still chooses a stable edge and direction.
    @Override
    protected void computeTitleLocation(DoublePoint location) {
        requireScaleGeometry();
        double placement = super.scale.getTitleState().getPlacement();
        DoublePoints axisPoints = getCachedAxisPoints();
        double titleAngle;
        if (super.scale.getAxis().getVisibleRange().getLength() != 0.0)
            titleAngle = getCachedAxisAngle();
        else {
            Rectangle plotRect = super.scale.getPlotRect();
            if (super.scale.getAxis().isXAxis() != super.scale.getChart().isProjectorReversed()) {
                if (!super.scale.getAxis().isReversed()) {
                    axisPoints.setX(0, plotRect.x);
                    axisPoints.setX(1, plotRect.x + plotRect.width - 1);
                    titleAngle = 0.0;
                } else {
                    axisPoints.setX(0, plotRect.x + plotRect.width - 1);
                    axisPoints.setX(1, plotRect.x);
                    titleAngle = 180.0;
                }
            } else if (super.scale.getAxis().isReversed()) {
                axisPoints.setY(0, plotRect.y);
                axisPoints.setY(1, plotRect.y + plotRect.height - 1);
                titleAngle = 270.0;
            } else {
                axisPoints.setY(0, plotRect.y + plotRect.height - 1);
                axisPoints.setY(1, plotRect.y);
                titleAngle = 90.0;
            }
        }

        double anchorAngle;
        if (placement == 100.0) {
            location.x = axisPoints.getX(1);
            location.y = axisPoints.getY(1);
            anchorAngle = titleAngle;
        } else if (placement == 0.0) {
            location.x = axisPoints.getX(0);
            location.y = axisPoints.getY(0);
            anchorAngle = titleAngle + 180.0;
        } else {
            location.x = axisPoints.getX(0) + (axisPoints.getX(1) - axisPoints.getX(0)) * placement / 100.0;
            location.y = axisPoints.getY(0) + (axisPoints.getY(1) - axisPoints.getY(0)) * placement / 100.0;
            anchorAngle = this.getAxisAngle(0.0);
        }

        Dimension2D titleSize = super.scale.getTitleState().getSize2D(true);
        GraphicUtil.computeTextLocation(location, anchorAngle, getTitleDistance(), titleSize.getWidth(), titleSize.getHeight());
    }
    
    /// Verifies that geometry and stacking-offset caches are ready before the base pre-draw hook.
    @Override
    void requireUpToDate() {
        requireScaleGeometry();
        requireScaleOffset();
        super.requireUpToDate();
    }
    
    /// Paints the scale after translating by the cached stacking offset.
    @Override
    protected void draw(Graphics g) {
        requireScaleOffset();
        int xOffset = getXOffset();
        int yOffset = getYOffset();
        if (xOffset != 0 || yOffset != 0)
            g.translate(xOffset, yOffset);
        try {
            super.draw(g);
        } finally {
            if (xOffset != 0 || yOffset != 0)
                g.translate(-xOffset, -yOffset);
        }
    }
    
    /// Draws the cached axis line.
    ///
    /// One-pixel axes are painted through [#drawSnappedAxis(Graphics2D, DoublePoints)] so the line
    /// lands on exact device pixels and avoids blurry half-pixel anti-aliasing.
    @Override
    protected void drawAxis(Graphics g) {
        requireScaleGeometry();
        DoublePoints axisPoints = getCachedAxisPoints();
        if (g instanceof Graphics2D g2 && isDevicePixelAxis(super.scale.getAxisStyle(), axisPoints)) {
            var previousAntiAliasing = g2.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
            var previousStrokeControl = g2.getRenderingHint(RenderingHints.KEY_STROKE_CONTROL);
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_NORMALIZE);
            try {
                drawSnappedAxis(g2, axisPoints);
                return;
            } finally {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        (previousAntiAliasing != null) ? previousAntiAliasing : RenderingHints.VALUE_ANTIALIAS_DEFAULT);
                g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,
                        (previousStrokeControl != null) ? previousStrokeControl : RenderingHints.VALUE_STROKE_DEFAULT);
            }
        }
        super.scale.getAxisStyle().drawLine(g, axisPoints.getX(0), axisPoints.getY(0), axisPoints.getX(1), axisPoints.getY(1));
    }
    
    /// Ensures cached geometry and cached offset are both available before dependent work runs.
    @Override
    void ensureUpToDate() {
        ensureScaleGeometry();
        ensureScaleOffset();
        super.ensureUpToDate();
    }
    
    /// Returns whether this configuration is currently driven by a cartesian projector.
    final boolean usesCartesianProjector() {
        return super.scale.getProjector() instanceof CartesianProjector;
    }
    
    /// Returns the owning scale's projector as a [CartesianProjector].
    final CartesianProjector getCartesianProjector() {
        return (CartesianProjector) super.scale.getProjector();
    }
    
    /// Returns the painted bounds of the axis line itself.
    @Override
    protected Rectangle2D getAxisBounds(Rectangle2D bounds) {
        requireScaleGeometry();
        DoublePoints axisPoints = getCachedAxisPoints();
        return super.scale.getAxisStyle().getBounds(axisPoints.getXValues(), axisPoints.getYValues(), 2, false, true, bounds);
    }
    
    /// Returns the axis length in display units.
    ///
    /// Cartesian projectors expose axis length directly because parallel axes may be offset beyond
    /// the raw endpoint distance. Other projectors fall back to the Euclidean distance between the
    /// cached endpoints.
    @Override
    protected int getScaleLength() {
        requireScaleGeometry();
        if (usesCartesianProjector())
            return (int) getCartesianProjector().getAxisLength(super.scale.getPlotRect(), super.scale.getAxis());
        DoublePoints axisPoints = getCachedAxisPoints();
        if (axisPoints == null)
            return 0;
        double deltaX = axisPoints.getX(1) - axisPoints.getX(0);
        double deltaY = axisPoints.getY(1) - axisPoints.getY(0);
        return (int) Math.sqrt(deltaX * deltaX + deltaY * deltaY);
    }
    
    /// Returns whether the cached axis geometry still matches the chart's current scale state.
    final boolean isGeometryUpToDate() {
        return geometryUpToDate && super.scale.getChart().scalesUpToDate;
    }
    
    /// Verifies that [#axisPoints] and [#axisAngle] have been computed for the current scale state.
    final void requireScaleGeometry() {
        if (isGeometryUpToDate())
            return;
        throw new IllegalStateException("missing updateScaleGeometry()");
    }
    
    /// Returns the cached display-space angle of the axis line.
    final double getCachedAxisAngle() {
        requireScaleGeometry();
        return axisAngle;
    }
    
    /// Returns the cached display-space endpoints of the axis line.
    final DoublePoints getCachedAxisPoints() {
        requireScaleGeometry();
        return axisPoints;
    }
    
    /// Invalidates the cached axis geometry.
    final void invalidateScaleGeometry() {
        geometryUpToDate = false;
    }
    
    /// Ensures the cached axis geometry is available.
    final void ensureScaleGeometry() {
        if (!geometryUpToDate)
            updateScaleGeometry();
    }
    
    /// Returns whether the cached stacking offset still matches the chart's current scale state.
    final boolean isOffsetUpToDate() {
        return offsetUpToDate && super.scale.getChart().scalesUpToDate;
    }
    
    /// Verifies that [#xOffset] and [#yOffset] have been computed for the current scale state.
    final void requireScaleOffset() {
        if (isOffsetUpToDate())
            return;
        throw new IllegalStateException("missing updateScaleOffset()");
    }
    
    /// Returns the cached horizontal stacking offset.
    final int getXOffset() {
        requireScaleOffset();
        return xOffset;
    }
    
    /// Returns the cached vertical stacking offset.
    final int getYOffset() {
        requireScaleOffset();
        return yOffset;
    }
    
    /// Invalidates the cached stacking offset.
    final void invalidateScaleOffset() {
        offsetUpToDate = false;
    }
    
    /// Ensures the cached stacking offset is available.
    final void ensureScaleOffset() {
        if (!offsetUpToDate)
            updateScaleOffset();
    }
    
    /// Returns the distance used to place the scale title away from the axis line.
    ///
    /// Titles anchored to either end of the axis use only the configured title offset. Titles
    /// placed along the interior of the axis also account for label offset and for the current
    /// label box extent perpendicular to the axis.
    @Override
    int getTitleDistance() {
        requireScaleGeometry();
        double placement = super.scale.getTitleState().getPlacement();
        if (placement != 100.0)
            if (placement != 0.0) {
                int titleDistance = super.scale.getTitleOffset() + super.scale.getLabelDistance();
                if (super.scale.isLabelVisible()) {
                    double labelExtent = (!isHorizontalAxis())
                            ? super.scale.getSteps().getMaxLabelWidth()
                            : super.scale.getSteps().getMaxLabelHeight();
                    titleDistance = (int) (titleDistance + labelExtent);
                }
                return titleDistance;
            }
        return super.scale.getTitleOffset();
    }
    
    /// Recomputes the cached axis endpoints and their display-space angle.
    ///
    /// Symmetric polar charts expand the radial geometry symmetrically around zero even when the
    /// visible y range is stored as a positive max-only interval.
    private void computeScaleGeometry() {
        double[] axisValues;
        if (super.scale.getChart().getType() == 2 && ((PolarProjector) super.scale.getProjector()).isSymmetric()) {
            axisValues = new double[] {-super.scale.getAxis().getVisibleMax(), super.scale.getAxis().getVisibleMax()};
        } else {
            axisValues = new double[] {super.scale.getAxis().getVisibleMin(), super.scale.getAxis().getVisibleMax()};
        }

        axisPoints = super.scale.projectValues(axisValues, 2);
        axisAngle = GraphicUtil.pointAngleDeg(
                axisPoints.getX(0), axisPoints.getY(0), axisPoints.getX(1), axisPoints.getY(1));
    }
    
    /// Refreshes the cached axis geometry for the current scale state.
    private void updateScaleGeometry() {
        computeScaleGeometry();
        geometryUpToDate = true;
    }
    
    /// Returns whether the cached axis line is horizontal in display space.
    private boolean isHorizontalAxis() {
        requireScaleGeometry();
        return axisAngle == 0.0 || axisAngle == 180.0;
    }
    
    /// Recomputes the cached stacking offset for parallel cartesian axes.
    ///
    /// Only cartesian axes that do not cross another axis participate in this translation. The
    /// offset accumulates the bounds of previously visible sibling scales that share the same
    /// effective side.
    private void computeScaleOffset() {
        xOffset = 0;
        yOffset = 0;
        if (!usesCartesianProjector() || super.scale.requiresDynamicPointPreparation())
            return;

        Scale siblingScale = super.scale.getPreviousParallelScale();
        while (siblingScale != null && !siblingScale.isVisible())
            siblingScale = siblingScale.getPreviousParallelScale();
        if (siblingScale == null)
            return;

        double siblingAxisAngle =
                ((RectangularScaleConfiguration) siblingScale.getScaleConfiguration()).getCartesianAxisAngle();
        int xDirection = (int) MathUtil.cosDeg(siblingAxisAngle);
        int yDirection = (int) MathUtil.sinDeg(siblingAxisAngle);
        while (siblingScale != null) {
            Rectangle2D siblingBounds = siblingScale.getBoundsUsingCache(null);
            int siblingXOffset = ((int) siblingBounds.getWidth() + 11) * xDirection;
            int siblingYOffset = -((int) siblingBounds.getHeight() + 11) * yDirection;
            xOffset += siblingXOffset;
            yOffset += siblingYOffset;

            do {
                siblingScale = siblingScale.getPreviousParallelScale();
            } while (siblingScale != null && !siblingScale.isVisible());
        }
    }
    
    /// Refreshes the cached stacking offset for the current scale state.
    private void updateScaleOffset() {
        computeScaleOffset();
        offsetUpToDate = true;
    }

    /// Paints a one-device-pixel axis as a filled snapped rectangle instead of a stroked line.
    private void drawSnappedAxis(Graphics2D g2, DoublePoints points) {
        Paint paint = super.scale.getAxisStyle().getStrokePaint();
        if (paint == null)
            return;

        DevicePixelSnapper devicePixels = new DevicePixelSnapper(g2);
        Paint previousPaint = g2.getPaint();
        try {
            g2.setPaint(paint);
            if (Math.abs(points.getX(0) - points.getX(1)) <= Math.abs(points.getY(0) - points.getY(1))) {
                int x = devicePixels.snapX(points.getX(0));
                int top = Math.min(devicePixels.snapY(points.getY(0)), devicePixels.snapY(points.getY(1)));
                int bottom = Math.max(devicePixels.snapY(points.getY(0)), devicePixels.snapY(points.getY(1)));
                g2.fill(devicePixels.toUserRect(x, top, 1, bottom - top + 1));
            } else {
                int y = devicePixels.snapY(points.getY(0));
                int left = Math.min(devicePixels.snapX(points.getX(0)), devicePixels.snapX(points.getX(1)));
                int right = Math.max(devicePixels.snapX(points.getX(0)), devicePixels.snapX(points.getX(1)));
                g2.fill(devicePixels.toUserRect(left, y, right - left + 1, 1));
            }
        } finally {
            g2.setPaint(previousPaint);
        }
    }

    /// Returns whether the axis can be rendered losslessly as a snapped one-device-pixel line.
    private static boolean isDevicePixelAxis(PlotStyle style, DoublePoints points) {
        if (style == null || points == null)
            return false;
        if (!(style.getStroke() instanceof BasicStroke stroke) || stroke.getLineWidth() > 1.0f)
            return false;
        return Double.isFinite(points.getX(0))
                && Double.isFinite(points.getY(0))
                && Double.isFinite(points.getX(1))
                && Double.isFinite(points.getY(1));
    }
}
