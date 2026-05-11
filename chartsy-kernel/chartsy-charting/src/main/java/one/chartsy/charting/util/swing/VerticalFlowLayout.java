package one.chartsy.charting.util.swing;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Insets;

/// `FlowLayout` variant that stacks visible components vertically and wraps them into additional
/// columns when the current container height is exhausted.
///
/// Components are always sized to their preferred size before placement. The inherited
/// `FlowLayout` alignment still controls left/center/right positioning within each column, while
/// this class's `TOP`, `MIDDLE`, and `BOTTOM` constants control how leftover vertical space is
/// distributed inside each column.
///
/// Unlike a typical preferred-size calculation, [#preferredLayoutSize(Container)] also consults
/// the container's current height. Once that height becomes positive, the reported width includes
/// any wrapped columns needed to fit within it; otherwise the method falls back to a single-column
/// estimate.
///
/// `LegendLayout` uses this layout for east and west legend docks, where the current container
/// height determines how many wrapped columns are needed.
public class VerticalFlowLayout extends FlowLayout {
    /// Aligns each column's contents to the top.
    public static final int TOP = 0;
    /// Centers each column's contents vertically.
    public static final int MIDDLE = 1;
    /// Aligns each column's contents to the bottom.
    public static final int BOTTOM = 2;
    private final int verticalAlignment;
    private final int horizontalGap;
    private final int verticalGap;
    private final boolean reverseOrder;

    /// Creates a middle-aligned layout with `5`-pixel gaps and normal component order.
    public VerticalFlowLayout() {
        this(MIDDLE, 5, 5);
    }

    /// Creates a layout with the given vertical alignment, `5`-pixel gaps, and normal component
    /// order.
    public VerticalFlowLayout(int verticalAlignment) {
        this(verticalAlignment, 5, 5);
    }

    /// Creates a layout with explicit alignment and gaps.
    public VerticalFlowLayout(int verticalAlignment, int horizontalGap, int verticalGap) {
        this(verticalAlignment, horizontalGap, verticalGap, false);
    }

    /// Creates a layout with full control over wrapping and iteration order.
    ///
    /// @param verticalAlignment one of [#TOP], [#MIDDLE], or [#BOTTOM]
    /// @param horizontalGap     horizontal gap inserted between wrapped columns
    /// @param verticalGap       vertical gap inserted between components and at the top/bottom padding
    /// @param reverseOrder      whether visible components are traversed from the last child to the
    ///                              first child
    public VerticalFlowLayout(int verticalAlignment, int horizontalGap, int verticalGap, boolean reverseOrder) {
        this.verticalAlignment = verticalAlignment;
        this.horizontalGap = horizontalGap;
        this.verticalGap = verticalGap;
        this.reverseOrder = reverseOrder;
        super.setAlignment(FlowLayout.LEFT);
    }

    /// Resolves the physical child index for one logical layout slot.
    private int getComponentIndex(int logicalIndex, int componentCount) {
        return !reverseOrder ? logicalIndex : componentCount - 1 - logicalIndex;
    }

    /// Positions one already-measured column of visible components.
    private void layoutColumn(Container target, int componentCount, int columnX, int columnY, int columnWidth,
                              int extraVerticalSpace, int fromIndex, int toIndex) {
        int y = columnY;
        if (verticalAlignment == MIDDLE)
            y = y + extraVerticalSpace / 2;
        else if (verticalAlignment == BOTTOM)
            y = y + extraVerticalSpace;
        for (int index = fromIndex; index < toIndex; index++) {
            int componentIndex = getComponentIndex(index, componentCount);
            Component component = target.getComponent(componentIndex);
            Dimension size = component.getSize();
            if (component.isVisible()) {
                int xOffset = 0;
                if (super.getAlignment() == FlowLayout.CENTER)
                    xOffset = (columnWidth - size.width) / 2;
                else if (super.getAlignment() == FlowLayout.RIGHT)
                    xOffset = columnWidth - size.width;
                component.setLocation(columnX + xOffset, y);
                y = y + (verticalGap + size.height);
            }
        }
    }

