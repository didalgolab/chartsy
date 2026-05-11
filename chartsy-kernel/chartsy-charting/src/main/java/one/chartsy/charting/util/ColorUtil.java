package one.chartsy.charting.util;

import java.awt.Color;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/// Provides the charting module's shared named-color registry and HSB-based color utilities.
///
/// {@link one.chartsy.charting.ColorData} extends this type to expose named constants backed by the
/// same registry, while renderers and Swing helpers use the static methods to derive highlight,
/// shadow, and translucent variants from an existing base color.
///
/// The registry is populated at class initialization with CSS-style names plus a small set of AWT
/// aliases. Calls to {@link #addColor(String, Color)} update that shared registry for the entire
/// charting module.
///
/// **Thread-safety:** direct registry operations synchronize on the class object. HSB-based
/// transformations reuse a shared scratch buffer and synchronize on that buffer. The map returned by
/// {@link #getColorMap()} is an unmodifiable live view backed by mutable shared state rather than a
/// snapshot, so callers that need a stable copy should materialize one immediately.
public class ColorUtil {
    private static Map<String, Color> colorsByName;
    private static Map<Color, String> namesByColor;
    private static final boolean preferAwtAliases = false;
    private static final float[] hsbBuffer = new float[3];
    
    static {
        initializeColorRegistry();
    }
    
    private static synchronized void initializeColorRegistry() {
        if (ColorUtil.colorsByName != null) {
            ColorUtil.colorsByName.clear();
            ColorUtil.namesByColor.clear();
        } else {
            ColorUtil.colorsByName = new HashMap<>(211);
            ColorUtil.namesByColor = new HashMap<>(211);
        }
        if (ColorUtil.preferAwtAliases) {
            ColorUtil.registerAwtAliases();
            ColorUtil.registerCssColors();
        } else {
            ColorUtil.registerCssColors();
            ColorUtil.registerAwtAliases();
        }
    }
    
    private static Color fromHsb(float hue, float saturation, float brightness, int alpha) {
        return (alpha == 255)
                ? Color.getHSBColor(hue, saturation, brightness)
                : ColorUtil.withAlpha(Color.HSBtoRGB(hue, saturation, brightness), alpha);
    }
    
    private static Color withAlpha(int rgb, int alpha) {
        return new Color(rgb & 0xffffff | alpha << 24, true);
    }
    
    private static void requireUnitInterval(String name, float value) {
        if (value >= 0.0f)
            if (value <= 1.0f)
                return;
        throw new IllegalArgumentException(
                name + " value must be in the range [0,1]");
    }
    
    /// Registers a color name in the shared registry.
    ///
    /// The key is stored exactly as supplied; the registry does not normalize case or synthesize
    /// aliases beyond the names that were explicitly registered. Re-registering a name replaces the
    /// previous mapping.
    ///
    /// @param name the registry key to expose
    /// @param color the color value associated with `name`
    public static synchronized void addColor(String name, Color color) {
        ColorUtil.colorsByName.put(name, color);
        ColorUtil.namesByColor.put(color, name);
    }
    
