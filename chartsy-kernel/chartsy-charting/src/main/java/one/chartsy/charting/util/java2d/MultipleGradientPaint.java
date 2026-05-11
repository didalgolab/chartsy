package one.chartsy.charting.util.java2d;

import java.awt.Color;
import java.awt.Paint;
import java.awt.Transparency;
import java.awt.geom.AffineTransform;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serial;
import java.io.Serializable;
import java.util.Arrays;

/// Base class for the charting module's multi-stop gradient paints.
///
/// The base class owns the shared gradient configuration used by [LinearGradientPaint] and
/// [RadialGradientPaint]: copied stop and color arrays, spread behavior, interpolation color
/// space, an optional extra transform, and the [AdaptablePaint] flag that tells callers whether
/// the concrete paint should scale itself to the current user bounds.
///
/// The constructor is intentionally forgiving with stop positions. It clamps each supplied stop
/// into `0.0f..1.0f` and forces the sequence to be non-decreasing instead of rejecting out-of-range
/// or slightly unsorted input. Callers that need strict validation must do it before constructing
/// the paint.
///
/// ### Implementation Requirements
///
/// Subclasses are responsible for translating the shared normalized stop/color definition into a
/// Java2D [java.awt.PaintContext]. When context creation fails, the concrete paints in this module
/// fall back to [#interpolateColor(float)] to obtain a solid representative color.
public abstract class MultipleGradientPaint
        implements Paint, Serializable, AdaptablePaint, MultipleGradientPaintConstants {
    private static Color requireColor(Color color) {
        if (color == null)
            throw new IllegalArgumentException("Null color not allowed as color of MultipleGradientPaint");
        return color;
    }

    private static void validateColorSpace(short colorSpace) {
        if (colorSpace != LINEAR_RGB && colorSpace != SRGB)
            throw new IllegalArgumentException("Invalid colorspace for interpolation");
    }

    private static void validateSpreadMethod(short spreadMethod) {
        if (spreadMethod != SPREAD_PAD && spreadMethod != SPREAD_REFLECT && spreadMethod != SPREAD_REPEAT)
            throw new IllegalArgumentException("Invalid spread method");
    }

    private static float clampStop(float stop) {
        if (stop < 0.0f)
            return 0.0f;
        if (stop > 1.0f)
            return 1.0f;
        return stop;
    }

    private static float[] copyAndNormalizeStops(float[] stops) {
        float[] normalizedStops = Arrays.copyOf(stops, stops.length);
        float previousStop = Float.NEGATIVE_INFINITY;
        for (int index = 0; index < normalizedStops.length; index++) {
            float normalizedStop = clampStop(normalizedStops[index]);
            if (normalizedStop < previousStop)
                normalizedStop = previousStop;
            normalizedStops[index] = normalizedStop;
            previousStop = normalizedStop;
        }
        return normalizedStops;
    }

    private static Color[] copyColors(Color[] colors) {
        Color[] copiedColors = Arrays.copyOf(colors, colors.length);
        for (int index = 0; index < copiedColors.length; index++)
            copiedColors[index] = requireColor(copiedColors[index]);
        return copiedColors;
    }

    private static int interpolateRgb(int startRgb, int endRgb, float fraction, short colorSpace) {
        int interpolatedStart = startRgb;
        int interpolatedEnd = endRgb;
        if (colorSpace == LINEAR_RGB) {
            interpolatedStart = GradientUtil.convertEntireColorSRGBtoLinearRGB(interpolatedStart);
            interpolatedEnd = GradientUtil.convertEntireColorSRGBtoLinearRGB(interpolatedEnd);
        }
        int interpolatedRgb = GradientUtil.interpolate(interpolatedStart, interpolatedEnd, fraction);
        return (colorSpace == LINEAR_RGB)
                ? GradientUtil.convertEntireColorLinearRGBtoSRGB(interpolatedRgb)
                : interpolatedRgb;
    }

    private static void writeTransform(ObjectOutputStream out, AffineTransform transform) throws IOException {
        out.writeBoolean(transform != null);
        if (transform == null)
            return;

        out.writeDouble(transform.getScaleX());
        out.writeDouble(transform.getShearY());
        out.writeDouble(transform.getShearX());
        out.writeDouble(transform.getScaleY());
        out.writeDouble(transform.getTranslateX());
        out.writeDouble(transform.getTranslateY());
    }

    private static AffineTransform readTransform(ObjectInputStream in) throws IOException {
        if (!in.readBoolean())
            return null;

        double scaleX = in.readDouble();
        double shearY = in.readDouble();
        double shearX = in.readDouble();
        double scaleY = in.readDouble();
        double translateX = in.readDouble();
        double translateY = in.readDouble();
        return new AffineTransform(scaleX, shearY, shearX, scaleY, translateX, translateY);
    }

    private int transparency;
    float[] stops;
    Color[] colors;
    transient AffineTransform transform;
    short spreadMethod;
    short colorSpace;

    boolean adapting;

    /// Creates a gradient definition shared by the concrete gradient paint subclasses.
    ///
    /// The stop and color arrays are copied before they are stored. Stop positions are normalized
    /// in-place by clamping them into `0.0f..1.0f` and raising any backward step to the previous
    /// normalized stop.
    ///
    /// @param stops        normalized stop positions. Values outside `0.0f..1.0f` are clamped and values
    ///                                                      that would move backward are raised to the previous stop
    /// @param colors       gradient stop colors
    /// @param spreadMethod one of the `SPREAD_*` constants
    /// @param colorSpace   one of [#SRGB] or [#LINEAR_RGB]
    /// @param transform    optional extra transform applied after any adaptive-bounds transform
    /// @param adapting     whether the concrete paint should normalize itself to the current user
    ///                                                         bounds when callers provide [AdaptablePaint#KEY_USER_BOUNDS]
    MultipleGradientPaint(float[] stops, Color[] colors, short spreadMethod, short colorSpace,
                          AffineTransform transform, boolean adapting) {
        transparency = Transparency.OPAQUE;
        if (colors.length < 2)
            throw new IllegalArgumentException("User must specify at least 2 colors");
        validateColorSpace(colorSpace);
        validateSpreadMethod(spreadMethod);
        this.stops = copyAndNormalizeStops(stops);
        this.colors = copyColors(colors);
        this.colorSpace = colorSpace;
        this.spreadMethod = spreadMethod;
        this.transform = (transform == null) ? null : new AffineTransform(transform);
        this.adapting = adapting;
        initTransparency();
    }

    /// Creates a deep copy of another gradient definition.
    public MultipleGradientPaint(MultipleGradientPaint paint) {
        this(paint.getStops(), paint.getColors(), paint.getSpreadMethod(), paint.getColorSpace(),
                paint.getTransform(), paint.isAdapting());
    }

    /// Returns the color produced by this gradient at normalized position `position`.
    ///
    /// Positions before the first stop clamp to the first color and positions after the last stop
    /// clamp to the last color. Between stops, interpolation happens in the configured color space.
    /// If two adjacent normalized stops collapse to the same position, the later stop color wins.
    Color interpolateColor(float position) {
        if (position <= stops[0])
            return colors[0];

        int stopCount = stops.length;
        if (position >= stops[stopCount - 1])
            return colors[stopCount - 1];

        for (int stopIndex = 1; stopIndex < stopCount; stopIndex++) {
            if (position > stops[stopIndex])
                continue;

            while (stopIndex + 1 < stopCount && position == stops[stopIndex + 1])
                stopIndex++;

            float lowerStop = stops[stopIndex - 1];
            float upperStop = stops[stopIndex];
            if (upperStop == lowerStop)
                return colors[stopIndex];

            float fraction = (position - lowerStop) / (upperStop - lowerStop);
            int interpolatedRgb = interpolateRgb(colors[stopIndex - 1].getRGB(), colors[stopIndex].getRGB(),
                    fraction, colorSpace);
            return new Color(interpolatedRgb);
        }
        return Color.black;
    }

    /// Returns a copy of the configured stop colors.
    ///
    /// The returned array is detached from this paint, but the contained [Color] instances are the
    /// same immutable values retained by the paint.
    public final Color[] getColors() {
        return Arrays.copyOf(colors, colors.length);
    }

    /// Returns the interpolation color space code for this gradient.
    public final short getColorSpace() {
        return colorSpace;
    }

    /// Returns the spread-mode code for this gradient.
    public final short getSpreadMethod() {
        return spreadMethod;
    }

    /// Returns a copy of the normalized stop positions.
    ///
    /// The returned values already reflect the constructor's clamping and monotonicity rules.
    public final float[] getStops() {
        return Arrays.copyOf(stops, stops.length);
    }

    /// Returns a copy of the additional transform applied by this paint, if any.
    public final AffineTransform getTransform() {
        if (transform == null)
            return null;
        return new AffineTransform(transform);
    }

    @Override
    public final int getTransparency() {
        return transparency;
    }

    /// Recomputes the Java2D transparency classification from the configured colors.
    protected final void initTransparency() {
        transparency = Transparency.OPAQUE;
        for (Color color : colors) {
            if (color.getAlpha() != 255) {
                transparency = Transparency.TRANSLUCENT;
                break;
            }
        }
    }

    /// Returns whether callers should publish [AdaptablePaint#KEY_USER_BOUNDS] before creating a
    /// paint context.
    ///
    /// Concrete gradient paints in this module use the hint to reinterpret their geometry in
    /// normalized user-bounds coordinates.
    @Override
    public final boolean isAdapting() {
        return adapting;
    }

    @Serial
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        transform = readTransform(in);
    }

    @Serial
    private void writeObject(ObjectOutputStream out) throws IOException {
        out.defaultWriteObject();
        writeTransform(out, transform);
    }
}
