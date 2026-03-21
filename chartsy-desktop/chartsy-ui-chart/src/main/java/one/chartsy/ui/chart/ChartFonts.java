/*
 * Copyright 2026 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.ui.chart;

import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.FontFormatException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class ChartFonts {
    private static final Logger LOG = Logger.getLogger(ChartFonts.class.getName());
    private static final String REGULAR_RESOURCE = "/one/chartsy/ui/chart/fonts/Iosevka-Regular.ttf";
    private static final String BOLD_RESOURCE = "/one/chartsy/ui/chart/fonts/Iosevka-Bold.ttf";
    private static final List<String> FALLBACK_FAMILIES = List.of(
            "Iosevka",
            "Iosevka Term",
            "Iosevka Fixed",
            "Cascadia Mono",
            "JetBrains Mono",
            "Consolas",
            Font.MONOSPACED
    );
    private static final float DEFAULT_SCALE_SIZE = 11.25f;
    private static final float LEGEND_SIZE_DELTA = 0.0f;
    private static final float FOOTER_UPPER_SIZE_DELTA = -0.55f;
    private static final float FOOTER_LOWER_SIZE_DELTA = -0.25f;
    private static final float ANNOTATION_SIZE_DELTA = -0.25f;

    private static final Font REGULAR_BASE = loadFont(REGULAR_RESOURCE, Font.PLAIN);
    private static final Font BOLD_BASE = loadFont(BOLD_RESOURCE, Font.BOLD);

    private ChartFonts() {
    }

    public static Font defaultChartFont() {
        return regular(DEFAULT_SCALE_SIZE);
    }

    public static Font scaleFont(ChartProperties properties) {
        return regular(baseSize(properties));
    }

    public static Font footerUpperFont(ChartProperties properties) {
        return regular(Math.max(9.9f, baseSize(properties) + FOOTER_UPPER_SIZE_DELTA));
    }

    public static Font footerLowerFont(ChartProperties properties) {
        return bold(Math.max(10.1f, baseSize(properties) + FOOTER_LOWER_SIZE_DELTA));
    }

    public static Font legendFont(ChartProperties properties) {
        return regular(baseSize(properties) + LEGEND_SIZE_DELTA);
    }

    public static Font legendSymbolFont(ChartProperties properties) {
        return bold(baseSize(properties) + LEGEND_SIZE_DELTA);
    }

    public static Font scaleAnnotationFont(ChartProperties properties) {
        return regular(Math.max(10.7f, baseSize(properties) + ANNOTATION_SIZE_DELTA));
    }

    private static float baseSize(ChartProperties properties) {
        Font font = (properties == null) ? null : properties.getFont();
        return (font != null) ? font.getSize2D() : DEFAULT_SCALE_SIZE;
    }

    private static Font regular(float size) {
        return REGULAR_BASE.deriveFont(Font.PLAIN, size);
    }

    private static Font bold(float size) {
        return BOLD_BASE.deriveFont(Font.BOLD, size);
    }

    private static Font loadFont(String resourcePath, int style) {
        try (InputStream input = ChartFonts.class.getResourceAsStream(resourcePath)) {
            if (input != null) {
                Font font = Font.createFont(Font.TRUETYPE_FONT, input);
                GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(font);
                return font.deriveFont(style, DEFAULT_SCALE_SIZE);
            }
        } catch (IOException ex) {
            throw new UncheckedIOException("Unable to load font resource " + resourcePath, ex);
        } catch (FontFormatException ex) {
            LOG.log(Level.FINE, "Embedded chart font {0} is not supported by the current JVM, using fallback font",
                    resourcePath);
        } catch (Exception ex) {
            LOG.log(Level.WARNING, "Unable to initialize embedded chart font " + resourcePath + ", using fallback font", ex);
        }
        return fallbackFont(style);
    }

    private static Font fallbackFont(int style) {
        for (String family : FALLBACK_FAMILIES) {
            Font font = new Font(family, style, Math.round(DEFAULT_SCALE_SIZE));
            if (family.equals(font.getFamily()) || Font.MONOSPACED.equals(family))
                return font.deriveFont(style, DEFAULT_SCALE_SIZE);
        }
        return new Font(Font.MONOSPACED, style, Math.round(DEFAULT_SCALE_SIZE)).deriveFont(style, DEFAULT_SCALE_SIZE);
    }
}
