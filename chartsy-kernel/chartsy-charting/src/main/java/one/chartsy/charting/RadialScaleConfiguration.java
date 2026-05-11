package one.chartsy.charting;

import java.awt.BasicStroke;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.util.Objects;

/// Scale-configuration strategy for radial axes in polar and radar charts.
///
/// [PolarConfig] selects this implementation for every y-axis scale, where the logical axis is not
/// a rectangle edge but a radial spoke extending outward from the polar origin. Most drawing and
/// layout behavior is inherited from [RectangularScaleConfiguration], which already knows how to
/// place a straight axis line, ticks, labels, and titles from two cached display points.
///
/// This specialization adjusts the two places where ordinary rectangular assumptions are not
/// enough:
/// - [#contains(Point2D)] hit-tests against the stroked radial axis line instead of the full scale
///   bounds rectangle.
/// - [#createSteps()] installs [Scale.RadialSteps], which formats symmetric polar ranges by
///   absolute distance from the origin rather than by signed value.
class RadialScaleConfiguration extends RectangularScaleConfiguration {

    /// Creates the default radial-axis configuration.
    RadialScaleConfiguration() {
    }

    /// Returns whether `point` lies on the interactive hit strip of the rendered radial axis line.
    ///
    /// The superclass caches the axis geometry as two display points. This implementation builds a
    /// line segment from those points and widens it with the same effective thickness that the
    /// scale uses for its labels and optional title, so scale hit detection tracks the visible
    /// radial spoke rather than the rectangular bounds used by cartesian axes.
    @Override
    public boolean contains(Point2D point) {
        ensureScaleGeometryUpdated();
        Line2D axisLine = createAxisLine();
        float hitWidth = getAxisHitWidth();
        BasicStroke hitStroke = new BasicStroke(hitWidth, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER);
        return hitStroke.createStrokedShape(axisLine).contains(point.getX(), point.getY());
    }

    /// Creates the step calculator used by radial axes.
    ///
    /// [Scale.RadialSteps] keeps the normal step-generation algorithm but changes label formatting
    /// for symmetric polar projection so matching positive and negative radii display the same
    /// absolute-distance label.
    @Override
    protected Scale.Steps createSteps() {
        Scale scale = Objects.requireNonNull(super.scale, "scale");
        return scale.new RadialSteps();
    }

    /// Returns the cached display-space line that represents this radial axis.
    private Line2D createAxisLine() {
        DoublePoints axisPoints = super.getCachedAxisPoints();
        return new Line2D.Double(axisPoints.getX(0), axisPoints.getY(0), axisPoints.getX(1), axisPoints.getY(1));
    }

    /// Returns the thickness of the interactive hit strip around the axis line.
    ///
    /// When the scale title is present, the full decorated axis thickness from [#t()] is used so
    /// the hit target still covers the occupied strip. Otherwise the width falls back to the label
    /// offset maintained directly on [Scale].
    private float getAxisHitWidth() {
        return super.scale.hasTitle() ? super.getTitleDistance() : super.scale.getLabelDistance();
    }

    /// Verifies that the superclass has already computed the cached axis geometry.
    private void ensureScaleGeometryUpdated() {
        super.requireScaleGeometry();
    }
}
