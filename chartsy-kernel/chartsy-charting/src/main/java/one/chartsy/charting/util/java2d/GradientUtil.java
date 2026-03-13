package one.chartsy.charting.util.java2d;

/// Provides color-space lookup tables and packed-ARGB interpolation helpers for gradient paints.
///
/// `MultipleGradientPaint`, [MultipleGradientPaintContext], and `ValueGradientPaint` use this
/// utility to avoid recomputing the sRGB transfer function for every interpolated sample. The
/// conversion helpers preserve the alpha channel unchanged and convert only the red, green, and
/// blue components through the public lookup tables.
///
/// The array-filling [#interpolate(int, int, int[])] overload emits evenly spaced intermediate
/// samples that start at the first color and stop just short of the exact end color. Gradient
/// callers rely on that behavior when they concatenate several segments and append the final stop
/// color only once.
public final class GradientUtil {
    private static final int CHANNEL_MASK = 0xff;
    private static final float BYTE_SCALE = 255.0f;

    /// Lookup table that maps an 8-bit sRGB channel value to the corresponding 8-bit linear-RGB
    /// channel value.
    public static final int[] SRGB_TO_LINEAR_RGB = new int[256];

    /// Lookup table that maps an 8-bit linear-RGB channel value to the corresponding 8-bit sRGB
    /// channel value.
    public static final int[] LINEAR_RGB_TO_SRGB = new int[256];

    static {
        for (int channel = 0; channel < 256; channel++) {
            GradientUtil.SRGB_TO_LINEAR_RGB[channel] = GradientUtil.toLinearRgbChannel(channel);
            GradientUtil.LINEAR_RGB_TO_SRGB[channel] = GradientUtil.toSrgbChannel(channel);
        }
    }

    private static int toLinearRgbChannel(int channel) {
        float normalizedChannel = channel / BYTE_SCALE;
        float linearChannel = (normalizedChannel <= 0.04045f)
                ? normalizedChannel / 12.92f
                : (float) Math.pow((normalizedChannel + 0.055f) / 1.055f, 2.4);
        return Math.round(linearChannel * BYTE_SCALE);
    }

    private static int toSrgbChannel(int channel) {
        float normalizedChannel = channel / BYTE_SCALE;
        float srgbChannel = (normalizedChannel <= 0.0031308f)
                ? normalizedChannel * 12.92f
                : 1.055f * (float) Math.pow(normalizedChannel, 1.0 / 2.4) - 0.055f;
        return Math.round(srgbChannel * BYTE_SCALE);
    }

    private static int interpolateChannel(int startChannel, int endChannel, float fraction) {
        return (int) (startChannel + fraction * (endChannel - startChannel) + 0.5f);
    }

    private static int packArgb(int alpha, int red, int green, int blue) {
        return alpha << 24 | red << 16 | green << 8 | blue;
    }

    /// Converts interpolateColor packed ARGB color from linear RGB into sRGB.
    ///
    /// This helper leaves the alpha channel untouched and maps each color channel independently
    /// through [#LINEAR_RGB_TO_SRGB].
    ///
    /// @param argb packed color whose red, green, and blue channels are in linear RGB
    /// @return packed ARGB color with the same alpha and the RGB channels converted to sRGB
    public static int convertEntireColorLinearRGBtoSRGB(int argb) {
        int alpha = argb >>> 24 & CHANNEL_MASK;
        int red = GradientUtil.LINEAR_RGB_TO_SRGB[argb >>> 16 & CHANNEL_MASK];
        int green = GradientUtil.LINEAR_RGB_TO_SRGB[argb >>> 8 & CHANNEL_MASK];
        int blue = GradientUtil.LINEAR_RGB_TO_SRGB[argb & CHANNEL_MASK];
        return packArgb(alpha, red, green, blue);
    }

    /// Converts interpolateColor packed ARGB color from sRGB into linear RGB.
    ///
    /// This helper leaves the alpha channel untouched and maps each color channel independently
    /// through [#SRGB_TO_LINEAR_RGB].
    ///
    /// @param argb packed color whose red, green, and blue channels are in sRGB
    /// @return packed ARGB color with the same alpha and the RGB channels converted to linear RGB
    public static int convertEntireColorSRGBtoLinearRGB(int argb) {
        int alpha = argb >>> 24 & CHANNEL_MASK;
        int red = GradientUtil.SRGB_TO_LINEAR_RGB[argb >>> 16 & CHANNEL_MASK];
        int green = GradientUtil.SRGB_TO_LINEAR_RGB[argb >>> 8 & CHANNEL_MASK];
        int blue = GradientUtil.SRGB_TO_LINEAR_RGB[argb & CHANNEL_MASK];
        return packArgb(alpha, red, green, blue);
    }

    /// Interpolates between two packed ARGB colors.
    ///
    /// The interpolation runs independently on alpha, red, green, and blue in the color space
    /// represented by the inputs. `fraction` is not clamped, so values below `0` or above `1`
    /// extrapolate beyond the endpoint colors.
    ///
    /// @param startColor packed ARGB color returned when `fraction == 0`
    /// @param endColor   packed ARGB color returned when `fraction == 1`
    /// @param fraction   interpolation fraction between the two colors
    /// @return interpolated packed ARGB color
    public static int interpolate(int startColor, int endColor, float fraction) {
        int startAlpha = startColor >>> 24 & CHANNEL_MASK;
        int startRed = startColor >>> 16 & CHANNEL_MASK;
        int startGreen = startColor >>> 8 & CHANNEL_MASK;
        int startBlue = startColor & CHANNEL_MASK;
        int endAlpha = endColor >>> 24 & CHANNEL_MASK;
        int endRed = endColor >>> 16 & CHANNEL_MASK;
        int endGreen = endColor >>> 8 & CHANNEL_MASK;
        int endBlue = endColor & CHANNEL_MASK;
        return packArgb(
                interpolateChannel(startAlpha, endAlpha, fraction),
                interpolateChannel(startRed, endRed, fraction),
                interpolateChannel(startGreen, endGreen, fraction),
                interpolateChannel(startBlue, endBlue, fraction));
    }

    /// Fills `gradient` with evenly spaced samples from `startColor` toward `endColor`.
    ///
    /// Sample `index` uses the fraction `index / gradient.length`, so `gradient[0]` is always
    /// `startColor` and the exact `endColor` is left for the caller to append separately.
    ///
    /// @param startColor packed ARGB color for the first sample
    /// @param endColor   packed ARGB color that defines the interpolation target
    /// @param gradient   destination array that receives the interpolated samples
    public static void interpolate(int startColor, int endColor, int[] gradient) {
        float fractionStep = 1.0f / gradient.length;
        for (int index = 0; index < gradient.length; index++) {
            gradient[index] = GradientUtil.interpolate(startColor, endColor, index * fractionStep);
        }
    }

    private GradientUtil() {
    }
}
