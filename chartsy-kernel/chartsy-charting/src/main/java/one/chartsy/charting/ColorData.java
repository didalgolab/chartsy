package one.chartsy.charting;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import one.chartsy.charting.util.ColorUtil;
import one.chartsy.charting.util.java2d.LinearGradientPaint;
import one.chartsy.charting.util.java2d.RadialGradientPaint;

/// Named chart colors plus small palette and gradient factories used across the charting module.
///
/// The static color constants expose a subset of CSS-style named colors through
/// [ColorUtil#getColor(String)]. The utility methods build higher-level chart-friendly defaults:
/// a shared synchronized palette, directional linear gradients that adapt to the painted bounds,
/// and a radial "sphere" gradient used for glossy marker fills.
public class ColorData extends ColorUtil {
    private static final float[] SPHERE_GRADIENT_STOPS;
    public static final int HORIZONTAL_GRADIENT = 0;
    public static final int VERTICAL_GRADIENT = 1;
    public static final int UP_DIAGONAL_GRADIENT = 2;
    public static final int DOWN_DIAGONAL_GRADIENT = 3;
    public static final Color aquamarine = ColorUtil.getColor("aquamarine");
    public static final Color blueViolet = ColorUtil.getColor("blueviolet");
    public static final Color brown = ColorUtil.getColor("brown");
    public static final Color cadetBlue = ColorUtil.getColor("cadetblue");
    public static final Color coral = ColorUtil.getColor("coral");
    public static final Color cornflowerBlue = ColorUtil.getColor("cornflowerblue");
    public static final Color darkGreen = ColorUtil.getColor("darkgreen");
    public static final Color darkOliveGreen = ColorUtil.getColor("darkolivegreen");
    public static final Color darkOrchid = ColorUtil.getColor("darkorchid");
    public static final Color darkSlateBlue = ColorUtil.getColor("darkslateblue");
    public static final Color darkSlateGray = ColorUtil.getColor("darkslategray");
    public static final Color darkTurquoise = ColorUtil.getColor("darkturquoise");
    public static final Color dimGray = ColorUtil.getColor("dimgray");
    public static final Color firebrick = ColorUtil.getColor("firebrick");
    public static final Color forestGreen = ColorUtil.getColor("forestgreen");
    public static final Color gold = ColorUtil.getColor("gold");
    public static final Color goldenrod = ColorUtil.getColor("goldenrod");
    public static final Color gray = ColorUtil.getColor("gray");
    public static final Color greenYellow = ColorUtil.getColor("greenyellow");
    public static final Color indianRed = ColorUtil.getColor("indianred");
    public static final Color lightBlue = ColorUtil.getColor("lightblue");
    public static final Color lightGray = ColorUtil.getColor("lightgray");
    public static final Color lightSteelBlue = ColorUtil.getColor("lightsteelblue");
    public static final Color limeGreen = ColorUtil.getColor("limegreen");
    public static final Color magenta = ColorUtil.getColor("magenta");
    public static final Color mediumAquamarine = ColorUtil.getColor("mediumaquamarine");
    public static final Color mediumBlue = ColorUtil.getColor("mediumblue");
    public static final Color mediumOrchid = ColorUtil.getColor("mediumorchid");
    public static final Color mediumSeaGreen = ColorUtil.getColor("mediumseagreen");
    public static final Color mediumSlateBlue = ColorUtil.getColor("mediumslateblue");
    public static final Color mediumSpringGreen = ColorUtil.getColor("mediumspringgreen");
    public static final Color mediumTurquoise = ColorUtil.getColor("mediumturquoise");
    public static final Color mediumVioletRed = ColorUtil.getColor("mediumvioletred");
    public static final Color navy = ColorUtil.getColor("navy");
    public static final Color navyBlue = ColorUtil.getColor("navy");
    public static final Color oliveDrab = ColorUtil.getColor("olivedrab");
    public static final Color orange = ColorUtil.getColor("orange");
    public static final Color orangeRed = ColorUtil.getColor("orangered");
    public static final Color orchid = ColorUtil.getColor("orchid");
    public static final Color paleGreen = ColorUtil.getColor("palegreen");
    public static final Color pink = ColorUtil.getColor("pink");
    public static final Color plum = ColorUtil.getColor("plum");
    public static final Color salmon = ColorUtil.getColor("salmon");
    public static final Color seaGreen = ColorUtil.getColor("seagreen");
    public static final Color sienna = ColorUtil.getColor("sienna");
    public static final Color skyBlue = ColorUtil.getColor("skyblue");
    public static final Color slateBlue = ColorUtil.getColor("slateblue");
    public static final Color springGreen = ColorUtil.getColor("springgreen");
    public static final Color steelBlue = ColorUtil.getColor("steelblue");
    public static final Color thistle = ColorUtil.getColor("thistle");
    public static final Color turquoise = ColorUtil.getColor("turquoise");
    public static final Color violet = ColorUtil.getColor("violet");
    public static final Color violetRed = new Color(208, 32, 144);
    public static final Color wheat = ColorUtil.getColor("wheat");
    public static final Color yellow = ColorUtil.getColor("yellow");
    public static final Color yellowGreen = ColorUtil.getColor("yellowgreen");
    private static final List<Color> DEFAULT_COLORS = Collections.synchronizedList(new ArrayList<>(10));

