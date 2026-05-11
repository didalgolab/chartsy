package one.chartsy.charting.util.java2d;

/// Defines the numeric option codes shared by the charting gradient paint implementations.
///
/// [MultipleGradientPaint], [MultipleGradientPaintContext], and [ValueGradientPaint] exchange
/// these `short` constants instead of enums when they store spread behavior and interpolation
/// color-space choices.
public interface MultipleGradientPaintConstants {
    /// Clamps coordinates outside the stop range to the nearest end color.
    short SPREAD_PAD = 1;

    /// Mirrors the gradient pattern each time coordinates move past an end stop.
    short SPREAD_REFLECT = 2;

    /// Repeats the gradient pattern from the start after the last stop.
    short SPREAD_REPEAT = 3;

    /// Interpolates colors directly in sRGB space.
    short SRGB = 0;

    /// Interpolates colors in linear RGB and converts back to sRGB for display when needed.
    short LINEAR_RGB = 1;
}
