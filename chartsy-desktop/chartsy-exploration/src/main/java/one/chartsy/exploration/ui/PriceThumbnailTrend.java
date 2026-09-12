package one.chartsy.exploration.ui;

import one.chartsy.misc.PriceSeriesThumbnail;

import java.awt.Color;

/** Describes the displayed price window, using its existing summary statistics. */
enum PriceThumbnailTrend {
    BULLISH("Strongly bullish", 0x008F3D, 0x29E675),
    BEARISH("Strongly bearish", 0xDC2336, 0xFF7272),
    FLAT("Mostly flat", 0x5C6675, 0xC8D2DE),
    MIXED("Mixed", 0x76518F, 0xCFB4E9),
    UNAVAILABLE("Direction unavailable", 0x5C6675, 0xC8D2DE);

    final String label;
    private final Color darkInk;
    private final Color lightInk;

    PriceThumbnailTrend(String label, int darkInk, int lightInk) {
        this.label = label;
        this.darkInk = new Color(darkInk);
        this.lightInk = new Color(lightInk);
    }

    static PriceThumbnailTrend of(PriceSeriesThumbnail prices) {
        if (prices.size() < 2)
            return UNAVAILABLE;
        double first = prices.closeAt(0);
        double last = prices.closeAt(prices.size() - 1);
        if (!(first > 0) || !(last > 0) || !Double.isFinite(first) || !Double.isFinite(last))
            return UNAVAILABLE;
        double range = prices.max() - prices.min();
        // Allow rounding at percentage boundaries, independent of the symbol's price units.
        double tolerance = 8 * Math.ulp(Math.max(first, prices.max()));
        if (range <= first * .05 + tolerance)
            return FLAT;
        double move = last - first;
        // A strong move must be substantial and dominate the window's total price range.
        double strongMove = Math.max(first * .15, range * .60);
        if (move + tolerance >= strongMove)
            return BULLISH;
        if (-move + tolerance >= strongMove)
            return BEARISH;
        return MIXED;
    }

    Color colorOn(Color background) {
        Color ink = contrast(darkInk, background) >= contrast(lightInk, background) ? darkInk : lightInk;
        // Preserve the hue on custom selection/theme colors while keeping thin lines legible.
        Color pole = luminance(background) < .18 ? Color.WHITE : Color.BLACK;
        for (int step = 0; contrast(ink, background) < 3 && step < 8; step++)
            ink = new Color((ink.getRed() * 3 + pole.getRed()) / 4,
                    (ink.getGreen() * 3 + pole.getGreen()) / 4,
                    (ink.getBlue() * 3 + pole.getBlue()) / 4);
        return ink;
    }

    static double contrast(Color a, Color b) {
        double x = luminance(a), y = luminance(b);
        return (Math.max(x, y) + .05) / (Math.min(x, y) + .05);
    }

    private static double luminance(Color color) {
        return .2126 * linear(color.getRed()) + .7152 * linear(color.getGreen()) + .0722 * linear(color.getBlue());
    }

    private static double linear(int channel) {
        double value = channel / 255.0;
        return value <= .04045 ? value / 12.92 : Math.pow((value + .055) / 1.055, 2.4);
    }
}
