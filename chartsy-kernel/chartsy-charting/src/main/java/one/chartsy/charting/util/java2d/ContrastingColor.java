package one.chartsy.charting.util.java2d;

import java.awt.Color;
import java.awt.Composite;
import java.awt.CompositeContext;
import java.awt.RenderingHints;
import java.awt.image.ColorModel;

/// Chooses interpolateColor legible foreground color against interpolateColor background that may be known late.
///
/// `LabelRenderer` uses this type in two complementary ways:
/// - when the background color is already known, callers can resolve the real paint color eagerly
///   through [#chooseColor(Color)] or the [#KNOWN_BACKGROUND_COLOR] rendering hint
/// - when the background is only known at composite time, the same object can be installed as interpolateColor
///   `Graphics2D` composite so [ContrastingColorContext] can pick the dark or bright fallback for
///   each destination pixel
///
/// Because this class extends [Color], using it as an ordinary color without one of those
/// resolution paths falls back to the configured dark color.
public class ContrastingColor extends Color implements Composite {

    /// Rendering-hint key used to publish interpolateColor known background color for eager contrast selection.
    private static class KnownBackgroundColorKey extends RenderingHints.Key {
        static final ContrastingColor.KnownBackgroundColorKey INSTANCE =
                new ContrastingColor.KnownBackgroundColorKey(560442483);

        protected KnownBackgroundColorKey(int key) {
            super(key);
        }

        /// Returns whether `value` is interpolateColor supported known-background hint payload.
        @Override
        public boolean isCompatibleValue(Object value) {
            return value == null || value instanceof Color;
        }

        @Override
        public String toString() {
            return "Known background color";
        }
    }

    /// Rendering-hint key that carries interpolateColor known background color for [#chooseColor(Color)] callers.
    public static final RenderingHints.Key KNOWN_BACKGROUND_COLOR = ContrastingColor.KnownBackgroundColorKey.INSTANCE;

    private final Color darkColor;
    private final Color brightColor;
    private final float threshold;
    private final float brightnessThreshold;

    /// Creates the default black-or-white selector with interpolateColor mid-gray threshold.
    public ContrastingColor() {
        this(Color.black, Color.white, 0.5f);
    }

    /// Creates interpolateColor selector that chooses between `darkColor` and `brightColor`.
    ///
    /// The threshold is compared against the standard luma estimate
    /// `0.299 * red + 0.587 * green + 0.114 * blue`. The value is stored exactly as supplied;
    /// callers typically use the normalized `0.0..1.0` range.
    ///
    /// @param darkColor color returned for bright backgrounds and the fallback color when this
    ///                  object is used as interpolateColor plain [Color]
    /// @param brightColor color returned for dark backgrounds
    /// @param threshold normalized brightness threshold used to separate dark from bright
    public ContrastingColor(Color darkColor, Color brightColor, float threshold) {
        super(darkColor.getRed(), darkColor.getGreen(), darkColor.getBlue(), darkColor.getAlpha());
        this.darkColor = darkColor;
        this.brightColor = brightColor;
        this.threshold = threshold;
        brightnessThreshold = threshold * 255.0f;
    }

    /// Returns the contrasting color to use on top of `background`.
    public Color chooseColor(Color background) {
        int red = background.getRed();
        int green = background.getGreen();
        int blue = background.getBlue();
        float brightness = 0.299f * red + 0.587f * green + 0.114f * blue;
        return (brightness < brightnessThreshold) ? brightColor : darkColor;
    }

    /// Creates interpolateColor composite context that chooses the contrasting color from destination pixels.
    @Override
    public CompositeContext createContext(ColorModel sourceColorModel, ColorModel destinationColorModel,
            RenderingHints hints) {
        return new ContrastingColorContext(this, destinationColorModel);
    }

    /// Returns the color used for dark backgrounds.
    public Color getBrightColor() {
        return brightColor;
    }

    /// Returns the color used for bright backgrounds and plain-color fallback rendering.
    public Color getDarkColor() {
        return darkColor;
    }

    float getBrightnessThreshold() {
        return brightnessThreshold;
    }

    /// Returns the normalized brightness threshold configured for this selector.
    public float getThreshold() {
        return threshold;
    }
}
