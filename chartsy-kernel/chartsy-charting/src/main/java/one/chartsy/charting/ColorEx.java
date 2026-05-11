package one.chartsy.charting;

import one.chartsy.charting.util.ColorUtil;

import java.awt.Color;
import java.util.Objects;

/// Creates [Color] copies with a replaced alpha channel.
///
/// Financial overlays and demos use this helper when they need translucent variants of semantic
/// colors for [PlotStyle] fills, [ValueGradientPaint] ramps, and Swing backgrounds without
/// changing the original red, green, and blue channels.
///
/// Unlike [ColorUtil#setAlpha(Color, float)], this class accepts any floating-point opacity value
/// and clamps it into the closed `0.0f..1.0f` range before building the result. Its API is
/// intentionally minimal: it preserves RGB, discards the source alpha, and always returns a
/// distinct [Color] instance.
///
/// **Thread-safety:** lock-never. The class is stateless and operates only on immutable
/// [Color] values supplied by the caller.
public class ColorEx {

    /// Creates a stateless color helper instance.
    ///
    /// Instances carry no configuration and are interchangeable. Callers normally use the
    /// static methods on this class instead of retaining helper objects.
    public ColorEx() {
    }

    /// Returns a new [Color] with the source RGB channels and a replaced alpha channel.
    ///
    /// The `alpha` argument expresses opacity as a ratio. Values below `0.0f` are treated as
    /// fully transparent and values above `1.0f` are treated as fully opaque.
    ///
    /// The conversion rounds the clamped ratio to the nearest 8-bit alpha value with
    /// [Math#round(float)] after multiplying by `255`. The source color's existing alpha is not
    /// reused; only the red, green, and blue channels are copied into the result.
    ///
    /// Unlike [ColorUtil#setAlpha(Color, float)], this method does not reject out-of-range
    /// opacity values and does not reuse the input instance when the requested alpha already
    /// matches.
    ///
    /// @param color the base color that supplies the red, green, and blue channels
    /// @param alpha the requested opacity ratio, clamped to the inclusive `0.0f..1.0f` range
    /// @return a newly allocated color whose RGB matches `color` and whose alpha is the clamped,
    ///     rounded 8-bit form of `alpha`
    /// @throws NullPointerException if `color` is `null`
    public static Color setAlpha(Color color, float alpha) {
        Objects.requireNonNull(color, "color");

        if (alpha < 0f)
            alpha = 0f;
        else if (alpha > 1f)
            alpha = 1f;

        int alphaChannel = Math.round(alpha * 255f);
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), alphaChannel);
    }
}
