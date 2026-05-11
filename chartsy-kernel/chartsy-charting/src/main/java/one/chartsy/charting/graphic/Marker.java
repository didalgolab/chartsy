package one.chartsy.charting.graphic;

import java.awt.Graphics;

import one.chartsy.charting.PlotStyle;

/// Device-space glyph painter for one chart marker.
///
/// Renderers, marker hints, and [MarkerIcon] use this functional interface to paint one symbol at
/// a point that is already projected into display coordinates. The supplied [PlotStyle] owns the
/// actual line and fill primitives, while the marker chooses the geometry to draw around the
/// center point.
///
/// Built-in marker implementations interpret `size` as a half-size value, so their nominal bounds
/// span `2 * size + 1` pixels on each axis. Current built-in scatter and polyline renderers treat
/// [#NONE] as an explicit "do not draw" marker.
@FunctionalInterface
public interface Marker {

    /// Square marker centered on the target point.
    Marker SQUARE = (Graphics g, int x, int y, int size, PlotStyle style) -> {
        style.plotSquare(g, x, y, size);
    };

    /// Diamond marker centered on the target point.
    Marker DIAMOND = (Graphics g, int x, int y, int size, PlotStyle style) -> {
        int[] xx = new int[]{x, x + size, x, x - size, x};
        int[] yy = new int[]{y - size, y, y + size, y, y - size};
        style.plotPoints(g, xx, yy, 5);
    };

    /// Circle marker centered on the target point.
    Marker CIRCLE = (Graphics g, int x, int y, int size, PlotStyle style) -> {
        style.plotCircle(g, x, y, size);
    };

    /// Plus-sign marker centered on the target point.
    Marker PLUS = (Graphics g, int x, int y, int size, PlotStyle style) -> {
        style.drawLine(g, x, y - size, x, y + size);
        style.drawLine(g, x - size, y, x + size, y);
    };

    /// Diagonal cross marker centered on the target point.
    Marker CROSS = (Graphics g, int x, int y, int size, PlotStyle style) -> {
        style.drawLine(g, x - size, y - size, x + size, y + size);
        style.drawLine(g, x + size, y - size, x - size, y + size);
    };

    /// Triangle marker centered on the target point.
    Marker TRIANGLE = (Graphics g, int x, int y, int size, PlotStyle style) -> {
        int[] xx = new int[]{x, x + size, x - size, x};
        int[] yy = new int[]{y - size, y + size, y + size, y - size};
        style.plotPoints(g, xx, yy, 4);
    };

    /// No-op marker used to suppress marker painting without using `null`.
    Marker NONE = (Graphics g, int x, int y, int size, PlotStyle style) -> {
    };

    /// Returns a marker that paints each supplied marker in encounter order.
    ///
    /// The backing varargs array is retained rather than copied, so later caller changes to that
    /// array are reflected by the returned marker.
    ///
    /// @param markers markers to overlay at the same point
    /// @return a composite marker that replays `markers` in order
    static Marker createCompoundMarker(Marker... markers) {
        return (Graphics g, int x, int y, int size, PlotStyle style) -> {
            for (Marker marker : markers)
                marker.draw(g, x, y, size, style);
        };
    }

    /// Returns a marker that paints each marker produced by `markers` in encounter order.
    ///
    /// The iterable is traversed on every draw call and is retained rather than copied. Supply an
    /// iterable that can be iterated repeatedly.
    ///
    /// @param markers markers to overlay at the same point
    /// @return a composite marker that iterates `markers` for each draw
    static Marker createCompoundMarker(Iterable<Marker> markers) {
        return (Graphics g, int x, int y, int size, PlotStyle style) -> {
            for (Marker marker : markers)
                marker.draw(g, x, y, size, style);
        };
    }

    /// Paints this marker centered at (`x`, `y`).
    ///
    /// Built-in markers interpret `size` as a half-size value and therefore occupy approximately
    /// `2 * size + 1` pixels horizontally and vertically.
    ///
    /// @param g     destination graphics
    /// @param x     x-coordinate of the marker center in display space
    /// @param y     y-coordinate of the marker center in display space
    /// @param size  marker half-size in pixels
    /// @param style plot style that should supply the actual drawing primitives
    void draw(Graphics g, int x, int y, int size, PlotStyle style);
}