    private static void registerCssColors() {
        ColorUtil.addColor("aliceblue", new Color(240, 248, 255));
        ColorUtil.addColor("antiquewhite", new Color(250, 235, 215));
        ColorUtil.addColor("aqua", Color.cyan);
        ColorUtil.addColor("aquamarine", new Color(127, 255, 212));
        ColorUtil.addColor("azure", new Color(240, 255, 255));
        ColorUtil.addColor("beige", new Color(245, 245, 220));
        ColorUtil.addColor("bisque", new Color(255, 228, 196));
        ColorUtil.addColor("black", new Color(0, 0, 0));
        ColorUtil.addColor("blanchedalmond", new Color(255, 235, 205));
        ColorUtil.addColor("blue", Color.blue);
        ColorUtil.addColor("blueviolet", new Color(138, 43, 226));
        ColorUtil.addColor("brown", new Color(165, 42, 42));
        ColorUtil.addColor("burlywood", new Color(222, 184, 135));
        ColorUtil.addColor("cadetblue", new Color(95, 158, 160));
        ColorUtil.addColor("chartreuse", new Color(127, 255, 0));
        ColorUtil.addColor("chocolate", new Color(210, 105, 30));
        ColorUtil.addColor("coral", new Color(255, 127, 80));
        ColorUtil.addColor("cornflowerblue", new Color(100, 149, 237));
        ColorUtil.addColor("cornsilk", new Color(255, 248, 220));
        ColorUtil.addColor("crimson", new Color(220, 20, 60));
        ColorUtil.addColor("cyan", Color.cyan);
        ColorUtil.addColor("darkblue", new Color(0, 0, 139));
        ColorUtil.addColor("darkcyan", new Color(0, 139, 139));
        ColorUtil.addColor("darkgoldenrod", new Color(184, 134, 11));
        ColorUtil.addColor("darkgray", new Color(169, 169, 169));
        ColorUtil.addColor("darkgreen", new Color(0, 100, 0));
        ColorUtil.addColor("darkgrey", new Color(169, 169, 169));
        ColorUtil.addColor("darkkhaki", new Color(189, 183, 107));
        ColorUtil.addColor("darkmagenta", new Color(139, 0, 139));
        ColorUtil.addColor("darkolivegreen", new Color(85, 107, 47));
        ColorUtil.addColor("darkorange", new Color(255, 140, 0));
        ColorUtil.addColor("darkorchid", new Color(153, 50, 204));
        ColorUtil.addColor("darkred", new Color(139, 0, 0));
        ColorUtil.addColor("darksalmon", new Color(233, 150, 122));
        ColorUtil.addColor("darkseagreen", new Color(143, 188, 143));
        ColorUtil.addColor("darkslateblue", new Color(72, 61, 139));
        ColorUtil.addColor("darkslategray", new Color(47, 79, 79));
        ColorUtil.addColor("darkslategrey", new Color(47, 79, 79));
        ColorUtil.addColor("darkturquoise", new Color(0, 206, 209));
        ColorUtil.addColor("darkviolet", new Color(148, 0, 211));
        ColorUtil.addColor("deeppink", new Color(255, 20, 147));
        ColorUtil.addColor("deepskyblue", new Color(0, 191, 255));
        ColorUtil.addColor("dimgray", new Color(105, 105, 105));
        ColorUtil.addColor("dimgrey", new Color(105, 105, 105));
        ColorUtil.addColor("dodgerblue", new Color(30, 144, 255));
        ColorUtil.addColor("firebrick", new Color(178, 34, 34));
        ColorUtil.addColor("floralwhite", new Color(255, 250, 240));
        ColorUtil.addColor("forestgreen", new Color(34, 139, 34));
        ColorUtil.addColor("fuchsia", Color.magenta);
        ColorUtil.addColor("gainsboro", new Color(220, 220, 220));
        ColorUtil.addColor("ghostwhite", new Color(248, 248, 255));
        ColorUtil.addColor("gold", new Color(255, 215, 0));
        ColorUtil.addColor("goldenrod", new Color(218, 165, 32));
        ColorUtil.addColor("gray", new Color(128, 128, 128));
        ColorUtil.addColor("grey", new Color(128, 128, 128));
        ColorUtil.addColor("green", new Color(0, 128, 0));
        ColorUtil.addColor("greenyellow", new Color(173, 255, 47));
        ColorUtil.addColor("honeydew", new Color(240, 255, 240));
        ColorUtil.addColor("hotpink", new Color(255, 105, 180));
        ColorUtil.addColor("indianred", new Color(205, 92, 92));
        ColorUtil.addColor("indigo", new Color(75, 0, 130));
        ColorUtil.addColor("ivory", new Color(255, 255, 240));
        ColorUtil.addColor("khaki", new Color(240, 230, 140));
        ColorUtil.addColor("lavender", new Color(230, 230, 250));
        ColorUtil.addColor("lavenderblush", new Color(255, 240, 245));
        ColorUtil.addColor("lawngreen", new Color(124, 252, 0));
        ColorUtil.addColor("lemonchiffon", new Color(255, 250, 205));
        ColorUtil.addColor("lightblue", new Color(173, 216, 230));
        ColorUtil.addColor("lightcoral", new Color(240, 128, 128));
        ColorUtil.addColor("lightcyan", new Color(224, 255, 255));
        ColorUtil.addColor("lightgoldenrodyellow", new Color(250, 250, 210));
        ColorUtil.addColor("lightgray", new Color(211, 211, 211));
        ColorUtil.addColor("lightgreen", new Color(144, 238, 144));
        ColorUtil.addColor("lightgrey", new Color(211, 211, 211));
        ColorUtil.addColor("lightpink", new Color(255, 160, 122));
        ColorUtil.addColor("lightsalmon", new Color(255, 160, 122));
        ColorUtil.addColor("lightseagreen", new Color(32, 178, 170));
        ColorUtil.addColor("lightskyblue", new Color(135, 206, 250));
        ColorUtil.addColor("lightslategray", new Color(119, 136, 153));
        ColorUtil.addColor("lightslategrey", new Color(119, 136, 153));
        ColorUtil.addColor("lightsteelblue", new Color(176, 196, 222));
        ColorUtil.addColor("lightyellow", new Color(255, 255, 224));
        ColorUtil.addColor("lime", Color.green);
        ColorUtil.addColor("limegreen", new Color(50, 205, 50));
        ColorUtil.addColor("linen", new Color(250, 240, 230));
        ColorUtil.addColor("magenta", Color.magenta);
        ColorUtil.addColor("maroon", new Color(128, 0, 0));
        ColorUtil.addColor("mediumaquamarine", new Color(102, 205, 170));
        ColorUtil.addColor("mediumblue", new Color(0, 0, 205));
        ColorUtil.addColor("mediumorchid", new Color(186, 85, 211));
        ColorUtil.addColor("mediumpurple", new Color(147, 112, 219));
        ColorUtil.addColor("mediumseagreen", new Color(60, 179, 113));
        ColorUtil.addColor("mediumslateblue", new Color(123, 104, 238));
        ColorUtil.addColor("mediumspringgreen", new Color(0, 250, 154));
        ColorUtil.addColor("mediumturquoise", new Color(72, 209, 204));
        ColorUtil.addColor("mediumvioletred", new Color(199, 21, 133));
        ColorUtil.addColor("midnightblue", new Color(25, 25, 112));
        ColorUtil.addColor("mintcream", new Color(245, 255, 250));
        ColorUtil.addColor("mistyrose", new Color(255, 228, 225));
        ColorUtil.addColor("moccasin", new Color(255, 228, 181));
        ColorUtil.addColor("navajowhite", new Color(255, 222, 173));
        ColorUtil.addColor("navy", new Color(0, 0, 128));
        ColorUtil.addColor("oldlace", new Color(253, 245, 230));
        ColorUtil.addColor("olive", new Color(128, 128, 0));
        ColorUtil.addColor("olivedrab", new Color(107, 142, 35));
        ColorUtil.addColor("orange", new Color(255, 165, 0));
        ColorUtil.addColor("orangered", new Color(255, 69, 0));
        ColorUtil.addColor("orchid", new Color(218, 112, 214));
        ColorUtil.addColor("palegoldenrod", new Color(238, 232, 170));
        ColorUtil.addColor("palegreen", new Color(152, 251, 152));
        ColorUtil.addColor("paleturquoise", new Color(175, 238, 238));
        ColorUtil.addColor("palevioletred", new Color(219, 112, 147));
        ColorUtil.addColor("papayawhip", new Color(255, 239, 213));
        ColorUtil.addColor("peachpuff", new Color(255, 218, 185));
        ColorUtil.addColor("peru", new Color(205, 133, 63));
        ColorUtil.addColor("pink", new Color(255, 192, 203));
        ColorUtil.addColor("plum", new Color(221, 160, 221));
        ColorUtil.addColor("powderblue", new Color(176, 224, 230));
        ColorUtil.addColor("purple", new Color(128, 0, 128));
        ColorUtil.addColor("red", Color.red);
        ColorUtil.addColor("rosybrown", new Color(188, 143, 143));
        ColorUtil.addColor("royalblue", new Color(65, 105, 225));
        ColorUtil.addColor("saddlebrown", new Color(139, 69, 19));
        ColorUtil.addColor("salmon", new Color(250, 128, 114));
        ColorUtil.addColor("sandybrown", new Color(244, 164, 96));
        ColorUtil.addColor("seagreen", new Color(46, 139, 87));
        ColorUtil.addColor("seashell", new Color(255, 245, 238));
        ColorUtil.addColor("sienna", new Color(160, 82, 45));
        ColorUtil.addColor("ser", new Color(192, 192, 192));
        ColorUtil.addColor("skyblue", new Color(135, 206, 235));
        ColorUtil.addColor("slateblue", new Color(106, 90, 205));
        ColorUtil.addColor("slategray", new Color(112, 128, 144));
        ColorUtil.addColor("slategrey", new Color(112, 128, 144));
        ColorUtil.addColor("snow", new Color(255, 250, 250));
        ColorUtil.addColor("springgreen", new Color(0, 255, 127));
        ColorUtil.addColor("steelblue", new Color(70, 130, 180));
        ColorUtil.addColor("tan", new Color(210, 180, 140));
        ColorUtil.addColor("teal", new Color(0, 128, 128));
        ColorUtil.addColor("thistle", new Color(216, 191, 216));
        ColorUtil.addColor("tomato", new Color(255, 99, 71));
        ColorUtil.addColor("turquoise", new Color(64, 224, 208));
        ColorUtil.addColor("violet", new Color(238, 130, 238));
        ColorUtil.addColor("wheat", new Color(245, 222, 179));
        ColorUtil.addColor("white", new Color(255, 255, 255));
        ColorUtil.addColor("whitesmoke", new Color(245, 245, 245));
        ColorUtil.addColor("yellow", Color.yellow);
        ColorUtil.addColor("yellowgreen", new Color(154, 205, 50));
    }
    
