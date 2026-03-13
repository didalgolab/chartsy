package one.chartsy.charting;

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Insets;
import java.io.Serializable;

import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;

/// Border that draws the separator line between a docked [Legend] and the chart area beside it.
///
/// When the target component is a docked [Legend], this border paints only the edge that faces the
/// chart: bottom for north docks, top for south docks, right for west docks, and left for east
/// docks. This keeps the legend visually attached to the chart without outlining the legend's
/// outer edges.
///
/// For floating legends and non-[Legend] components, the current implementation falls back to a
/// cached [LineBorder] or [CompoundBorder] built from the same `thickness`, `color`, and
/// `insideMargin`. In that mode `outsideMargin` is not applied.
///
/// The separator color defaults to the target component's foreground and then to
/// `UIManager.getColor("Label.foreground")` when no explicit color was configured.
public class LegendSeparator implements Border, Serializable {
    private static final String LEGEND_DRAG_POSITION_CLIENT_PROPERTY = "_DragPosition_Key__";

    private enum SeparatorSide {
        NONE,
        TOP,
        LEFT,
        BOTTOM,
        RIGHT
    }

    private final int thickness;
    private final Color color;
    private final int outsideMargin;
    private final int insideMargin;
    private Border cachedBorder;
    
    /// Creates a one-pixel separator that inherits its color from the target component and adds no
    /// extra spacing.
    public LegendSeparator() {
        this(null, 1, 0, 0);
    }
    
    /// Creates a separator with an explicit line color and thickness, but no extra margins.
    ///
    /// @param color explicit separator color, or `null` to inherit from the target component
    /// @param thickness separator thickness in pixels
    public LegendSeparator(Color color, int thickness) {
        this(color, thickness, 0, 0);
    }
    
    /// Creates a separator with explicit styling and spacing rules.
    ///
    /// `insideMargin` reserves space between the painted rule and the legend contents.
    /// `outsideMargin` reserves space between the rule and the chart-facing outer edge when the
    /// separator is attached to a docked legend.
    ///
    /// @param color explicit separator color, or `null` to inherit from the target component
    /// @param thickness separator thickness in pixels
    /// @param insideMargin space between the separator line and the legend contents
    /// @param outsideMargin space between the separator line and the chart-facing outer edge of a
    ///     docked legend
    public LegendSeparator(Color color, int thickness, int insideMargin, int outsideMargin) {
        this.color = color;
        this.thickness = thickness;
        this.insideMargin = insideMargin;
        this.outsideMargin = outsideMargin;
    }
    
    /// Creates a separator that inherits its color from the target component.
    ///
    /// @param thickness separator thickness in pixels
    public LegendSeparator(int thickness) {
        this(null, thickness, 0, 0);
    }
    
    /// Creates a separator that inherits its color from the target component and applies the
    /// provided spacing rules.
    ///
    /// @param thickness separator thickness in pixels
    /// @param insideMargin space between the separator line and the legend contents
    /// @param outsideMargin space between the separator line and the chart-facing outer edge of a
    ///     docked legend
    public LegendSeparator(int thickness, int insideMargin, int outsideMargin) {
        this(null, thickness, insideMargin, outsideMargin);
    }
    
    private synchronized Border resolveBorder(Component c) {
        var resolvedColor = resolveColor(c);
        var lineBorder = extractLineBorder(cachedBorder);
        if (lineBorder == null || !lineBorder.getLineColor().equals(resolvedColor)) {
            var updatedLineBorder = new LineBorder(resolvedColor, thickness);
            if (insideMargin == 0) {
                cachedBorder = updatedLineBorder;
            } else {
                var insideBorder = (cachedBorder instanceof CompoundBorder compoundBorder)
                        ? compoundBorder.getInsideBorder()
                        : new EmptyBorder(insideMargin, insideMargin, insideMargin, insideMargin);
                cachedBorder = new CompoundBorder(updatedLineBorder, insideBorder);
            }
        }
        return cachedBorder;
    }
    
    private static LineBorder extractLineBorder(Border border) {
        if (border instanceof LineBorder lineBorder)
            return lineBorder;
        if (border instanceof CompoundBorder compoundBorder)
            return (LineBorder) compoundBorder.getOutsideBorder();
        return null;
    }

    private Color resolveColor(Component c) {
        if (color != null)
            return color;
        if (c.getForeground() != null)
            return c.getForeground();
        return UIManager.getColor("Label.foreground");
    }

