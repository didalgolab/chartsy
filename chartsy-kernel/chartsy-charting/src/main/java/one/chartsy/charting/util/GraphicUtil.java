package one.chartsy.charting.util;

import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;

import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import javax.swing.plaf.basic.BasicGraphicsUtils;
import javax.swing.text.View;

import one.chartsy.charting.DoublePoint;

/// Collects low-level Swing painting and geometry helpers shared by the charting module.
///
/// Most methods follow the charting pipeline's scratch-object style: they mutate caller-supplied
/// [Rectangle], [Rectangle2D], [Insets], or [DoublePoint] instances instead of allocating fresh
/// wrappers. That keeps hot rendering paths lean, but it also means this utility is not
/// thread-safe. In particular, [#paintJLabel(Graphics, JLabel, Rectangle)] reuses shared static
/// rectangles and insets and is intended for the usual Swing event-dispatch thread.
public final class GraphicUtil {
    private static final FontRenderContext plainFontRenderContext =
            new FontRenderContext(null, false, false);
    private static final FontRenderContext antialiasedFontRenderContext =
            new FontRenderContext(null, true, false);
    private static final FontRenderContext fractionalMetricsFontRenderContext =
            new FontRenderContext(null, false, true);
    private static final FontRenderContext antialiasedFractionalMetricsFontRenderContext =
            new FontRenderContext(null, true, true);

    private static final Rectangle iconRect = new Rectangle();
    private static final Rectangle textRect = new Rectangle();
    private static final Rectangle viewRect = new Rectangle();
    private static final Insets sharedInsets = new Insets(0, 0, 0, 0);

    private GraphicUtil() {
    }

    /// Expands `target` so it also covers `addition`.
    ///
    /// Empty rectangles are ignored. When `target` is empty, it is replaced with `addition`'s
    /// bounds instead of forming a union around the origin.
    public static void addToRect(Rectangle target, Rectangle addition) {
        if (addition.isEmpty()) {
            return;
        }
        if (target.isEmpty()) {
            target.setBounds(addition.x, addition.y, addition.width, addition.height);
        } else {
            target.add(addition);
        }
    }

    /// Expands `target` so it also covers the point (`x`, `y`).
    ///
    /// When `target` is empty, the method seeds it with a `1x1` rectangle rooted at the supplied
    /// point.
    public static Rectangle2D addToRect(Rectangle2D target, double x, double y) {
        if (target.isEmpty()) {
            target.setRect(x, y, 1.0, 1.0);
        } else {
            target.add(x, y);
        }
        return target;
    }

    /// Expands `target` so it also covers `addition`.
    ///
    /// Empty additions are ignored. When `target` is empty, it is replaced with `addition`.
    public static Rectangle2D addToRect(Rectangle2D target, Rectangle2D addition) {
        if (!addition.isEmpty()) {
            if (target.isEmpty()) {
                target.setRect(addition);
            } else {
                target.add(addition);
            }
        }
        return target;
    }

    /// Applies `insets` to `rectangle` in place.
    ///
    /// Positive inset values move the origin inward and shrink the available width and height.
    public static Rectangle applyInsets(Rectangle rectangle, Insets insets) {
        rectangle.x += insets.left;
        rectangle.y += insets.top;
        rectangle.width -= insets.left + insets.right;
        rectangle.height -= insets.top + insets.bottom;
        return rectangle;
    }

