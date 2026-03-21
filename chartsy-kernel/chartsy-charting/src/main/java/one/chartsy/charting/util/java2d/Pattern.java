package one.chartsy.charting.util.java2d;

import one.chartsy.charting.PlotStyle;

import java.awt.Canvas;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.TexturePaint;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.PixelGrabber;
import java.util.Objects;

import javax.imageio.ImageIO;

/// Recolorable [TexturePaint] backed by a catalog of built-in hatch, grid, stipple, and bitmap
/// tiles.
///
/// The integer constants on this class identify the available pattern families. Most entries are
/// generated procedurally, while a smaller set of decorative patterns is loaded from bundled image
/// resources and recolored so every non-background source pixel becomes the requested foreground
/// color.
///
/// A `null` background means "leave the tile transparent". That makes the pattern suitable for
/// overlay fills such as highlighted chart regions, which is how the charting module uses it in
/// practice.
///
/// Pattern instances behave as compact value objects keyed by built-in type plus configured colors.
/// [PlotStyle] relies on that value semantics when it copies or rehydrates fills without
/// preserving the underlying raster instance.
public class Pattern extends TexturePaint {
    public static final int GRAY_5 = 0;
    public static final int GRAY_10 = 1;
    public static final int GRAY_20 = 2;
    public static final int GRAY_25 = 3;
    public static final int GRAY_30 = 4;
    public static final int GRAY_40 = 5;
    public static final int GRAY_50 = 6;
    public static final int GRAY_60 = 7;
    public static final int GRAY_70 = 8;
    public static final int GRAY_75 = 9;
    public static final int GRAY_80 = 10;
    public static final int GRAY_90 = 11;
    public static final int DOWN_LIGHT_DIAGONAL = 12;
    public static final int DOWN_DARK_DIAGONAL = 13;
    public static final int UP_LIGHT_DIAGONAL = 14;
    public static final int UP_DARK_DIAGONAL = 15;
    public static final int LIGHT_VERTICAL = 16;
    public static final int NARROW_VERTICAL = 17;
    public static final int LIGHT_HORIZONTAL = 18;
    public static final int NARROW_HORIZONTAL = 19;
    public static final int DOWN_DASHED_DIAGONAL = 20;
    public static final int UP_DASHED_DIAGONAL = 21;
    public static final int HORIZONTAL_DASH = 22;
    public static final int VERTICAL_DASH = 23;
    public static final int SMALL_GRID = 24;
    public static final int LARGE_GRID = 25;
    public static final int HORIZONTAL = 26;
    public static final int DARK_HORIZONTAL = 27;
    public static final int NARROW_DARK_HORIZONTAL = 28;
    public static final int VERTICAL = 29;
    public static final int DARK_VERTICAL = 30;
    public static final int NARROW_DARK_VERTICAL = 31;
    public static final int UP_DIAGONAL = 32;
    public static final int UP_THICK_DIAGONAL = 33;
    public static final int DOWN_DIAGONAL = 34;
    public static final int DOWN_THICK_DIAGONAL = 35;
    public static final int LARGE_DARK_GRID = 36;
    public static final int SMALL_DARK_GRID = 37;
    public static final int ALTERNATE_DOTS = 38;
    public static final int TINY_GRID = 39;
    public static final int DIAGONAL_GRID = 40;
    public static final int THICK_DIAGONAL_GRID = 41;
    public static final int SMALL_DIAGONAL_GRID = 42;
    public static final int DOWN_BLACK_DIAGONAL = 43;
    public static final int UP_BLACK_DIAGONAL = 44;
    public static final int TINY_STIPPLE = 45;
    public static final int SMALL_STIPPLE = 46;
    public static final int STIPPLE = 47;
    public static final int WAVES = 48;
    public static final int WAFFER = 49;
    public static final int BRICKS_HORIZONTAL = 50;
    public static final int BRICKS_VERTICAL = 51;
    public static final int BRICKS_DIAGONAL = 52;
    public static final int DOT_QUILT = 53;
    public static final int BALLS = 54;
    public static final int THATCHES = 55;
    public static final int LIGHT_LOSANGES = 56;
    public static final int VSHAPE = 57;
    public static final int DARK_SAND = 58;
    public static final int LIGHT_SAND = 59;
    public static final int SCALES = 60;
    public static final int OBLIQUE_TILES = 61;
    public static final int DIAGONAL_TILES = 62;
    public static final int AZTEC = 63;
    public static final int ALTERNATE_DIAGONAL = 64;
    public static final int CIRCLE_DOT = 65;
    public static final int SMALL_CHESSBOARD = 66;
    public static final int ARCHES = 67;
    public static final int COBBLE_DIAGONAL = 68;
    public static final int LAST_USED = 68;
    private static final Canvas IMAGE_OBSERVER = new Canvas();
    
