package one.chartsy.charting.internal;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.PrintGraphics;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.font.FontRenderContext;
import java.awt.font.LineBreakMeasurer;
import java.awt.font.TextAttribute;
import java.awt.font.TextLayout;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.awt.print.PrinterGraphics;
import java.io.Serializable;
import java.text.AttributedString;
import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.List;

import javax.swing.SwingConstants;
import javax.swing.UIManager;

import one.chartsy.charting.LabelRenderer;
import one.chartsy.charting.Scale;
import one.chartsy.charting.util.ChartUtil;
import one.chartsy.charting.util.ColorUtil;
import one.chartsy.charting.util.GraphicUtil;
import one.chartsy.charting.util.MathUtil;

/// Measures, caches, and paints a single centered text block with optional wrapping, rotation,
/// and halo outlining.
///
/// [LabelRenderer] and [Scale] reuse this type when the same text must be measured repeatedly
/// before it is painted. Layout is computed lazily from the mutable state inherited from
/// [TextRendererParameters]; changes to text, font, antialiasing, or wrapping invalidate that
/// cached layout, while rotation only invalidates the derived bounds and background shapes.
///
/// Bounds and background shapes are returned in the renderer's local coordinate system and are
/// owned by the renderer. Treat the returned objects as read-only transient views: later state
/// changes may replace or mutate them.
public class TextRenderer extends TextRendererParameters implements Serializable {
    private static final boolean SNAP_UNROTATED_TEXT_TO_INTEGER_PIXELS =
            ChartUtil.compareToJavaVersion(1, 4, 0) >= 0
                    && ChartUtil.compareToJavaVersion(1, 5, 0) < 0;
    private static final int OUTLINE_PADDING = 8;
    private static final double MAX_RASTER_OUTLINE_DETERMINANT = 10_000.0;
    private static final float[] OUTLINE_STROKE_WIDTHS = {4.0f, 2.0f, 1.0f, 0.5f};
    private static final int[] OUTLINE_ALPHAS = {26, 77, 153, 204};
    private static final Kernel OUTLINE_KERNEL = createOutlineKernel();
    private static final ConvolveOp OUTLINE_BLUR = new ConvolveOp(OUTLINE_KERNEL);

    private boolean outlined;
    private int alignment;
    private transient boolean layoutValid;
    private transient String[] lines;
    private transient TextLayout[] lineLayouts;
    private transient boolean boundsValid;
    private transient Rectangle2D bounds;
    private transient Rectangle2D rotatedBounds;
    private transient boolean backgroundShapesValid;
    private transient Shape backgroundShape;
    private transient Shape rotatedBackgroundShape;

    /// Creates a renderer for an empty, unrotated text block.
    public TextRenderer() {
        this("");
    }

    /// Creates a renderer for the supplied text with zero rotation.
    ///
    /// @param text source text to lay out; must not be `null`
    /// @throws IllegalArgumentException if `text` is `null`
    public TextRenderer(String text) {
        this(text, 0.0);
    }

    /// Creates a renderer for the supplied text and rotation.
    ///
    /// The text block is still painted around its center point; rotation only changes the local
    /// coordinate system used by [#draw(Graphics, Color, Color, double, double)],
    /// [#getBounds(boolean)], and [#getBackgroundShape(boolean)].
    ///
    /// @param text source text to lay out; must not be `null`
    /// @param rotation rotation angle in degrees
    /// @throws IllegalArgumentException if `text` is `null`
    public TextRenderer(String text, double rotation) {
        alignment = SwingConstants.LEFT;
        lines = new String[0];
        lineLayouts = new TextLayout[0];
        if (text == null) {
            throw new IllegalArgumentException();
        }
        super.text = text;
        super.rotation = rotation;
    }