    static {
        SPHERE_GRADIENT_STOPS = new float[]{0.0f, 0.65f, 1.0f};
        ColorData.DEFAULT_COLORS.add(new Color(0, 134, 198));
        ColorData.DEFAULT_COLORS.add(new Color(205, 104, 137));
        ColorData.DEFAULT_COLORS.add(new Color(152, 251, 152));
        ColorData.DEFAULT_COLORS.add(new Color(235, 235, 0));
        ColorData.DEFAULT_COLORS.add(new Color(233, 150, 122));
        ColorData.DEFAULT_COLORS.add(new Color(245, 222, 179));
        ColorData.DEFAULT_COLORS.add(new Color(100, 149, 237));
        ColorData.DEFAULT_COLORS.add(new Color(255, 106, 106));
        ColorData.DEFAULT_COLORS.add(new Color(176, 196, 222));
        ColorData.DEFAULT_COLORS.add(new Color(198, 195, 255));
        ColorData.DEFAULT_COLORS.add(new Color(0, 130, 132));
        ColorData.DEFAULT_COLORS.add(new Color(198, 132, 198));
        ColorData.DEFAULT_COLORS.add(new Color(40, 195, 195));
        ColorData.DEFAULT_COLORS.add(new Color(198, 255, 198));
        ColorData.DEFAULT_COLORS.add(new Color(167, 160, 187));
    }

    /// Returns `count` colors, reusing the built-in default palette first and filling any
    /// remaining slots with random colors.
    public static Color[] generateColors(int count) {
        Color[] generatedColors = new Color[count];
        int colorIndex = 0;
        Iterator<Color> paletteIterator = ColorData.DEFAULT_COLORS.iterator();
        while (paletteIterator.hasNext() && colorIndex < count) {
            generatedColors[colorIndex] = paletteIterator.next();
            colorIndex++;
        }
        while (colorIndex < count) {
            generatedColors[colorIndex] = ColorUtil.getRandomColor();
            colorIndex++;
        }
        return generatedColors;
    }

    /// Returns one entry from the shared default palette.
    public static Color getDefaultColor(int index) {
        return ColorData.DEFAULT_COLORS.get(index);
    }

    /// Returns the live synchronized default palette list.
    ///
    /// Callers that iterate it directly should respect the synchronization requirements of
    /// [Collections#synchronizedList(List)].
    public static List<Color> getDefaultColors() {
        return ColorData.DEFAULT_COLORS;
    }

    /// Builds a three-stop directional gradient from one base color by brightening the start and
    /// darkening the end.
    ///
    /// The returned paint is adaptive, so its normalized coordinates scale to the bounds of the
    /// chart element currently being painted.
    public static LinearGradientPaint getGradient(int direction, Color baseColor) {
        if (baseColor == null)
            throw new IllegalArgumentException("Null color");
        return ColorData.getGradient(direction, new Color[]{
                ColorUtil.brighter(baseColor),
                baseColor,
                ColorUtil.darker(baseColor)
        });
    }

    /// Builds an adaptive linear gradient from the supplied color ramp.
    ///
    /// `direction` should be one of the `*_GRADIENT` constants on this class. The resulting paint
    /// uses normalized `0..1` coordinates, so it scales to the bounds of the chart element being
    /// rendered. When `direction` is not recognized, the gradient falls back to the horizontal
    /// left-to-right orientation.
    public static LinearGradientPaint getGradient(int direction, Color[] colors) {
        if (colors == null)
            throw new IllegalArgumentException("Null colors");
        int colorCount = colors.length;
        float[] stops = new float[colorCount];
        for (int index = 0; index < colorCount; index++) {
            stops[index] = index / (float) (colorCount - 1);
        }
        float startX;
        float startY;
        float endX;
        float endY;
        switch (direction) {
            case VERTICAL_GRADIENT:
                startX = 0.0f;
                startY = 1.0f;
                endX = 0.0f;
                endY = 0.0f;
                break;

            case UP_DIAGONAL_GRADIENT:
                startX = 0.0f;
                startY = 1.0f;
                endX = 1.0f;
                endY = 0.0f;
                break;

            case DOWN_DIAGONAL_GRADIENT:
                startX = 0.0f;
                startY = 0.0f;
                endX = 1.0f;
                endY = 1.0f;
                break;

            default:
                startX = 0.0f;
                startY = 0.0f;
                endX = 1.0f;
                endY = 0.0f;
                break;
        }
        return new LinearGradientPaint(startX, startY, endX, endY, stops, colors, true);
    }

    /// Builds a radial gradient that simulates a glossy sphere highlight.
    ///
    /// The highlight is biased toward the upper-left by centering the radial gradient at
    /// `(0.3, 0.3)` inside normalized bounds.
    public static RadialGradientPaint getSphereGradient(Color bodyColor, Color highlightColor) {
        if (bodyColor == null || highlightColor == null)
            throw new IllegalArgumentException("Null colors");
        return new RadialGradientPaint(0.3f, 0.3f, 1.0f, ColorData.SPHERE_GRADIENT_STOPS,
                new Color[]{highlightColor, bodyColor, bodyColor}, true);
    }

    private ColorData() {
    }
}
