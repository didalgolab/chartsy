package one.chartsy.exploration.ui;

import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static one.chartsy.exploration.ui.PriceThumbnailRendererTest.prices;
import static org.junit.jupiter.api.Assertions.*;

class PriceThumbnailTrendTest {
    private record Example(PriceThumbnailTrend expected, double... closes) { }

    private static final List<Example> EXAMPLES = List.of(
            new Example(PriceThumbnailTrend.BULLISH, 100, 105, 112, 122),
            new Example(PriceThumbnailTrend.BEARISH, 100, 95, 82, 75),
            new Example(PriceThumbnailTrend.FLAT, 100, 101, 99, 100.5),
            new Example(PriceThumbnailTrend.FLAT, 100, 100, 100),
            new Example(PriceThumbnailTrend.MIXED, 100, 70, 140, 100),
            new Example(PriceThumbnailTrend.MIXED, 100, 140, 120),
            new Example(PriceThumbnailTrend.MIXED, 100, 60, 80),
            new Example(PriceThumbnailTrend.MIXED, 100, 107, 114),
            new Example(PriceThumbnailTrend.MIXED, 100, 94, 86));

    @Test
    void of_distinguishes_strong_direction_flat_prices_and_volatile_roundtrips() {
        for (var example : EXAMPLES)
            assertEquals(example.expected(), PriceThumbnailTrend.of(prices(example.closes())),
                    Arrays.toString(example.closes()));
    }

    @Test
    void of_includes_five_percent_flat_fifteen_percent_move_and_sixty_percent_dominance_boundaries() {
        for (var example : boundaryExamples())
            assertEquals(example.expected(), PriceThumbnailTrend.of(prices(example.closes())),
                    Arrays.toString(example.closes()));
        assertEquals(PriceThumbnailTrend.MIXED, PriceThumbnailTrend.of(prices(100, 114.99)));
        assertEquals(PriceThumbnailTrend.MIXED, PriceThumbnailTrend.of(prices(100, 85.01)));
        assertEquals(PriceThumbnailTrend.MIXED, PriceThumbnailTrend.of(prices(100, 105.01, 100)));
        assertEquals(PriceThumbnailTrend.MIXED, PriceThumbnailTrend.of(prices(100, 80, 130, 129.99)));
    }

    @Test
    void of_positive_price_scaling_preserves_classification_including_exact_boundaries() {
        var examples = new ArrayList<>(EXAMPLES);
        examples.addAll(boundaryExamples());
        for (double scale : new double[] {.000001, .01, .1, 1, 1.25, 1234, 1_000_000}) {
            for (var example : examples) {
                var scaled = Arrays.stream(example.closes()).map(close -> close * scale).toArray();
                assertEquals(example.expected(), PriceThumbnailTrend.of(prices(scaled)),
                        "Classification must not depend on price units: scale=" + scale + ", closes="
                                + Arrays.toString(example.closes()));
            }
        }
    }

    @Test
    void of_missing_or_nonpositive_endpoints_do_not_claim_direction() {
        for (double[] closes : new double[][] {
                {}, {100}, {Double.NaN, 100}, {100, Double.NaN}, {100, Double.POSITIVE_INFINITY},
                {Double.NaN, Double.NaN}, {0, 100}, {100, 0}, {-100, 100}, {100, -100}
        })
            assertEquals(PriceThumbnailTrend.UNAVAILABLE, PriceThumbnailTrend.of(prices(closes)),
                    Arrays.toString(closes));
    }

