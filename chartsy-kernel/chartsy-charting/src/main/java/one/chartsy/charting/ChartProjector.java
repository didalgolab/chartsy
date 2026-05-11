package one.chartsy.charting;

import java.awt.Rectangle;
import java.awt.Shape;

/// Translates between chart data coordinates and display geometry for one chart type.
///
/// Implementations such as Cartesian, polar, and radar projectors encapsulate the chart-type
/// specific mapping that [Chart], [Scale], [Grid], renderers, and interactors rely on. The
/// projector operates on a chart-specific `rect` together with the current [CoordinateSystem] and
/// mutates [DoublePoints] in place for efficient bulk conversion.
public interface ChartProjector {

    /// Returns the usable display-space length for `axis` inside `rect`.
    ///
    /// Renderers and scale configurations use this to size bars, ticks, and layout decisions along
    /// the projected axis direction.
    ///
    /// @param rect the projector bounds in display coordinates
    /// @param axis the axis whose display extent is being queried
    /// @return the projected length available for that axis
    double getAxisLength(Rectangle rect, Axis axis);

    /// Returns the display shape representing `window`.
    ///
    /// The result is used for clipping, hit testing, and interaction overlays. Cartesian
    /// implementations typically return a rectangle, while polar implementations may return sectors
    /// or polygons.
    ///
    /// @param window the data-space window to project
    /// @param rect the projector bounds in display coordinates
    /// @param coords the coordinate system whose axes define the mapping
    /// @return the projected shape
    Shape getShape(DataWindow window, Rectangle rect, CoordinateSystem coords);

    /// Returns the display shape for one constant-axis line or band segment.
    ///
    /// `axisType` uses the [Axis#X_AXIS] or [Axis#Y_AXIS] constants. `value` supplies the constant
    /// coordinate on that axis, while `interval` supplies the span on the opposite axis.
    ///
    /// @param value the constant coordinate on the axis designated by `axisType`
    /// @param interval the span on the opposite axis
    /// @param axisType the axis identifier, typically [Axis#X_AXIS] or [Axis#Y_AXIS]
    /// @param rect the projector bounds in display coordinates
    /// @param coords the coordinate system whose axes define the mapping
    /// @return the projected shape
    Shape getShape(double value, DataInterval interval, int axisType, Rectangle rect,
            CoordinateSystem coords);

    /// Returns the display shape for one constant-axis line using the current visible span of the
    /// opposite axis.
    ///
    /// @param value the constant coordinate on the axis designated by `axisType`
    /// @param axisType the axis identifier, typically [Axis#X_AXIS] or [Axis#Y_AXIS]
    /// @param rect the projector bounds in display coordinates
    /// @param coords the coordinate system whose axes define the mapping
    /// @return the projected shape
    Shape getShape(double value, int axisType, Rectangle rect, CoordinateSystem coords);

    /// Moves one display-space point by `distance` along the projected direction of `axis`.
    ///
    /// This is used for layout tasks such as exploding pie slices or offsetting annotations along
    /// the local axis direction.
    ///
    /// @param rect the projector bounds in display coordinates
    /// @param axis the axis that defines the movement direction
    /// @param point the point to mutate in place
    /// @param distance the display-space distance to travel
    void shiftAlongAxis(Rectangle rect, Axis axis, DoublePoint point, double distance);

    /// Converts the supplied display-space points into data coordinates in place.
    ///
    /// @param points the points to mutate
    /// @param rect the projector bounds in display coordinates
    /// @param coords the coordinate system whose axes define the mapping
    void toData(DoublePoints points, Rectangle rect, CoordinateSystem coords);

    /// Converts a display-space rectangle into the corresponding data-space window.
    ///
    /// @param selection the display-space rectangle to interpret
    /// @param rect the projector bounds in display coordinates
    /// @param coords the coordinate system whose axes define the mapping
    /// @return a new data window covering the selected display region
    DataWindow toDataWindow(Rectangle selection, Rectangle rect, CoordinateSystem coords);

    /// Converts the supplied data-space points into display coordinates in place.
    ///
    /// @param points the points to mutate
    /// @param rect the projector bounds in display coordinates
    /// @param coords the coordinate system whose axes define the mapping
    void toDisplay(DoublePoints points, Rectangle rect, CoordinateSystem coords);

    /// Converts `window` into a display-space rectangle that encloses its projection.
    ///
    /// @param window the data-space window to convert
    /// @param rect the projector bounds in display coordinates
    /// @param coords the coordinate system whose axes define the mapping
    /// @return a new display-space rectangle enclosing the projected window
    Rectangle toRectangle(DataWindow window, Rectangle rect, CoordinateSystem coords);
}