    /// Repositions a text anchor around its current origin for the supplied angle and label size.
    ///
    /// The angle is interpreted in screen coordinates with `0` degrees pointing right, `90`
    /// degrees up, `180` left, and `270` down. Angles within `5` degrees of a cardinal direction
    /// snap to the corresponding side. The supplied point is mutated and returned.
    ///
    /// This helper is used by scale titles, scale annotations, and pie-label placement so those
    /// call sites all follow the same gap and cardinal-snap rules.
    public static DoublePoint computeTextLocation(
            DoublePoint anchor,
            double angleDegrees,
            int gap,
            double textWidth,
            double textHeight
    ) {
        double normalizedAngle = MathUtil.mod360(angleDegrees);

        if (Math.abs(normalizedAngle) < 5.0 || Math.abs(normalizedAngle - 360.0) < 5.0) {
            anchor.x += textWidth / 2.0 + gap;
            return anchor;
        }
        if (Math.abs(normalizedAngle - 90.0) < 5.0) {
            anchor.y -= textHeight / 2.0 + gap;
            return anchor;
        }
        if (Math.abs(normalizedAngle - 180.0) < 5.0) {
            anchor.x -= textWidth / 2.0 + gap;
            return anchor;
        }
        if (Math.abs(normalizedAngle - 270.0) < 5.0) {
            anchor.y += textHeight / 2.0 + gap;
            return anchor;
        }

        double radians = MathUtil.toRadians(normalizedAngle);
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);
        if (normalizedAngle < 90.0 || normalizedAngle >= 270.0) {
            anchor.x += textWidth / 2.0 + cos * gap;
        } else {
            anchor.x -= textWidth / 2.0 - cos * gap;
        }
        anchor.y -= (textHeight / 2.0 + gap) * sin;
        return anchor;
    }

    /// Computes the y value on the line segment through (`x1`, `y1`) and (`x2`, `y2`) at `x`.
    ///
    /// The method also works as linear extrapolation when `x` lies outside the original segment.
    /// For vertical segments it returns `y1`, matching the historical behavior used by renderer
    /// helpers.
    public static double computeYSeg(double x1, double y1, double x2, double y2, double x) {
        if (x1 == x2) {
            return y1;
        }
        double slope = (y2 - y1) / (x2 - x1);
        return slope * (x - x2) + y2;
    }

    /// Converts `count` pairs of doubles into integer coordinate arrays.
    ///
    /// When `suppressDuplicates` is `true`, consecutive points that snap to the same integer pixel
    /// are collapsed and the returned value is the number of retained points. `PlotStyle` uses this
    /// to avoid drawing redundant vertices after device-pixel conversion.
    ///
    /// @param count              the number of entries to read from the source arrays
    /// @param xValues            the source x coordinates
    /// @param yValues            the source y coordinates
    /// @param xInts              the destination x coordinates
    /// @param yInts              the destination y coordinates
    /// @param suppressDuplicates whether to collapse consecutive duplicate integer points
    /// @return the number of valid points written to `xInts` and `yInts`
    public static int doubleToInts(
            int count,
            double[] xValues,
            double[] yValues,
            int[] xInts,
            int[] yInts,
            boolean suppressDuplicates
    ) {
        if (!suppressDuplicates) {
            for (int index = 0; index < count; index++) {
                xInts[index] = toInt(xValues[index]);
                yInts[index] = toInt(yValues[index]);
            }
            return count;
        }

        xInts[0] = toInt(xValues[0]);
        yInts[0] = toInt(yValues[0]);
        int size = 1;
        for (int index = 1; index < count; index++) {
            int x = toInt(xValues[index]);
            int y = toInt(yValues[index]);
            if (x == xInts[size - 1] && y == yInts[size - 1]) {
                continue;
            }
            xInts[size] = x;
            yInts[size] = y;
            size++;
        }
        return size;
    }

    /// Returns a shared [FontRenderContext] for the requested rendering flags.
    ///
    /// The returned instances are immutable cached objects keyed only by antialiasing and
    /// fractional-metrics state.
    public static FontRenderContext getFRC(boolean antiAliased, boolean fractionalMetrics) {
        if (!antiAliased) {
            return fractionalMetrics
                    ? fractionalMetricsFontRenderContext
                    : plainFontRenderContext;
        }
        return fractionalMetrics
                ? antialiasedFractionalMetricsFontRenderContext
                : antialiasedFontRenderContext;
    }

    /// Grows `rectangle` symmetrically by `deltaX` and `deltaY`.
    public static Rectangle2D grow(Rectangle2D rectangle, double deltaX, double deltaY) {
        rectangle.setRect(
                rectangle.getX() - deltaX,
                rectangle.getY() - deltaY,
                rectangle.getWidth() + deltaX * 2.0,
                rectangle.getHeight() + deltaY * 2.0
        );
        return rectangle;
    }

    /// Returns whether all four inset components are zero.
    public static boolean isEmpty(Insets insets) {
        return insets.left == 0
                && insets.right == 0
                && insets.top == 0
                && insets.bottom == 0;
    }

    /// Replaces each side of `target` with the larger of its current value and `addition`.
    ///
    /// This is a side-by-side maximum merge rather than arithmetic addition.
    public static Insets mergeInsets(Insets target, Insets addition) {
        if (target.left < addition.left) {
            target.left = addition.left;
        }
        if (target.right < addition.right) {
            target.right = addition.right;
        }
        if (target.top < addition.top) {
            target.top = addition.top;
        }
        if (target.bottom < addition.bottom) {
            target.bottom = addition.bottom;
        }
        return target;
    }

    /// Paints `label` into `g` as though Swing had laid it out inside `bounds`.
    ///
    /// The helper mirrors Swing's compound-label layout rules, supports plain text and HTML views,
    /// and reproduces the disabled embossed text effect. It does not add the label to a component
    /// hierarchy; callers provide the graphics context and target rectangle directly.
    ///
    /// Because this method reuses shared scratch rectangles and insets, it is intended for the
    /// single-threaded Swing painting model.
    public static void paintJLabel(Graphics g, JLabel label, Rectangle bounds) {
        String text = label.getText();
        Icon icon = label.isEnabled() ? label.getIcon() : label.getDisabledIcon();
        if (icon == null && text == null) {
            return;
        }

        FontMetrics metrics = g.getFontMetrics();
        Insets insets = label.getInsets(sharedInsets);
        viewRect.x = bounds.x + insets.left;
        viewRect.y = bounds.y + insets.top;
        viewRect.width = bounds.width - (insets.left + insets.right);
        viewRect.height = bounds.height - (insets.top + insets.bottom);

        iconRect.setBounds(0, 0, 0, 0);
        textRect.setBounds(0, 0, 0, 0);
        String clippedText = SwingUtilities.layoutCompoundLabel(
                label,
                metrics,
                text,
                icon,
                label.getVerticalAlignment(),
                label.getHorizontalAlignment(),
                label.getVerticalTextPosition(),
                label.getHorizontalTextPosition(),
                viewRect,
                iconRect,
                textRect,
                label.getIconTextGap()
        );

        if (icon != null) {
            icon.paintIcon(label, g, iconRect.x, iconRect.y);
        }
        if (text == null) {
            return;
        }

        View htmlView = (View) label.getClientProperty("html");
        if (htmlView == null) {
            int textX = textRect.x;
            int textY = textRect.y + metrics.getAscent();
            if (label.isEnabled()) {
                paintEnabledLabelText(label, g, clippedText, textX, textY);
            } else {
                paintDisabledLabelText(label, g, clippedText, textX, textY);
            }
            return;
        }

        boolean installClip = g.getClipBounds() == null;
        try {
            if (installClip) {
                g.setClip(bounds);
            }
            htmlView.paint(g, textRect);
        } finally {
            if (installClip) {
                g.setClip(null);
            }
        }
    }

    /// Returns the angle from (`x1`, `y1`) to (`x2`, `y2`) in screen coordinates.
    ///
    /// The result is normalized to `[0, 360)` where `0` points right, `90` up, `180` left, and
    /// `270` down.
    public static double pointAngleDeg(double x1, double y1, double x2, double y2) {
        if (x1 == x2) {
            return (y1 >= y2) ? 90.0 : 270.0;
        }
        if (y1 == y2) {
            return (x1 >= x2) ? 180.0 : 0.0;
        }

        double angle = Math.toDegrees(Math.atan2(y1 - y2, x2 - x1));
        if (angle < 0.0) {
            angle += 360.0;
        }
        return angle;
    }

    /// Enables general antialiasing when it is currently explicitly off.
    ///
    /// @return `true` if the caller's graphics object already had antialiasing enabled or
    ///         configured to a non-`OFF` value; `false` if this method changed the hint and the
    ///         caller should later pair it with [#stopAntiAliasing(Graphics)]
    public static boolean startAntiAliasing(Graphics g) {
        Graphics2D g2 = (Graphics2D) g;
        Object hint = g2.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        if (hint != RenderingHints.VALUE_ANTIALIAS_OFF) {
            return true;
        }

        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(
                RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_OFF
        );
        return false;
    }

    /// Enables text antialiasing when it is currently explicitly off.
    ///
    /// @return `true` if text antialiasing was already enabled or configured to a non-`OFF` value;
    ///         `false` if this method changed the hint and the caller should later pair it with
    ///         [#stopTextAntiAliasing(Graphics)]
    public static boolean startTextAntiAliasing(Graphics g) {
        Graphics2D g2 = (Graphics2D) g;
        Object hint = g2.getRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING);
        if (hint != RenderingHints.VALUE_TEXT_ANTIALIAS_OFF) {
            return true;
        }

        g2.setRenderingHint(
                RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON
        );
        return false;
    }

    /// Forces general antialiasing off.
    ///
    /// This method restores the historical charting default rather than the exact previous hint
    /// value, so it should be paired only with [#startAntiAliasing(Graphics)] when that method
    /// returned `false`.
    public static void stopAntiAliasing(Graphics g) {
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
    }

    /// Forces text antialiasing off.
    ///
    /// This method restores the historical charting default rather than the exact previous hint
    /// value, so it should be paired only with [#startTextAntiAliasing(Graphics)] when that method
    /// returned `false`.
    public static void stopTextAntiAliasing(Graphics g) {
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(
                RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_OFF
        );
    }

    /// Converts a device-space `double` coordinate to the charting integer pixel convention.
    ///
    /// Positive values are truncated after adding a small epsilon and negative values are floored
    /// with the same epsilon. This matches the historical pixel snapping used throughout
    /// `PlotStyle`, `Scale`, and related rendering helpers.
    public static int toInt(double value) {
        double adjusted = (value < 0.0)
                ? Math.floor(value + 1.0E-6)
                : (int) (value + 1.0E-6);
        return (int) adjusted;
    }

    /// Converts `rectangle` to an integer bounding box.
    ///
    /// The method reuses `target` when supplied, clears it when `rectangle` is empty, and returns
    /// `rectangle` directly when it is already a [Rectangle]. Non-empty floating bounds are
    /// converted with `floor` for the minimum corner and `ceil` for the maximum corner so the
    /// result fully contains the original geometry.
    public static Rectangle toRectangle(Rectangle2D rectangle, Rectangle target) {
        if (rectangle instanceof Rectangle integerRectangle) {
            return integerRectangle;
        }
        if (target == null) {
            return rectangle.getBounds();
        }
        if (rectangle.isEmpty()) {
            target.setBounds(0, 0, 0, 0);
            return target;
        }

        double minX = Math.floor(rectangle.getX());
        double minY = Math.floor(rectangle.getY());
        double maxX = Math.ceil(rectangle.getX() + rectangle.getWidth());
        double maxY = Math.ceil(rectangle.getY() + rectangle.getHeight());
        target.setBounds((int) minX, (int) minY, (int) (maxX - minX), (int) (maxY - minY));
        return target;
    }

    /// Replaces `rectangle` with the bounding box of its affine transform.
    ///
    /// The method transforms all four rectangle corners and then expands the original
    /// [Rectangle2D] to the minimal axis-aligned box that contains the transformed polygon.
    public static Rectangle2D transform(Rectangle2D rectangle, AffineTransform transform) {
        double[] corners = new double[]{
                rectangle.getX(), rectangle.getY(),
                rectangle.getX() + rectangle.getWidth(), rectangle.getY(),
                rectangle.getX() + rectangle.getWidth(), rectangle.getY() + rectangle.getHeight(),
                rectangle.getX(), rectangle.getY() + rectangle.getHeight()
        };
        transform.transform(corners, 0, corners, 0, 4);

        rectangle.setRect(0.0, 0.0, 0.0, 0.0);
        addToRect(rectangle, corners[0], corners[1]);
        addToRect(rectangle, corners[2], corners[3]);
        addToRect(rectangle, corners[4], corners[5]);
        addToRect(rectangle, corners[6], corners[7]);
        return rectangle;
    }

    private static void paintEnabledLabelText(
            JLabel label,
            Graphics g,
            String text,
            int textX,
            int textY
    ) {
        g.setColor(label.getForeground());
        BasicGraphicsUtils.drawStringUnderlineCharAt(
                g,
                text,
                label.getDisplayedMnemonicIndex(),
                textX,
                textY
        );
    }

    private static void paintDisabledLabelText(
            JLabel label,
            Graphics g,
            String text,
            int textX,
            int textY
    ) {
        Color background = label.getBackground();
        int mnemonicIndex = label.getDisplayedMnemonicIndex();

        g.setColor(background.brighter());
        BasicGraphicsUtils.drawStringUnderlineCharAt(g, text, mnemonicIndex, textX + 1, textY + 1);
        g.setColor(background.darker());
        BasicGraphicsUtils.drawStringUnderlineCharAt(g, text, mnemonicIndex, textX, textY);
    }
}