    private static Kernel createOutlineKernel() {
        float[] kernelData = new float[81];
        int index = 0;
        for (int y = -4; y <= 4; y++) {
            for (int x = -4; x <= 4; x++) {
                int distanceSquared = x * x + y * y;
                kernelData[index++] =
                        0.94f * (float) Math.exp(-distanceSquared / 2.0f)
                                + 0.05f * (float) Math.exp(-distanceSquared / 8.0f)
                                + 0.01f * (float) Math.exp(-distanceSquared / 18.0f);
            }
        }
        return new Kernel(9, 9, kernelData);
    }

    private static String[] splitLinesPreservingBlankLines(String text) {
        int lineCount = 1;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                lineCount++;
            }
        }

        String[] result = new String[lineCount];
        if (lineCount == 1) {
            result[0] = text;
            return result;
        }

        int lineStart = 0;
        for (int i = 0; i < lineCount; i++) {
            int lineEnd = text.indexOf('\n', lineStart);
            if (lineEnd < 0) {
                lineEnd = text.length();
            }
            result[i] = (lineEnd == lineStart) ? " " : text.substring(lineStart, lineEnd);
            lineStart = lineEnd + 1;
        }
        return result;
    }

    private static double computeAlignedLineX(TextLayout layout, double blockWidth, int alignment) {
        return switch (alignment) {
            case SwingConstants.CENTER -> (blockWidth - layout.getAdvance()) / 2.0;
            case SwingConstants.LEFT -> layout.isLeftToRight() ? 0.0 : blockWidth - layout.getAdvance();
            case SwingConstants.RIGHT -> layout.isLeftToRight() ? blockWidth - layout.getAdvance() : 0.0;
            default -> throw new IllegalStateException("Unsupported alignment: " + alignment);
        };
    }

    private static Color withAlpha(Color color, int alpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
    }

    private void ensureBounds() {
        if (boundsValid) {
            return;
        }

        TextLayout firstLayout = lineLayouts[0];
        double maxWidth = (firstLayout == null) ? 0.0 : firstLayout.getAdvance();
        double totalHeight = (firstLayout == null) ? 0.0 : firstLayout.getAscent() + firstLayout.getDescent();
        for (int i = 1; i < lineLayouts.length; i++) {
            TextLayout layout = lineLayouts[i];
            if (layout != null) {
                maxWidth = Math.max(maxWidth, layout.getAdvance());
                totalHeight += layout.getAscent() + layout.getDescent() + layout.getLeading();
            }
        }

        if (bounds == null) {
            bounds = new Rectangle2D.Double();
        }
        bounds.setRect(0.0, 0.0, maxWidth, totalHeight);

        if (super.rotation == 0.0) {
            rotatedBounds = bounds;
        } else {
            AffineTransform rotationTransform =
                    AffineTransform.getRotateInstance(MathUtil.toRadians(super.rotation));
            rotatedBounds = GraphicUtil.transform((Rectangle2D) bounds.clone(), rotationTransform);
        }
        boundsValid = true;
    }

    private Shape createTextOutline(double centerX, double centerY) {
        if (lines.length <= 1) {
            TextLayout layout = lineLayouts[0];
            if (layout == null) {
                return null;
            }
            double baselineX = centerX - layout.getAdvance() / 2.0;
            double baselineY = centerY + (layout.getAscent() - layout.getDescent()) / 2.0;
            if (SNAP_UNROTATED_TEXT_TO_INTEGER_PIXELS && getRotation() == 0.0) {
                baselineX = Math.floor(baselineX);
                baselineY = Math.floor(baselineY);
            }
            return layout.getOutline(AffineTransform.getTranslateInstance(baselineX, baselineY));
        }

        GeneralPath outline = new GeneralPath();
        Rectangle2D blockBounds = getBounds(false);
        double blockWidth = blockBounds.getWidth();
        double baselineX = centerX - blockWidth / 2.0;
        double baselineY = centerY - blockBounds.getHeight() / 2.0;

        for (TextLayout layout : lineLayouts) {
            if (layout == null) {
                continue;
            }
            baselineY += layout.getAscent();
            double lineX = baselineX + computeAlignedLineX(layout, blockWidth, alignment);
            if (SNAP_UNROTATED_TEXT_TO_INTEGER_PIXELS && getRotation() == 0.0) {
                lineX = Math.floor(lineX);
                baselineY = Math.floor(baselineY);
            }
            outline.append(layout.getOutline(AffineTransform.getTranslateInstance(lineX, baselineY)), false);
            baselineY += layout.getDescent() + layout.getLeading();
        }
        return outline;
    }

    private void drawText(Graphics2D g, Color textColor, double centerX, double centerY) {
        g.setColor(textColor);
        g.setFont(getFont());

        AffineTransform originalTransform = null;
        if (getRotation() != 0.0) {
            originalTransform = g.getTransform();
            g.transform(AffineTransform.getRotateInstance(Math.toRadians(getRotation()), centerX, centerY));
        }

        try {
            drawPreparedText(g, centerX, centerY);
        } finally {
            if (originalTransform != null) {
                g.setTransform(originalTransform);
            }
        }
    }

    private void drawPreparedText(Graphics2D g, double centerX, double centerY) {
        if (lines.length <= 1) {
            TextLayout layout = lineLayouts[0];
            if (layout == null) {
                return;
            }
            double baselineX = centerX - layout.getAdvance() / 2.0;
            double baselineY = centerY + (layout.getAscent() - layout.getDescent()) / 2.0;
            if (SNAP_UNROTATED_TEXT_TO_INTEGER_PIXELS && getRotation() == 0.0) {
                baselineX = Math.floor(baselineX);
                baselineY = Math.floor(baselineY);
            }
            layout.draw(g, (float) baselineX, (float) baselineY);
            return;
        }

        Rectangle2D blockBounds = getBounds(false);
        double blockWidth = blockBounds.getWidth();
        double baselineX = centerX - blockWidth / 2.0;
        double baselineY = centerY - blockBounds.getHeight() / 2.0;

        for (TextLayout layout : lineLayouts) {
            if (layout == null) {
                continue;
            }
            baselineY += layout.getAscent();
            double lineX = baselineX + computeAlignedLineX(layout, blockWidth, alignment);
            if (SNAP_UNROTATED_TEXT_TO_INTEGER_PIXELS && getRotation() == 0.0) {
                lineX = Math.floor(lineX);
                baselineY = Math.floor(baselineY);
            }
            layout.draw(g, (float) lineX, (float) baselineY);
            baselineY += layout.getDescent() + layout.getLeading();
        }
    }

    private void ensureBackgroundShapes() {
        if (backgroundShapesValid) {
            return;
        }

        backgroundShape = createBackgroundShape();
        if (backgroundShape == null) {
            rotatedBackgroundShape = null;
        } else if (super.rotation == 0.0) {
            rotatedBackgroundShape = backgroundShape;
        } else {
            rotatedBackgroundShape =
                    AffineTransform.getRotateInstance(MathUtil.toRadians(super.rotation))
                            .createTransformedShape(backgroundShape);
        }
        backgroundShapesValid = true;
    }

    private Shape createBackgroundShape() {
        if (lines.length <= 1) {
            TextLayout layout = lineLayouts[0];
            if (layout == null) {
                return null;
            }
            double width = layout.getAdvance();
            double height = layout.getAscent() + layout.getDescent();
            return new Rectangle2D.Double(-width / 2.0, -height / 2.0, width, height);
        }

        Rectangle2D blockBounds = getBounds(false);
        double blockWidth = blockBounds.getWidth();
        double left = -blockWidth / 2.0;
        double top = -blockBounds.getHeight() / 2.0;

        int firstLayoutIndex = 0;
        while (firstLayoutIndex < lineLayouts.length && lineLayouts[firstLayoutIndex] == null) {
            firstLayoutIndex++;
        }
        if (firstLayoutIndex == lineLayouts.length) {
            return null;
        }

        GeneralPath shape = new GeneralPath();
        while (firstLayoutIndex < lineLayouts.length) {
            int runEnd = firstLayoutIndex + 1;
            while (runEnd < lineLayouts.length && lineLayouts[runEnd] != null) {
                runEnd++;
            }

            double[] lineTops = new double[runEnd - firstLayoutIndex];
            float[] lineLefts = new float[runEnd - firstLayoutIndex];
            float[] lineRights = new float[runEnd - firstLayoutIndex];

            double currentTop = top;
            for (int i = firstLayoutIndex; i < runEnd; i++) {
                TextLayout layout = lineLayouts[i];
                assert layout != null;
                lineTops[i - firstLayoutIndex] = currentTop;
                double lineX = left + computeAlignedLineX(layout, blockWidth, alignment);
                lineLefts[i - firstLayoutIndex] = (float) lineX;
                lineRights[i - firstLayoutIndex] = (float) (lineX + layout.getAdvance());
                currentTop += layout.getAscent() + layout.getDescent() + layout.getLeading();
            }

            shape.moveTo(lineLefts[0], (float) lineTops[0]);
            for (int i = firstLayoutIndex; i < runEnd; i++) {
                TextLayout layout = lineLayouts[i];
                assert layout != null;
                int offset = i - firstLayoutIndex;
                if (i == runEnd - 1) {
                    shape.lineTo(
                            lineLefts[offset],
                            (float) (lineTops[offset] + layout.getAscent() + layout.getDescent()));
                } else if (lineLefts[offset + 1] != lineLefts[offset]) {
                    double jointY = (lineLefts[offset + 1] < lineLefts[offset])
                            ? lineTops[offset + 1]
                            : lineTops[offset] + layout.getAscent() + layout.getDescent();
                    shape.lineTo(lineLefts[offset], (float) jointY);
                    shape.lineTo(lineLefts[offset + 1], (float) jointY);
                }
            }
            for (int i = runEnd - 1; i >= firstLayoutIndex; i--) {
                TextLayout layout = lineLayouts[i];
                assert layout != null;
                int offset = i - firstLayoutIndex;
                if (i == runEnd - 1) {
                    shape.lineTo(
                            lineRights[offset],
                            (float) (lineTops[offset] + layout.getAscent() + layout.getDescent()));
                } else if (lineRights[offset + 1] != lineRights[offset]) {
                    double jointY = (lineRights[offset + 1] < lineRights[offset])
                            ? lineTops[offset + 1]
                            : lineTops[offset] + layout.getAscent() + layout.getDescent();
                    shape.lineTo(lineRights[offset + 1], (float) jointY);
                    shape.lineTo(lineRights[offset], (float) jointY);
                }
            }
            shape.lineTo(lineRights[0], (float) lineTops[0]);
            shape.closePath();

            firstLayoutIndex = runEnd;
            while (firstLayoutIndex < lineLayouts.length && lineLayouts[firstLayoutIndex] == null) {
                firstLayoutIndex++;
            }
        }
        return shape;
    }

    private void ensureLayout() {
        if (layoutValid) {
            return;
        }

        lines = splitLinesPreservingBlankLines(getText());
        FontRenderContext frc = createFontRenderContext();
        Font font = getFont();

        if (super.wrappingWidth > 0.0f && isAutoWrapping()) {
            List<String> wrappedLines = new ArrayList<>();
            List<TextLayout> wrappedLayouts = new ArrayList<>();
            BreakIterator lineBreaks = BreakIterator.getLineInstance();

            for (String line : lines) {
                if (line.isEmpty()) {
                    wrappedLines.add(line);
                    wrappedLayouts.add(null);
                    continue;
                }

                AttributedString attributedLine = new AttributedString(line);
                attributedLine.addAttribute(TextAttribute.FONT, font);
                LineBreakMeasurer measurer = new LineBreakMeasurer(attributedLine.getIterator(), lineBreaks, frc);

                int lineLength = line.length();
                int lineStart = 0;
                while (lineStart < lineLength) {
                    int lineEnd = measurer.nextOffset(super.wrappingWidth, lineLength, true);
                    if (lineEnd == lineStart) {
                        lineEnd = lineBreaks.following(lineStart);
                    }

                    int textEnd = lineEnd;
                    while (textEnd > lineStart + 1 && Character.isSpaceChar(line.charAt(textEnd - 1))) {
                        textEnd--;
                    }

                    String wrappedLine = line.substring(lineStart, textEnd);
                    wrappedLines.add(wrappedLine);
                    wrappedLayouts.add(new TextLayout(wrappedLine, font, frc));

                    measurer.setPosition(lineEnd);
                    lineStart = lineEnd;
                }
            }

            lines = wrappedLines.toArray(String[]::new);
            lineLayouts = wrappedLayouts.toArray(TextLayout[]::new);
        } else {
            lineLayouts = new TextLayout[lines.length];
            for (int i = 0; i < lines.length; i++) {
                String line = lines[i];
                lineLayouts[i] = line.isEmpty() ? null : new TextLayout(line, font, frc);
            }
        }

        layoutValid = true;
        boundsValid = false;
        backgroundShapesValid = false;
    }

    private FontRenderContext createFontRenderContext() {
        return GraphicUtil.getFRC(isAntiAliased(), false);
    }

    private static boolean shouldRasterizeOutline(Graphics g, Graphics2D g2) {
        return !(g instanceof PrinterGraphics)
                && !(g instanceof PrintGraphics)
                && Math.abs(g2.getTransform().getDeterminant()) <= MAX_RASTER_OUTLINE_DETERMINANT;
    }

    private void normalizeGlyphCoverage(BufferedImage image, int[] rowBuffer) {
        for (int y = 0; y < image.getHeight(); y++) {
            image.getRGB(0, y, image.getWidth(), 1, rowBuffer, 0, image.getWidth());
            for (int x = 0; x < image.getWidth(); x++) {
                int argb = rowBuffer[x];
                if ((argb & 0xFF00_0000) == 0) {
                    continue;
                }
                int alpha = argb >>> 24;
                int red = (((argb & 0x00FF_0000) >>> 16) * 510 + alpha) / (2 * alpha);
                int green = (((argb & 0x0000_FF00) >>> 8) * 510 + alpha) / (2 * alpha);
                int blue = ((argb & 0x0000_00FF) * 510 + alpha) / (2 * alpha);
                rowBuffer[x] = alpha << 24
                        | Math.min(red, 255) << 16
                        | Math.min(green, 255) << 8
                        | Math.min(blue, 255);
            }
            image.setRGB(0, y, image.getWidth(), 1, rowBuffer, 0, image.getWidth());
        }
    }

    private static void tintOutlineImage(BufferedImage image, Color outlineColor, int[] rowBuffer) {
        int rgb = outlineColor.getRGB() & 0x00FF_FFFF;
        for (int y = 0; y < image.getHeight(); y++) {
            image.getRGB(0, y, image.getWidth(), 1, rowBuffer, 0, image.getWidth());
            for (int x = 0; x < image.getWidth(); x++) {
                int argb = rowBuffer[x];
                if ((argb & 0xFF00_0000) != 0) {
                    rowBuffer[x] = (argb & 0xFF00_0000) | rgb;
                }
            }
            image.setRGB(0, y, image.getWidth(), 1, rowBuffer, 0, image.getWidth());
        }
    }

    private void softenGlyphAlpha(BufferedImage image, int[] rowBuffer) {
        for (int y = 0; y < image.getHeight(); y++) {
            image.getRGB(0, y, image.getWidth(), 1, rowBuffer, 0, image.getWidth());
            for (int x = 0; x < image.getWidth(); x++) {
                int argb = rowBuffer[x];
                if ((argb & 0xFF00_0000) != 0) {
                    int alpha = (argb >>> 24) + 256;
                    rowBuffer[x] = (alpha / 2) << 24 | (argb & 0x00FF_FFFF);
                }
            }
            image.setRGB(0, y, image.getWidth(), 1, rowBuffer, 0, image.getWidth());
        }
    }

    private void drawRasterizedOutline(Graphics g, Graphics2D g2, Color textColor, Color outlineColor,
            double centerX, double centerY) {
        Rectangle2D rotatedBounds = getBounds(true);
        int imageX = (int) Math.floor(centerX - rotatedBounds.getWidth() * 0.5) - OUTLINE_PADDING;
        int imageY = (int) Math.floor(centerY - rotatedBounds.getHeight() * 0.5) - OUTLINE_PADDING;
        int imageWidth = (int) Math.ceil(centerX + rotatedBounds.getWidth() * 0.5) - imageX + OUTLINE_PADDING;
        int imageHeight = (int) Math.ceil(centerY + rotatedBounds.getHeight() * 0.5) - imageY + OUTLINE_PADDING;

        BufferedImage glyphImage = new BufferedImage(imageWidth, imageHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D glyphGraphics = glyphImage.createGraphics();
        try {
            glyphGraphics.setRenderingHints(g2.getRenderingHints());
            drawText(glyphGraphics, textColor, centerX - imageX, centerY - imageY);
        } finally {
            glyphGraphics.dispose();
        }

        int[] rowBuffer = new int[imageWidth];
        if (super.antiAliased) {
            normalizeGlyphCoverage(glyphImage, rowBuffer);
        }

        BufferedImage outlineImage = new BufferedImage(imageWidth, imageHeight, BufferedImage.TYPE_INT_ARGB);
        OUTLINE_BLUR.filter(glyphImage, outlineImage);
        tintOutlineImage(outlineImage, outlineColor, rowBuffer);
        g.drawImage(outlineImage, imageX, imageY, null);

        if (super.antiAliased) {
            softenGlyphAlpha(glyphImage, rowBuffer);
        }
        g.drawImage(glyphImage, imageX, imageY, null);
    }

    private void drawVectorOutline(Graphics2D g2, Color textColor, Color outlineColor,
            double centerX, double centerY) {
        Shape outline = createTextOutline(centerX, centerY);
        if (outline == null) {
            return;
        }

        Stroke originalStroke = g2.getStroke();
        Object originalAntialiasing = g2.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        try {
            g2.setRenderingHint(
                    RenderingHints.KEY_ANTIALIASING,
                    isAntiAliased() ? RenderingHints.VALUE_ANTIALIAS_ON : RenderingHints.VALUE_ANTIALIAS_OFF);
            for (int i = 0; i < OUTLINE_STROKE_WIDTHS.length; i++) {
                g2.setColor(withAlpha(outlineColor, OUTLINE_ALPHAS[i]));
                g2.setStroke(new BasicStroke(
                        OUTLINE_STROKE_WIDTHS[i],
                        BasicStroke.CAP_ROUND,
                        BasicStroke.JOIN_ROUND));
                g2.draw(outline);
            }
            g2.setColor(textColor);
            g2.fill(outline);
            g2.fill(outline);
        } finally {
            g2.setStroke(originalStroke);
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, originalAntialiasing);
        }
    }

    /// Paints the current text block centered on the supplied anchor point.
    ///
    /// When outlining is enabled, screen rendering uses a temporary raster + blur pass to create
    /// a soft halo. Printing paths and unusually large graphics transforms fall back to repeated
    /// vector strokes around the glyph outline instead. Passing `null` for `outlineColor` lets the
    /// renderer choose a black or white halo based on the luminance of `textColor`.
    ///
    /// @param g target graphics, expected to be a `Graphics2D` instance
    /// @param textColor fill color for the glyphs
    /// @param outlineColor halo color, or `null` for an automatically chosen contrasting color
    /// @param centerX x-coordinate of the text block center
    /// @param centerY y-coordinate of the text block center
    public void draw(Graphics g, Color textColor, Color outlineColor, double centerX, double centerY) {
        Graphics2D g2 = (Graphics2D) g;
        ensureLayout();

        if (!outlined) {
            drawText(g2, textColor, centerX, centerY);
            return;
        }

        Color effectiveOutlineColor = (outlineColor != null)
                ? outlineColor
                : (ColorUtil.getLuminance(textColor) < 0.5f ? Color.white : Color.black);

        if (shouldRasterizeOutline(g, g2)) {
            drawRasterizedOutline(g, g2, textColor, effectiveOutlineColor, centerX, centerY);
        } else {
            drawVectorOutline(g2, textColor, effectiveOutlineColor, centerX, centerY);
        }
    }

    /// Returns the current multiline alignment code.
    ///
    /// The value is one of `SwingConstants.CENTER`, `SwingConstants.LEFT`, or
    /// `SwingConstants.RIGHT`.
    public final int getAlignment() {
        return alignment;
    }

    /// Returns the cached local background shape for the prepared text block.
    ///
    /// The unrotated shape is centered on the origin. For multiline text it follows the ragged
    /// left or right edge implied by [#getAlignment()] instead of collapsing to a plain rectangle.
    /// The returned object is renderer-owned and must be treated as read-only.
    ///
    /// @param rotated `true` to apply the current rotation before returning the shape
    /// @return cached background shape, or `null` when there is no visible text
    public final Shape getBackgroundShape(boolean rotated) {
        ensureLayout();
        ensureBackgroundShapes();
        return rotated ? rotatedBackgroundShape : backgroundShape;
    }

    /// Returns the cached axis-aligned bounds of the prepared text block in local coordinates.
    ///
    /// Consumers usually use only the returned width and height and translate them so the text
    /// remains centered around the paint anchor. The rectangle instance is cached and may be
    /// reused across calls.
    ///
    /// @param rotated `true` to return the bounds after applying the current rotation
    /// @return cached local bounds of the current text block
    public final Rectangle2D getBounds(boolean rotated) {
        ensureLayout();
        ensureBounds();
        return rotated ? rotatedBounds : bounds;
    }

    /// Returns the effective font used for layout and drawing.
    ///
    /// A `null` font in the inherited parameter state means "use the current `Label.font` from
    /// [UIManager]".
    public Font getFont() {
        return (super.font != null) ? super.font : UIManager.getFont("Label.font");
    }

    /// Returns the current rotation angle in degrees.
    public double getRotation() {
        return super.rotation;
    }

    /// Returns the source text that will be split on line-feed boundaries during layout.
    public final String getText() {
        return super.text;
    }

    /// Returns the maximum line width used for automatic wrapping.
    ///
    /// Non-positive values disable width-based wrapping even when [#isAutoWrapping()] is enabled.
    public final float getWrappingWidth() {
        return super.wrappingWidth;
    }

    /// Returns whether layout and drawing use antialiased font metrics.
    ///
    /// Toggling this flag can change both appearance and measured bounds because the font render
    /// context changes with it.
    public final boolean isAntiAliased() {
        return super.antiAliased;
    }

    /// Returns whether automatic line breaking is enabled.
    ///
    /// Automatic wrapping only takes effect when [#getWrappingWidth()] is greater than `0`.
    public final boolean isAutoWrapping() {
        return super.autoWrapping;
    }

    /// Returns whether [#draw(Graphics, Color, Color, double, double)] paints a halo around glyphs.
    public final boolean isOutline() {
        return outlined;
    }

    /// Sets the horizontal alignment used for multiline text blocks.
    ///
    /// Single-line text is always centered around the anchor point. For multiline text,
    /// `SwingConstants.LEFT` and `SwingConstants.RIGHT` behave as leading and trailing alignment
    /// based on each [TextLayout]'s text direction.
    ///
    /// @param alignment one of `SwingConstants.CENTER`, `SwingConstants.LEFT`, or
    ///     `SwingConstants.RIGHT`
    /// @throws IllegalArgumentException if `alignment` is not one of the supported constants
    public void setAlignment(int alignment) {
        if (alignment != SwingConstants.LEFT
                && alignment != SwingConstants.CENTER
                && alignment != SwingConstants.RIGHT) {
            throw new IllegalArgumentException("Invalid alignment: " + alignment);
        }
        this.alignment = alignment;
    }

    /// Sets whether layout should use antialiased font metrics.
    ///
    /// Changing this flag invalidates cached line layout because `TextLayout` measurement depends
    /// on the [FontRenderContext].
    ///
    /// @param antiAliased `true` to use antialiased font metrics
    @Override
    public void setAntiAliased(boolean antiAliased) {
        if (super.antiAliased == antiAliased) {
            return;
        }
        super.antiAliased = antiAliased;
        layoutValid = false;
    }

    /// Sets whether automatic line breaking may occur.
    ///
    /// The flag has no effect until [#setWrappingWidth(float)] provides a positive width.
    ///
    /// @param autoWrapping `true` to enable automatic wrapping
    @Override
    public void setAutoWrapping(boolean autoWrapping) {
        if (super.autoWrapping == autoWrapping) {
            return;
        }
        super.autoWrapping = autoWrapping;
        layoutValid = false;
    }

    /// Sets the explicit font used for layout and drawing.
    ///
    /// Passing `null` restores the [UIManager] fallback described by [#getFont()]. Any change
    /// invalidates cached line layout.
    ///
    /// @param font explicit font to use, or `null` to fall back to the current look-and-feel font
    @Override
    public final void setFont(Font font) {
        if (super.font != null && super.font.equals(font)) {
            return;
        }
        super.font = font;
        layoutValid = false;
    }

    /// Enables or disables halo painting around the glyphs.
    ///
    /// This flag affects only drawing; it does not invalidate layout or bounds.
    ///
    /// @param outlined `true` to paint a halo behind the text
    public void setOutline(boolean outlined) {
        this.outlined = outlined;
    }

    /// Sets the text rotation angle in degrees.
    ///
    /// Rotation reuses the current line layout and only invalidates the cached bounds and
    /// background shapes derived from that layout.
    ///
    /// @param rotation rotation angle in degrees
    @Override
    public final void setRotation(double rotation) {
        if (super.rotation == rotation) {
            return;
        }
        super.rotation = rotation;
        boundsValid = false;
        backgroundShapesValid = false;
    }

    /// Sets the source text that will be laid out on the next measurement or paint operation.
    ///
    /// The renderer splits the text on `\n` characters and preserves explicit blank lines as empty
    /// vertical slots in the laid-out block.
    ///
    /// @param text source text; must not be `null`
    /// @throws IllegalArgumentException if `text` is `null`
    @Override
    public void setText(String text) {
        if (text == null) {
            throw new IllegalArgumentException();
        }
        if (super.text.equals(text)) {
            return;
        }
        super.text = text;
        layoutValid = false;
    }

    /// Sets the maximum line width used by the auto-wrapping algorithm.
    ///
    /// Width changes invalidate layout only when automatic wrapping is currently enabled.
    ///
    /// @param wrappingWidth maximum width available to each wrapped line
    @Override
    public void setWrappingWidth(float wrappingWidth) {
        if (super.wrappingWidth == wrappingWidth) {
            return;
        }
        super.wrappingWidth = wrappingWidth;
        if (isAutoWrapping()) {
            layoutValid = false;
        }
    }
}
