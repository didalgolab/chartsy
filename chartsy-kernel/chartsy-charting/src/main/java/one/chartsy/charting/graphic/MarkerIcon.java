package one.chartsy.charting.graphic;

import java.awt.Component;
import java.awt.Graphics;
import java.io.Serial;
import java.io.Serializable;

import javax.swing.Icon;

import one.chartsy.charting.PlotStyle;

/// Swing [Icon] that renders one chart [Marker] inside a square swatch.
///
/// The stored marker size uses the same half-size convention as [Marker#draw(Graphics, int, int,
/// int, PlotStyle)], so the icon reports a width and height of `2 * markerSize + 1`. When no
/// explicit style is configured, [#paintIcon(Component, Graphics, int, int)] derives one from the
/// hosting component's foreground and background colors.
public class MarkerIcon implements Icon, Serializable {
    @Serial
    private static final long serialVersionUID = 6690104347765005481L;

    private final Marker marker;
    private int markerSize;
    private PlotStyle markerStyle;

    /// Creates a square marker icon with half-size `3`.
    public MarkerIcon() {
        this(Marker.SQUARE, 3);
    }

    /// Creates an icon backed by `marker` and using the component-derived fallback style.
    ///
    /// @param marker marker painter used by this icon
    /// @param markerSize marker half-size used for both painting and icon dimensions
    public MarkerIcon(Marker marker, int markerSize) {
        this(marker, markerSize, null);
    }

    /// Creates an icon backed by `marker`.
    ///
    /// @param marker marker painter used by this icon
    /// @param markerSize marker half-size used for both painting and icon dimensions
    /// @param markerStyle explicit style to use, or `null` to derive one from the hosting
    ///     component during painting
    public MarkerIcon(Marker marker, int markerSize, PlotStyle markerStyle) {
        this.marker = marker;
        setMarkerSize(markerSize);
        setMarkerStyle(markerStyle);
    }

    /// Returns the backing marker painter.
    ///
    /// @return marker renderer used by this icon
    public final Marker getMarker() {
        return marker;
    }

    /// Returns the square icon height in pixels.
    @Override
    public int getIconHeight() {
        return markerSize * 2 + 1;
    }

    /// Returns the square icon width in pixels.
    @Override
    public int getIconWidth() {
        return markerSize * 2 + 1;
    }

    /// Returns the marker half-size used by this icon.
    ///
    /// @return half-size value passed to [Marker#draw(Graphics, int, int, int, PlotStyle)]
    public final int getMarkerSize() {
        return markerSize;
    }

    /// Returns the explicit style used when painting this icon.
    ///
    /// @return the retained explicit style, or `null` when painting falls back to the hosting
    ///     component colors
    public final PlotStyle getMarkerStyle() {
        return markerStyle;
    }

    /// Paints the marker centered inside this icon's square bounds.
    ///
    /// When no explicit marker style is set, the icon builds one from `c` by treating the
    /// component's foreground as the stroke paint and the background as the fill paint.
    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
        PlotStyle style = getMarkerStyle();
        if (style == null)
            style = new PlotStyle(c.getForeground(), c.getBackground());

        marker.draw(g, x + markerSize + 1, y + markerSize + 1, markerSize, style);
    }

    /// Changes the marker half-size used by this icon.
    ///
    /// @param markerSize new half-size value; the reported icon width and height become
    ///     `2 * markerSize + 1`
    public void setMarkerSize(int markerSize) {
        this.markerSize = markerSize;
    }

    /// Changes the explicit style used when painting this icon.
    ///
    /// @param markerStyle new explicit style, or `null` to derive colors from the hosting
    ///     component
    public void setMarkerStyle(PlotStyle markerStyle) {
        this.markerStyle = markerStyle;
    }
}