    private SeparatorSide resolveSeparatorSide(Component c) {
        if (!(c instanceof Legend legend))
            return SeparatorSide.NONE;

        var position = (String) legend.getClientProperty(LEGEND_DRAG_POSITION_CLIENT_PROPERTY);
        if (position == null)
            position = legend.getPosition();
        if (position == null || ChartLayout.ABSOLUTE.equals(position))
            return SeparatorSide.NONE;

        return switch (position) {
            case ChartLayout.NORTH_TOP, ChartLayout.NORTH_BOTTOM -> SeparatorSide.BOTTOM;
            case ChartLayout.SOUTH_TOP, ChartLayout.SOUTH_BOTTOM -> SeparatorSide.TOP;
            case ChartLayout.EAST -> SeparatorSide.LEFT;
            case ChartLayout.WEST -> SeparatorSide.RIGHT;
            default -> SeparatorSide.NONE;
        };
    }

    private int getSeparatorExtent() {
        return thickness + insideMargin + outsideMargin;
    }

    private void paintHorizontalLine(Graphics g, int x, int y, int width) {
        for (int offset = 0; offset < thickness; offset++)
            g.drawLine(x, y + offset, x + width - 1, y + offset);
    }

    private void paintVerticalLine(Graphics g, int x, int y, int height) {
        for (int offset = 0; offset < thickness; offset++)
            g.drawLine(x + offset, y, x + offset, y + height - 1);
    }
    
    /// Returns the space this separator reserves around `c`.
    ///
    /// Docked legends reserve space only on the side that faces the chart. Floating legends and
    /// non-[Legend] components use the delegate border described in the class-level documentation.
    @Override
    public Insets getBorderInsets(Component c) {
        var extent = getSeparatorExtent();
        return switch (resolveSeparatorSide(c)) {
            case TOP -> new Insets(extent, 0, 0, 0);
            case LEFT -> new Insets(0, extent, 0, 0);
            case BOTTOM -> new Insets(0, 0, extent, 0);
            case RIGHT -> new Insets(0, 0, 0, extent);
            case NONE -> resolveBorder(c).getBorderInsets(c);
        };
    }
    
    /// Returns the explicit separator color.
    ///
    /// A `null` result means the border inherits its paint color from the target component.
    ///
    /// @return the configured separator color, or `null` to inherit from the target component
    public Color getColor() {
        return color;
    }
    
    /// Returns the padding between the separator line and the legend contents.
    ///
    /// @return the inner spacing between the painted rule and legend contents
    public int getInsideMargin() {
        return insideMargin;
    }
    
    /// Returns the spacing between the separator line and the chart-facing outer edge.
    ///
    /// ### API Note
    ///
    /// This value affects only docked legends. Floating legends and non-[Legend] components
    /// currently paint through the delegate-border path, which ignores `outsideMargin`.
    ///
    /// @return the outer spacing between the separator line and the chart-facing edge
    public int getOutsideMargin() {
        return outsideMargin;
    }
    
    /// Returns the separator thickness in pixels.
    ///
    /// @return the separator line thickness in pixels
    public int getThickness() {
        return thickness;
    }
    
    /// Returns whether this border paints every pixel in the area it reserves.
    ///
    /// Any inside or outside margin leaves part of that reserved area transparent, so only a
    /// margin-free separator reports `true`.
    @Override
    public boolean isBorderOpaque() {
        return insideMargin == 0 && outsideMargin == 0;
    }
    
    /// Paints the chart-facing separator edge for docked legends.
    ///
    /// Components that do not currently resolve to a docked legend position are painted through the
    /// delegate border path instead.
    @Override
    public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
        var separatorSide = resolveSeparatorSide(c);
        if (separatorSide == SeparatorSide.NONE) {
            resolveBorder(c).paintBorder(c, g, x, y, width, height);
            return;
        }

        var previousColor = g.getColor();
        g.setColor(resolveColor(c));
        switch (separatorSide) {
            case TOP -> paintHorizontalLine(g, x, y + outsideMargin, width);
            case LEFT -> paintVerticalLine(g, x + outsideMargin, y, height);
            case BOTTOM -> paintHorizontalLine(g, x, y + height - outsideMargin - thickness, width);
            case RIGHT -> paintVerticalLine(g, x + width - outsideMargin - thickness, y, height);
            case NONE -> throw new AssertionError(separatorSide);
        }
        g.setColor(previousColor);
    }
}