    @Test
    void colorOn_all_trends_keep_graphical_contrast_on_light_dark_and_custom_selection_colors() {
        for (var background : List.of(Color.WHITE, new Color(244, 244, 244), new Color(30, 34, 39),
                new Color(38, 82, 130), new Color(244, 211, 94), new Color(188, 199, 212),
                new Color(115, 115, 115), new Color(30, 130, 80), new Color(160, 110, 190))) {
            for (var trend : PriceThumbnailTrend.values()) {
                var ink = trend.colorOn(background);
                assertTrue(contrastRatio(ink, background) >= 3,
                        trend + " must remain visible against #" + Integer.toHexString(background.getRGB()));
                assertEquals(255, ink.getAlpha());
            }
            var bullish = PriceThumbnailTrend.BULLISH.colorOn(background);
            var bearish = PriceThumbnailTrend.BEARISH.colorOn(background);
            var mixed = PriceThumbnailTrend.MIXED.colorOn(background);
            assertTrue(bullish.getGreen() > bullish.getRed() && bullish.getGreen() > bullish.getBlue());
            assertTrue(bearish.getRed() > bearish.getGreen() && bearish.getRed() > bearish.getBlue());
            assertTrue(mixed.getBlue() > mixed.getRed() && mixed.getRed() > mixed.getGreen(),
                    "Mixed movement should use restrained purple, with red between green and blue");
        }
    }

    @Test
    void colorOn_bullish_and_bearish_are_more_saturated_on_light_dark_and_selected_rows() {
        for (var background : List.of(Color.WHITE, new Color(30, 34, 39), new Color(38, 82, 130))) {
            boolean light = background.equals(Color.WHITE);
            var previousGreen = new Color(light ? 0x147D52 : 0x8DDEB4);
            var previousRed = new Color(light ? 0xBE3B42 : 0xFFB4B4);
            var green = PriceThumbnailTrend.BULLISH.colorOn(background);
            var red = PriceThumbnailTrend.BEARISH.colorOn(background);
            assertTrue(saturation(green) >= saturation(previousGreen) + .08,
                    "Bullish color should gain visibly more saturation against " + background);
            assertTrue(saturation(red) >= saturation(previousRed) + .08,
                    "Bearish color should gain visibly more saturation against " + background);
            assertTrue(contrastRatio(green, background) >= 3);
            assertTrue(contrastRatio(red, background) >= 3);
            var mixed = PriceThumbnailTrend.MIXED.colorOn(background);
            assertTrue(saturation(mixed) < saturation(green) && saturation(mixed) < saturation(red),
                    "Mixed purple should stay quieter than a strong directional move");
        }
    }

    @Test
    void colorOn_flat_and_unavailable_keep_the_existing_neutral_palette() {
        for (var background : List.of(Color.WHITE, new Color(30, 34, 39), new Color(38, 82, 130))) {
            var expected = new Color(background.equals(Color.WHITE) ? 0x5C6675 : 0xC8D2DE);
            assertEquals(expected, PriceThumbnailTrend.FLAT.colorOn(background));
            assertEquals(expected, PriceThumbnailTrend.UNAVAILABLE.colorOn(background));
        }
    }

    private static float saturation(Color color) {
        return Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null)[1];
    }

    private static List<Example> boundaryExamples() {
        return List.of(new Example(PriceThumbnailTrend.FLAT, 100, 105, 100),
                new Example(PriceThumbnailTrend.BULLISH, 100, 115),
                new Example(PriceThumbnailTrend.BEARISH, 100, 85),
                new Example(PriceThumbnailTrend.BULLISH, 100, 80, 130, 130),
                new Example(PriceThumbnailTrend.BEARISH, 100, 120, 70, 70));
    }

    /** Independent sRGB/WCAG contrast calculation, not the production helper. */
    static double contrastRatio(Color foreground, Color background) {
        double first = relativeLuminance(foreground);
        double second = relativeLuminance(background);
        return (Math.max(first, second) + .05) / (Math.min(first, second) + .05);
    }

    private static double relativeLuminance(Color color) {
        double[] weights = {.2126, .7152, .0722};
        int[] channels = {color.getRed(), color.getGreen(), color.getBlue()};
        double luminance = 0;
        for (int i = 0; i < channels.length; i++) {
            double channel = channels[i] / 255.0;
            luminance += weights[i] * (channel <= .04045 ? channel / 12.92
                    : Math.pow((channel + .055) / 1.055, 2.4));
        }
        return luminance;
    }
}
