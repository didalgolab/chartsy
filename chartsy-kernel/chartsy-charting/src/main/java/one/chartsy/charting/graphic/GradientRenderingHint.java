package one.chartsy.charting.graphic;

import java.awt.Color;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serial;
import java.io.Serializable;
import java.util.Arrays;

import one.chartsy.charting.DisplayPoint;
import one.chartsy.charting.PlotStyle;

/// Applies a piecewise-linear value-to-color gradient to one rendered data point.
///
/// The hint keeps parallel numeric stops and colors. Values below the first stop use the first
/// color, values above the last stop use the last color, exact stop matches use the corresponding
/// color, and intermediate values interpolate linearly between the surrounding ARGB components.
///
/// Constructor arrays are copied on entry. Subclasses can override
/// [#getPointValue(DisplayPoint)] to drive the gradient from something other than
/// [DisplayPoint#getYData()].
///
/// ### API Note
///
/// Stop lookup delegates to [Arrays#binarySearch(double[], double)]. Callers should therefore
/// provide stop values in ascending order.
public class GradientRenderingHint implements DataRenderingHint, Serializable {
    private final double[] gradientValues;
    private Color[] gradientColors;
    private transient int[] alphaDeltas;
    private transient int[] redDeltas;
    private transient int[] greenDeltas;
    private transient int[] blueDeltas;

    /// Creates a gradient from paired numeric stops and colors.
    ///
    /// The supplied arrays are copied. Each stop must have a matching color, and at least two stops
    /// are required so interpolation has one segment to work with.
    ///
    /// @param values numeric stops used for clamping and interpolation
    /// @param colors colors paired with the corresponding stop values
    /// @throws IllegalArgumentException if the arrays have different lengths or contain fewer than
    ///     two entries
    public GradientRenderingHint(double[] values, Color[] colors) {
        if (values.length != colors.length)
            throw new IllegalArgumentException("colors and values must have the same size");
        if (values.length < 2)
            throw new IllegalArgumentException("Gradient must be defined by at least two colors");

        gradientValues = Arrays.copyOf(values, values.length);
        setGradientColors(colors);
    }

    /// Returns the color stop stored at `index`.
    ///
    /// @param index zero-based gradient stop index
    /// @return the retained immutable [Color] instance for that stop
    public final Color getGradientColor(int index) {
        return gradientColors[index];
    }

    /// Returns the numeric stop stored at `index`.
    ///
    /// @param index zero-based gradient stop index
    /// @return numeric value stored for that stop
    public final double getGradientValue(int index) {
        return gradientValues[index];
    }

    /// Resolves the effective style for `point` by recoloring the supplied baseline style.
    ///
    /// The current implementation replaces the fill paint when the baseline style paints fills and
    /// otherwise replaces the stroke paint.
    ///
    /// @param point display point whose scalar value selects the gradient segment
    /// @param style renderer-selected baseline style
    /// @return a recolored style derived from `style`
    @Override
    public PlotStyle getStyle(DisplayPoint point, PlotStyle style) {
        Color color = resolveColor(point);
        if (style.isFillOn())
            return style.setFillPaint(color);
        return style.setStrokePaint(color);
    }

    /// Returns the scalar used to locate `point` inside this gradient.
    ///
    /// The default implementation uses the point's y-data coordinate.
    ///
    /// @param point display point being styled
    /// @return scalar value compared against this hint's gradient stops
    protected double getPointValue(DisplayPoint point) {
        return point.getYData();
    }

    /// Returns the interpolated color for `point`.
    ///
    /// @param point display point whose scalar value selects the gradient segment
    /// @return the clamped or interpolated gradient color for that point
    protected Color resolveColor(DisplayPoint point) {
        double value = getPointValue(point);
        int stopIndex = Arrays.binarySearch(gradientValues, value);
        if (stopIndex == -1)
            return gradientColors[0];
        if (stopIndex == -gradientColors.length - 1)
            return gradientColors[gradientColors.length - 1];
        if (stopIndex >= 0)
            return gradientColors[stopIndex];

        stopIndex = -stopIndex - 2;
        int startArgb = gradientColors[stopIndex].getRGB();
        int endArgb = gradientColors[stopIndex + 1].getRGB();
        if (startArgb == endArgb)
            return gradientColors[stopIndex];

        int startAlpha = startArgb >> 24 & 0xFF;
        int startRed = startArgb >> 16 & 0xFF;
        int startGreen = startArgb >> 8 & 0xFF;
        int startBlue = startArgb & 0xFF;
        double startValue = gradientValues[stopIndex];
        double endValue = gradientValues[stopIndex + 1];
        float ratio = (float) ((value - startValue) / (endValue - startValue));
        int argb = (int) (startAlpha + alphaDeltas[stopIndex] * ratio + 0.5f) << 24
                | (int) (startRed + redDeltas[stopIndex] * ratio + 0.5f) << 16
                | (int) (startGreen + greenDeltas[stopIndex] * ratio + 0.5f) << 8
                | (int) (startBlue + blueDeltas[stopIndex] * ratio + 0.5f);
        return new Color(argb);
    }

    private void setGradientColors(Color[] colors) {
        gradientColors = Arrays.copyOf(colors, colors.length);
        updateChannelDeltas(gradientColors);
    }

    private void updateChannelDeltas(Color[] colors) {
        int segmentCount = colors.length - 1;
        alphaDeltas = new int[segmentCount];
        redDeltas = new int[segmentCount];
        greenDeltas = new int[segmentCount];
        blueDeltas = new int[segmentCount];

        int startArgb = colors[0].getRGB();
        for (int index = 0; index < segmentCount; index++) {
            int endArgb = colors[index + 1].getRGB();
            int startAlpha = startArgb >> 24 & 0xFF;
            int startRed = startArgb >> 16 & 0xFF;
            int startGreen = startArgb >> 8 & 0xFF;
            int startBlue = startArgb & 0xFF;
            alphaDeltas[index] = (endArgb >> 24 & 0xFF) - startAlpha;
            redDeltas[index] = (endArgb >> 16 & 0xFF) - startRed;
            greenDeltas[index] = (endArgb >> 8 & 0xFF) - startGreen;
            blueDeltas[index] = (endArgb & 0xFF) - startBlue;
            startArgb = endArgb;
        }
    }

    @Serial
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        updateChannelDeltas(gradientColors);
    }
}