    /// Lays out visible children into one or more vertical columns based on the container's
    /// current height.
    @Override
    public void layoutContainer(Container target) {
        Insets insets = target.getInsets();
        int availableHeight = target.getHeight() - 2 * verticalGap;
        int occupiedHeight = insets.top;
        int usedWidth = 0;
        int columnWidth = 0;
        int columnStartIndex = 0;
        int componentCount = target.getComponentCount();

        // Keep the historical logical-index-based wrapping so live layout matches the server-side
        // legend layout variant that mirrors this class.
        for (int logicalIndex = 0; logicalIndex < componentCount; logicalIndex++) {
            Component component = target.getComponent(getComponentIndex(logicalIndex, componentCount));
            if (!component.isVisible())
                continue;

            Dimension preferredSize = component.getPreferredSize();
            component.setSize(preferredSize.width, preferredSize.height);
            if (logicalIndex == 0) {
                occupiedHeight += preferredSize.height;
                columnWidth = Math.max(columnWidth, preferredSize.width);
            } else if (occupiedHeight + preferredSize.height + insets.bottom <= availableHeight) {
                occupiedHeight += verticalGap + preferredSize.height;
                columnWidth = Math.max(columnWidth, preferredSize.width);
            } else {
                layoutColumn(target, componentCount, usedWidth + insets.left, insets.top + verticalGap,
                        columnWidth, availableHeight - occupiedHeight - insets.bottom, columnStartIndex,
                        logicalIndex);
                columnStartIndex = logicalIndex;
                occupiedHeight = preferredSize.height + insets.top;
                usedWidth += columnWidth + horizontalGap;
                columnWidth = preferredSize.width;
            }
        }

        layoutColumn(target, componentCount, usedWidth + insets.left, insets.top + verticalGap, columnWidth,
                availableHeight - occupiedHeight - insets.bottom, columnStartIndex, componentCount);
    }

    /// Returns [#preferredLayoutSize(Container)].
    @Override
    public Dimension minimumLayoutSize(Container target) {
        return preferredLayoutSize(target);
    }

    /// Returns the size needed for the current container height.
    ///
    /// If the container height is already positive, the returned width includes any wrapped
    /// columns and the returned height is clamped to that current available height. Otherwise the
    /// result describes a single vertically stacked column including gaps and insets.
    @Override
    public Dimension preferredLayoutSize(Container target) {
        Insets insets = target.getInsets();
        int availableHeight = target.getHeight() - 2 * verticalGap;
        int componentCount = target.getComponentCount();

        if (availableHeight <= 0) {
            Dimension singleColumnSize = new Dimension(0, 0);
            for (int logicalIndex = 0; logicalIndex < componentCount; logicalIndex++) {
                Component component = target.getComponent(getComponentIndex(logicalIndex, componentCount));
                if (!component.isVisible())
                    continue;

                Dimension preferredSize = component.getPreferredSize();
                singleColumnSize.width = Math.max(singleColumnSize.width, preferredSize.width);
                if (logicalIndex > 1)
                    singleColumnSize.height += verticalGap;
                singleColumnSize.height += preferredSize.height;
            }
            singleColumnSize.width += insets.left + insets.right + horizontalGap * 2;
            singleColumnSize.height += insets.top + insets.bottom + verticalGap * 2;
            return singleColumnSize;
        }

        int occupiedHeight = insets.top;
        int totalWidth = 0;
        int columnWidth = 0;
        for (int logicalIndex = 0; logicalIndex < componentCount; logicalIndex++) {
            Component component = target.getComponent(getComponentIndex(logicalIndex, componentCount));
            if (!component.isVisible())
                continue;

            Dimension preferredSize = component.getPreferredSize();
            component.setSize(preferredSize.width, preferredSize.height);
            if (logicalIndex == 0) {
                occupiedHeight += preferredSize.height;
                columnWidth = Math.max(columnWidth, preferredSize.width);
            } else if (occupiedHeight + preferredSize.height + insets.bottom <= availableHeight) {
                occupiedHeight += verticalGap + preferredSize.height;
                columnWidth = Math.max(columnWidth, preferredSize.width);
            } else {
                occupiedHeight = preferredSize.height + insets.top;
                totalWidth += columnWidth + horizontalGap;
                columnWidth = preferredSize.width;
            }
        }

        return new Dimension(totalWidth + columnWidth + insets.left + insets.right, availableHeight);
    }
}