    /// Returns a brighter variant of `color` while preserving its alpha channel.
    ///
    /// Colors above mid-brightness are brightened by increasing brightness and trimming saturation
    /// so chart fills and strokes look highlighted instead of merely washed out. Very dark inputs
    /// are first nudged away from pure black before the stronger boost is applied.
    ///
    /// @return a derived color intended for highlight treatment
    public static Color brighter(Color color) {
        synchronized (ColorUtil.hsbBuffer) {
            Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), ColorUtil.hsbBuffer);
            boolean isBright = ColorUtil.hsbBuffer[2] > 0.5f;
            if (isBright) {
                return ColorUtil.fromHsb(
                        ColorUtil.hsbBuffer[0],
                        ColorUtil.hsbBuffer[1] * 0.7f,
                        Math.min(1.0f, ColorUtil.hsbBuffer[2] * 1.4f),
                        color.getAlpha());
            }
            if (ColorUtil.hsbBuffer[2] == 0.0f)
                ColorUtil.hsbBuffer[2] = 0.06f;
            return ColorUtil.fromHsb(
                    ColorUtil.hsbBuffer[0],
                    ColorUtil.hsbBuffer[1],
                    Math.min(1.0f, ColorUtil.hsbBuffer[2] * 1.6f),
                    color.getAlpha());
        }
    }
    
    private static void registerAwtAliases() {
        ColorUtil.addColor("gray", Color.gray);
        ColorUtil.addColor("green", Color.green);
        ColorUtil.addColor("darkGray", Color.darkGray);
        ColorUtil.addColor("lightGray", Color.lightGray);
        ColorUtil.addColor("darkgray", Color.darkGray);
        ColorUtil.addColor("lightgray", Color.lightGray);
        ColorUtil.addColor("orange", Color.orange);
        ColorUtil.addColor("pink", Color.pink);
    }
    
    /// Returns a darker variant of `color` while preserving its alpha channel.
    ///
    /// Colors above mid-brightness mostly lose brightness, while darker inputs also gain
    /// saturation so muted paints do not collapse toward black too abruptly.
    ///
    /// @return a derived color intended for shadow or pressed-state treatment
    public static Color darker(Color color) {
        synchronized (ColorUtil.hsbBuffer) {
            Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), ColorUtil.hsbBuffer);
            boolean isBright = ColorUtil.hsbBuffer[2] > 0.5f;
            if (isBright) {
                return ColorUtil.fromHsb(
                        ColorUtil.hsbBuffer[0],
                        ColorUtil.hsbBuffer[1],
                        Math.min(1.0f, ColorUtil.hsbBuffer[2] * 0.71428573f),
                        color.getAlpha());
            }
            return ColorUtil.fromHsb(
                    ColorUtil.hsbBuffer[0],
                    ColorUtil.hsbBuffer[1] * 1.42f,
                    Math.min(1.0f, ColorUtil.hsbBuffer[2] * 0.625f),
                    color.getAlpha());
        }
    }
    
    /// Returns the HSB brightness component of `color`.
    ///
    /// @return the value component from {@link Color#RGBtoHSB(int, int, int, float[])} in the
    ///     inclusive `0.0f..1.0f` range
    public static float getBrightness(Color color) {
        synchronized (ColorUtil.hsbBuffer) {
            return Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), ColorUtil.hsbBuffer)[2];
        }
    }
    
    /// Returns the color currently registered under `name`.
    ///
    /// @return the mapped color, or `null` when the registry has no entry for `name`
    public static final synchronized Color getColor(String name) {
        return ColorUtil.colorsByName.get(name);
    }
    
    /// Returns an unmodifiable live view of the shared registry.
    ///
    /// The returned map is backed by the registry itself rather than copied. Callers that need a
    /// stable snapshot, or that may iterate while other code registers colors, should copy the view
    /// before using it as independent state.
    ///
    /// @return an unmodifiable map view backed by the shared registry
    public static synchronized Map<String, Color> getColorMap() {
        return Collections.unmodifiableMap(ColorUtil.colorsByName);
    }
    
    /// Returns the HSB hue component of `color`.
    ///
    /// @return the hue component from {@link Color#RGBtoHSB(int, int, int, float[])} in the
    ///     inclusive `0.0f..1.0f` range
    public static float getHue(Color color) {
        synchronized (ColorUtil.hsbBuffer) {
            return Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), ColorUtil.hsbBuffer)[0];
        }
    }
    
    /// Computes perceived luminance from `color`'s red, green, and blue channels.
    ///
    /// {@link one.chartsy.charting.internal.TextRenderer} uses this value as a simple contrast
    /// heuristic when choosing between black and white label text. The alpha channel is ignored.
    ///
    /// @return a normalized luminance ratio where `0.0f` is black and `1.0f` is white
    public static float getLuminance(Color color) {
        int r = color.getRed();
        int g = color.getGreen();
        int b = color.getBlue();
        return (0.299f * r + 0.587f * g + 0.114f * b) / 255.0f;
    }
    
    /// Creates an opaque fallback color for renderers that do not have an assigned palette entry.
    ///
    /// {@link one.chartsy.charting.ChartRenderer}, {@link one.chartsy.charting.ColorData}, and
    /// {@link one.chartsy.charting.renderers.SinglePieRenderer} use this when a chart element needs
    /// a visually distinct color but no semantic palette entry is available.
    ///
    /// Implementation note: the current algorithm favors variety over reproducibility or uniform
    /// coverage of RGB space.
    public static Color getRandomColor() {
        int blue = (int) (Math.random() * 256.0);
        int green = (int) (Math.random() * (256.0 - Math.random() * blue));
        int red = (int) Math.abs(Math.random() * (256.0 - Math.random() * (blue + green)));
        return new Color(red, green, blue);
    }
    
    /// Returns the HSB saturation component of `color`.
    ///
    /// @return the saturation component from {@link Color#RGBtoHSB(int, int, int, float[])} in the
    ///     inclusive `0.0f..1.0f` range
    public static float getSaturation(Color color) {
        synchronized (ColorUtil.hsbBuffer) {
            return Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), ColorUtil.hsbBuffer)[1];
        }
    }
    
    /// Returns `color` with a replaced alpha channel derived from `alpha`.
    ///
    /// The red, green, and blue channels are preserved exactly. When the requested opacity already
    /// matches the source alpha, this method returns the original instance instead of allocating a
    /// copy.
    ///
    /// @param alpha the requested opacity ratio in the inclusive `0.0f..1.0f` range
    /// @return `color` when no change is required; otherwise a color with the same RGB channels and
    ///     the requested alpha
    /// @throws IllegalArgumentException if `alpha` lies outside the inclusive `0.0f..1.0f` range
    public static Color setAlpha(Color color, float alpha) {
        ColorUtil.requireUnitInterval("Alpha", alpha);
        int argbAlpha = (int) (alpha * 255.0f + 0.5f);
        if (color.getAlpha() == argbAlpha)
            return color;
        return new Color(color.getRGB() & 0xffffff | argbAlpha << 24, true);
    }
    
    /// Returns `color` with its HSB brightness component replaced.
    ///
    /// Hue, saturation, and alpha are preserved.
    ///
    /// @param brightness the replacement brightness in the inclusive `0.0f..1.0f` range
    /// @return a derived color with the requested brightness
    /// @throws IllegalArgumentException if `brightness` lies outside the inclusive `0.0f..1.0f`
    ///     range
    public static Color setBrightness(Color color, float brightness) {
        ColorUtil.requireUnitInterval("Brightness", brightness);
        synchronized (ColorUtil.hsbBuffer) {
            Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), ColorUtil.hsbBuffer);
            ColorUtil.hsbBuffer[2] = brightness;
            return ColorUtil.fromHsb(ColorUtil.hsbBuffer[0], ColorUtil.hsbBuffer[1], ColorUtil.hsbBuffer[2], color.getAlpha());
        }
    }
    
    /// Returns `color` with its HSB hue component replaced.
    ///
    /// Saturation, brightness, and alpha are preserved.
    ///
    /// @param hue the replacement hue in the inclusive `0.0f..1.0f` range
    /// @return a derived color with the requested hue
    /// @throws IllegalArgumentException if `hue` lies outside the inclusive `0.0f..1.0f` range
    public static Color setHue(Color color, float hue) {
        ColorUtil.requireUnitInterval("Hue", hue);
        synchronized (ColorUtil.hsbBuffer) {
            Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), ColorUtil.hsbBuffer);
            ColorUtil.hsbBuffer[0] = hue;
            return ColorUtil.fromHsb(ColorUtil.hsbBuffer[0], ColorUtil.hsbBuffer[1], ColorUtil.hsbBuffer[2], color.getAlpha());
        }
    }
    
    /// Returns `color` with its HSB saturation component replaced.
    ///
    /// Hue, brightness, and alpha are preserved.
    ///
    /// @param saturation the replacement saturation in the inclusive `0.0f..1.0f` range
    /// @return a derived color with the requested saturation
    /// @throws IllegalArgumentException if `saturation` lies outside the inclusive `0.0f..1.0f`
    ///     range
    public static Color setSaturation(Color color, float saturation) {
        ColorUtil.requireUnitInterval("Saturation", saturation);
        synchronized (ColorUtil.hsbBuffer) {
            Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), ColorUtil.hsbBuffer);
            ColorUtil.hsbBuffer[1] = saturation;
            return ColorUtil.fromHsb(ColorUtil.hsbBuffer[0], ColorUtil.hsbBuffer[1], ColorUtil.hsbBuffer[2], color.getAlpha());
        }
    }
    
    /// Returns a gentler brightening step than {@link #brighter(Color)}.
    ///
    /// {@link one.chartsy.charting.PlotStyle} uses this helper for incremental intensity changes
    /// where repeated small adjustments look better than a single large highlight jump.
    ///
    /// @return a derived color suitable for subtle emphasis changes
    public static Color slightlyBrighter(Color color) {
        synchronized (ColorUtil.hsbBuffer) {
            Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), ColorUtil.hsbBuffer);
            boolean isBright = ColorUtil.hsbBuffer[2] > 0.5f;
            if (isBright) {
                return ColorUtil.fromHsb(
                        ColorUtil.hsbBuffer[0],
                        ColorUtil.hsbBuffer[1] * 0.7f,
                        Math.min(1.0f, ColorUtil.hsbBuffer[2] * 1.1f),
                        color.getAlpha());
            }
            if (ColorUtil.hsbBuffer[2] == 0.0f)
                ColorUtil.hsbBuffer[2] = 0.06f;
            return ColorUtil.fromHsb(
                    ColorUtil.hsbBuffer[0],
                    ColorUtil.hsbBuffer[1],
                    Math.min(1.0f, ColorUtil.hsbBuffer[2] * 1.25f),
                    color.getAlpha());
        }
    }
    
    /// Returns a gentler darkening step than {@link #darker(Color)}.
    ///
    /// {@link one.chartsy.charting.PlotStyle} uses this helper for incremental intensity changes
    /// where repeated small adjustments look better than a single large shadow jump.
    ///
    /// @return a derived color suitable for subtle de-emphasis changes
    public static Color slightlyDarker(Color color) {
        synchronized (ColorUtil.hsbBuffer) {
            Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), ColorUtil.hsbBuffer);
            boolean isBright = ColorUtil.hsbBuffer[2] > 0.5f;
            if (isBright) {
                return ColorUtil.fromHsb(
                        ColorUtil.hsbBuffer[0],
                        ColorUtil.hsbBuffer[1],
                        Math.min(1.0f, ColorUtil.hsbBuffer[2] * 0.9090909f),
                        color.getAlpha());
            }
            return ColorUtil.fromHsb(
                    ColorUtil.hsbBuffer[0],
                    ColorUtil.hsbBuffer[1] * 1.42f,
                    Math.min(1.0f, ColorUtil.hsbBuffer[2] * 0.8f),
                    color.getAlpha());
        }
    }
    
    /// Creates a subclass hook for types that expose the shared registry.
    ///
    /// Instances carry no state. The protected constructor exists only so {@link
    /// one.chartsy.charting.ColorData} can extend this utility class while reusing its static
    /// helpers.
    protected ColorUtil() {
    }
}
