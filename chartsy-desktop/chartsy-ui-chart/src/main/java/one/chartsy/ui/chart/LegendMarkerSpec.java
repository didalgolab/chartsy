package one.chartsy.ui.chart;

import one.chartsy.ui.chart.plot.Marker;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Stroke;

public record LegendMarkerSpec(
        Kind kind,
        Color primaryColor,
        Color secondaryColor,
        Stroke stroke,
        Marker marker,
        int markerSize) {

    public enum Kind {
        LINE,
        BAR,
        MARKER
    }

    public LegendMarkerSpec {
        if (kind == null)
            throw new IllegalArgumentException("kind cannot be null");
        if (primaryColor == null)
            throw new IllegalArgumentException("primaryColor cannot be null");
        if (markerSize <= 0)
            markerSize = 10;
    }

    public static LegendMarkerSpec line(Color color, Stroke stroke) {
        return new LegendMarkerSpec(Kind.LINE, color, color, stroke, Marker.NONE, 10);
    }

    public static LegendMarkerSpec bar(Color color) {
        return new LegendMarkerSpec(Kind.BAR, color, color, new BasicStroke(1f), Marker.NONE, 10);
    }

    public static LegendMarkerSpec marker(Color color, Marker marker, int markerSize) {
        return new LegendMarkerSpec(Kind.MARKER, color, color, new BasicStroke(1f), marker, markerSize);
    }
}