    private static Color requireColor(Color color, boolean allowNull) {
        if (color == null && !allowNull)
            throw new IllegalArgumentException("Null color not allowed as color of Pattern");
        return color;
    }
    
    /// Loads a bundled bitmap tile and recolors every non-white source pixel to `foreground`.
    ///
    /// Resource lookup failures and interrupted pixel grabs return `null`, matching the historical
    /// fallback behavior used by [#makeImage(int, Color, Color)].
    private static BufferedImage loadBundledPatternImage(String resourceName, Color foreground, Color background) {
        try {
            Image sourceImage = ImageIO.read(Pattern.class.getResource(resourceName));
            if (sourceImage == null)
                return null;

            int width = sourceImage.getWidth(IMAGE_OBSERVER);
            int height = sourceImage.getHeight(IMAGE_OBSERVER);
            int[] sourcePixels = new int[width * height];
            PixelGrabber grabber = new PixelGrabber(sourceImage, 0, 0, width, height, sourcePixels, 0, width);
            grabber.grabPixels();

            BufferedImage image = createImage(width, height, background);
            int foregroundRgb = foreground.getRGB();
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    if (sourcePixels[x + y * width] != -1)
                        image.setRGB(x, y, foregroundRgb);
                }
            }
            return image;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception e) {
            return null;
        }
    }
    
    /// Creates a blank tile image, optionally prefilled with a background color.
    protected static BufferedImage createImage(int width, int height, Color background) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        if (background != null) {
            Graphics g = image.getGraphics();
            try {
                g.setColor(background);
                g.fillRect(0, 0, width, height);
            } finally {
                g.dispose();
            }
        }
        return image;
    }

    private static void setPixels(BufferedImage image, int rgb, int... coordinates) {
        for (int index = 0; index < coordinates.length; index += 2) {
            image.setRGB(coordinates[index], coordinates[index + 1], rgb);
        }
    }
    
    /// Creates the tile image for one of this class's built-in pattern identifiers.
    ///
    /// The common hatch and stipple variants are generated procedurally so they do not depend on
    /// external resources. Decorative patterns still delegate to bundled bitmap tiles. Unknown
    /// `type` values return `null`.
    protected static BufferedImage makeImage(int type, Color foreground, Color background) {
        BufferedImage image = null;
        int foregroundRgb = requireColor(foreground, false).getRGB();

        switch (type) {
        case GRAY_5:
            image = createImage(8, 8, background);
            setPixels(image, foregroundRgb, 0, 7, 4, 3);
            break;

        case GRAY_10:
            image = createImage(8, 4, background);
            setPixels(image, foregroundRgb, 0, 3, 4, 1);
            break;

        case GRAY_20:
            image = createImage(4, 4, background);
            setPixels(image, foregroundRgb, 0, 3, 2, 1);
            break;

        case GRAY_25:
            image = createImage(4, 2, background);
            setPixels(image, foregroundRgb, 0, 1, 2, 0);
            break;

        case GRAY_30:
            image = createImage(4, 4, background);
            setPixels(image, foregroundRgb, 1, 0, 0, 1, 2, 1, 3, 2, 0, 3, 2, 3);
            break;

        case GRAY_40:
            image = createImage(8, 6, background);
            for (int column = 0; column < 8; column++) {
                for (int row = (column + 1) % 2; row < 6; row += 2) {
                    if ((column != 1 || row != 0) && (column != 5 || row != 4)) {
                        image.setRGB(column, row, foregroundRgb);
                    }
                }
            }
            break;

        case GRAY_50:
            image = createImage(2, 2, background);
            setPixels(image, foregroundRgb, 0, 1, 1, 0);
            break;

        case GRAY_60:
            image = createImage(4, 4, background);
            for (int column = 0; column < 4; column++) {
                for (int row = column % 2; row < 4; row += 2) {
                    image.setRGB(column, row, foregroundRgb);
                }
            }
            setPixels(image, foregroundRgb, 0, 1, 2, 3);
            break;

        case GRAY_70:
            image = createImage(4, 2, background);
            setPixels(image, foregroundRgb, 0, 0, 2, 0, 3, 0, 0, 1, 1, 1, 2, 1);
            break;

        case GRAY_75:
            image = createImage(4, 4, background);
            for (int x = 0; x < 4; x++) {
                for (int y = 0; y < 4; y++) {
                    if ((x != 3 || y != 1) && (x != 1 || y != 3)) {
                        image.setRGB(x, y, foregroundRgb);
                    }
                }
            }
            break;

        case GRAY_80:
            image = createImage(8, 4, background);
            for (int x = 0; x < 8; x++) {
                for (int y = 0; y < 4; y++) {
                    if ((x != 6 || y != 1) && (x != 2 || y != 3)) {
                        image.setRGB(x, y, foregroundRgb);
                    }
                }
            }
            break;

        case GRAY_90:
            image = createImage(8, 8, background);
            for (int x = 0; x < 8; x++) {
                for (int y = 0; y < 8; y++) {
                    if ((x != 7 || y != 0) && (x != 3 || y != 4)) {
                        image.setRGB(x, y, foregroundRgb);
                    }
                }
            }
            break;

        case DOWN_LIGHT_DIAGONAL:
            image = createImage(4, 4, background);
            setPixels(image, foregroundRgb, 3, 0, 0, 1, 1, 2, 2, 3);
            break;

        case DOWN_DARK_DIAGONAL:
            image = createImage(4, 4, background);
            setPixels(image, foregroundRgb, 3, 0, 0, 1, 1, 2, 2, 3, 0, 0, 1, 1, 2, 2, 3, 3);
            break;

        case UP_LIGHT_DIAGONAL:
            image = createImage(4, 4, background);
            setPixels(image, foregroundRgb, 3, 3, 2, 0, 1, 1, 0, 2);
            break;

        case UP_DARK_DIAGONAL:
            image = createImage(4, 4, background);
            setPixels(image, foregroundRgb, 3, 3, 2, 0, 1, 1, 0, 2, 3, 0, 2, 1, 1, 2, 0, 3);
            break;

        case LIGHT_VERTICAL:
            image = createImage(2, 1, background);
            setPixels(image, foregroundRgb, 1, 0);
            break;

        case NARROW_VERTICAL:
            image = createImage(4, 1, background);
            setPixels(image, foregroundRgb, 1, 0);
            break;

        case LIGHT_HORIZONTAL:
            image = createImage(1, 2, background);
            setPixels(image, foregroundRgb, 0, 1);
            break;

        case NARROW_HORIZONTAL:
            image = createImage(1, 4, background);
            setPixels(image, foregroundRgb, 0, 1);
            break;

        case DOWN_DASHED_DIAGONAL:
            image = createImage(4, 8, background);
            setPixels(image, foregroundRgb, 3, 0, 0, 5, 1, 6, 2, 7);
            break;

        case UP_DASHED_DIAGONAL:
            image = createImage(4, 8, background);
            setPixels(image, foregroundRgb, 0, 6, 1, 5, 2, 4, 3, 3);
            break;

        case HORIZONTAL_DASH:
            image = createImage(8, 8, background);
            setPixels(image, foregroundRgb, 0, 3, 1, 3, 2, 3, 3, 3, 4, 7, 5, 7, 6, 7, 7, 7);
            break;

        case VERTICAL_DASH:
            image = createImage(8, 8, background);
            setPixels(image, foregroundRgb, 0, 1, 0, 2, 0, 3, 0, 4, 4, 0, 4, 5, 4, 6, 4, 7);
            break;
            
        case SMALL_GRID:
            image = createImage(4, 4, background);
            for (int index = 0; index < 4; index++) {
                image.setRGB(1, index, foregroundRgb);
                image.setRGB(index, 3, foregroundRgb);
            }
            break;

        case LARGE_GRID:
            image = createImage(8, 8, background);
            for (int index = 0; index < 8; index++) {
                image.setRGB(1, index, foregroundRgb);
                image.setRGB(index, 1, foregroundRgb);
            }
            break;

        case HORIZONTAL:
            image = createImage(1, 8, background);
            setPixels(image, foregroundRgb, 0, 0);
            break;

        case DARK_HORIZONTAL:
            image = createImage(1, 8, background);
            setPixels(image, foregroundRgb, 0, 0, 0, 1);
            break;

        case NARROW_DARK_HORIZONTAL:
            image = createImage(1, 4, background);
            setPixels(image, foregroundRgb, 0, 0, 0, 1);
            break;

        case VERTICAL:
            image = createImage(8, 1, background);
            setPixels(image, foregroundRgb, 0, 0);
            break;

        case DARK_VERTICAL:
            image = createImage(8, 1, background);
            setPixels(image, foregroundRgb, 0, 0, 1, 0);
            break;

        case NARROW_DARK_VERTICAL:
            image = createImage(4, 1, background);
            setPixels(image, foregroundRgb, 0, 0, 1, 0);
            break;

        case UP_DIAGONAL:
            image = createImage(8, 8, background);
            for (int row = 7; row >= 0; row--) {
                image.setRGB(7 - row, row, foregroundRgb);
            }
            break;

        case UP_THICK_DIAGONAL:
            image = createImage(8, 8, background);
            for (int row = 7; row >= 0; row--) {
                image.setRGB(7 - row, row, foregroundRgb);
                image.setRGB((8 - row) % 8, row, foregroundRgb);
            }
            break;

        case DOWN_DIAGONAL:
            image = createImage(8, 8, background);
            for (int row = 7; row >= 0; row--) {
                image.setRGB(row, row, foregroundRgb);
            }
            break;

        case DOWN_THICK_DIAGONAL:
            image = createImage(8, 8, background);
            for (int row = 7; row >= 0; row--) {
                image.setRGB(row, row, foregroundRgb);
                image.setRGB((row + 1) % 8, row, foregroundRgb);
            }
            break;

        case LARGE_DARK_GRID:
            image = createImage(8, 8, background);
            for (int index = 0; index < 8; index++) {
                setPixels(image, foregroundRgb, 1, index, index, 1, 0, index, index, 0);
            }
            break;

        case SMALL_DARK_GRID:
            image = createImage(4, 4, background);
            for (int index = 0; index < 4; index++) {
                setPixels(image, foregroundRgb, 1, index, 0, index, index, 3, index, 2);
            }
            break;

        case ALTERNATE_DOTS:
            image = loadBundledPatternImage("pattern_alternate_dots.gif", foreground, background);
            break;

        case TINY_GRID:
            image = createImage(2, 2, background);
            setPixels(image, foregroundRgb, 1, 0, 1, 1, 0, 1);
            break;

        case DIAGONAL_GRID:
            image = createImage(8, 8, background);
            for (int row = 7; row >= 0; row--) {
                image.setRGB(row, row, foregroundRgb);
                image.setRGB(7 - row, row, foregroundRgb);
            }
            break;

        case THICK_DIAGONAL_GRID:
            image = createImage(8, 8, background);
            for (int row = 7; row >= 0; row--) {
                setPixels(image, foregroundRgb, row, row, (row + 1) % 8, row, 7 - row, row, (8 - row) % 8, row);
            }
            break;

        case SMALL_DIAGONAL_GRID:
            image = createImage(4, 4, background);
            for (int row = 3; row >= 0; row--) {
                image.setRGB(row, row, foregroundRgb);
            }
            for (int row = 2; row >= 0; row--) {
                image.setRGB(3 - row, row + 1, foregroundRgb);
            }
            break;

        case DOWN_BLACK_DIAGONAL:
            image = createImage(4, 4, background);
            for (int row = 3; row >= 0; row--) {
                setPixels(image, foregroundRgb, row, row, (row + 1) % 4, row, (row + 2) % 4, row);
            }
            break;

        case UP_BLACK_DIAGONAL:
            image = createImage(4, 4, background);
            for (int row = 3; row >= 0; row--) {
                setPixels(image, foregroundRgb, 3 - row, row, (4 - row) % 4, row, (5 - row) % 4, row);
            }
            break;

        case TINY_STIPPLE:
            image = createImage(2, 2, background);
            setPixels(image, foregroundRgb, 0, 0);
            break;

        case SMALL_STIPPLE:
            image = createImage(4, 4, background);
            setPixels(image, foregroundRgb, 0, 0);
            break;

        case STIPPLE:
            image = createImage(8, 8, background);
            setPixels(image, foregroundRgb, 0, 0);
            break;

        case WAVES:
            image = loadBundledPatternImage("pattern_waves.gif", foreground, background);
            break;

        case WAFFER:
            image = loadBundledPatternImage("pattern_waffer.gif", foreground, background);
            break;

        case BRICKS_HORIZONTAL:
            image = loadBundledPatternImage("pattern_bricks_horizontal.gif", foreground, background);
            break;

        case BRICKS_VERTICAL:
            image = loadBundledPatternImage("pattern_bricks_vertical.gif", foreground, background);
            break;

        case BRICKS_DIAGONAL:
            image = loadBundledPatternImage("pattern_bricks_diagonal.gif", foreground, background);
            break;

        case DOT_QUILT:
            image = loadBundledPatternImage("pattern_dot_quilt.gif", foreground, background);
            break;

        case BALLS:
            image = loadBundledPatternImage("pattern_balls.gif", foreground, background);
            break;

        case THATCHES:
            image = loadBundledPatternImage("pattern_thatches.gif", foreground, background);
            break;

        case LIGHT_LOSANGES:
            image = loadBundledPatternImage("pattern_light_losange.gif", foreground, background);
            break;

        case VSHAPE:
            image = loadBundledPatternImage("pattern_vshape.gif", foreground, background);
            break;

        case DARK_SAND:
            image = loadBundledPatternImage("pattern_dark_sand.gif", foreground, background);
            break;

        case LIGHT_SAND:
            image = loadBundledPatternImage("pattern_light_sand.gif", foreground, background);
            break;

        case SCALES:
            image = loadBundledPatternImage("pattern_scales.gif", foreground, background);
            break;

        case OBLIQUE_TILES:
            image = loadBundledPatternImage("pattern_oblique_tiles.gif", foreground, background);
            break;

        case DIAGONAL_TILES:
            image = loadBundledPatternImage("pattern_diagonal_tiles.gif", foreground, background);
            break;

        case AZTEC:
            image = loadBundledPatternImage("pattern_aztec.gif", foreground, background);
            break;

        case ALTERNATE_DIAGONAL:
            image = createImage(8, 8, background);
            setPixels(image, foregroundRgb, 0, 0, 1, 1, 2, 2, 4, 6, 5, 5, 6, 4);
            break;

        case CIRCLE_DOT:
            image = loadBundledPatternImage("pattern_circle_dot.gif", foreground, background);
            break;

        case SMALL_CHESSBOARD:
            image = createImage(4, 4, background);
            setPixels(image, foregroundRgb, 0, 0, 1, 0, 0, 1, 1, 1, 2, 2, 3, 2, 2, 3, 3, 3);
            break;

        case ARCHES:
            image = loadBundledPatternImage("pattern_arches.gif", foreground, background);
            break;

        case COBBLE_DIAGONAL:
            image = loadBundledPatternImage("pattern_cobble_diagonal.gif", foreground, background);
            break;

        default:
            break;
        }
        return image;
    }
    
    protected int type;
    
    protected Color foreground;
    
    protected Color background;
    
    private static BufferedImage requirePatternImage(int type, Color foreground, Color background) {
        BufferedImage image = makeImage(type, requireColor(foreground, false), requireColor(background, true));
        if (image == null)
            throw new IllegalArgumentException("Unknown pattern type: " + type);
        return image;
    }
    
    /// Wraps a raw tile image in a [TexturePaint] anchored to the image's natural size.
    protected Pattern(BufferedImage image) {
        super(image, new Rectangle2D.Float(0.0f, 0.0f, image.getWidth(), image.getHeight()));
    }
    
    /// Creates one of the built-in recolorable pattern fills.
    ///
    /// Construction eagerly validates the requested type by generating the backing tile once. That
    /// keeps later paint operations free from lazy lookup failures.
    ///
    /// @param type one of this class's pattern constants from `0` through [#LAST_USED]
    /// @param foreground foreground color painted into the tile's marked pixels
    /// @param background optional background color. `null` leaves the tile background transparent
    /// @throws IllegalArgumentException if `foreground` is `null` or `type` does not name a supported
    ///                                  built-in pattern
    public Pattern(int type, Color foreground, Color background) {
        this(Pattern.requirePatternImage(type, foreground, background));
        this.type = type;
        this.foreground = foreground;
        this.background = background;
    }
    
    /// Compares pattern identity by built-in type and configured colors rather than by the
    /// generated tile image instance.
    @Override
    public boolean equals(Object obj) {
        return obj == this || obj instanceof Pattern other
                && type == other.type
                && Objects.equals(foreground, other.foreground)
                && Objects.equals(background, other.background);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(type, foreground, background);
    }
    
    /// Returns the configured background color, or `null` when the tile background is transparent.
    public Color getBackground() {
        return background;
    }
    
    /// Returns the configured foreground color.
    public Color getForeground() {
        return foreground;
    }
    
    /// Returns the built-in pattern identifier used to create this texture.
    public int getType() {
        return type;
    }
}
