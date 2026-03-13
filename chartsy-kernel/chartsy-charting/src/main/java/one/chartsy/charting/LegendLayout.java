package one.chartsy.charting;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.Rectangle;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import javax.swing.JComponent;
import javax.swing.SwingConstants;

import one.chartsy.charting.util.swing.VerticalFlowLayout;

/// Chooses the concrete layout strategy that a [Legend] should use for its current docking mode.
///
/// The legend itself exposes a single layout manager, but its geometry requirements differ
/// substantially between:
/// - the default detached case, which renders rows in one column,
/// - north and south chart docks, which wrap rows into multiple columns based on the chart width,
/// - east and west docks, which wrap rows vertically based on the chart height,
/// - floating legends, which may stack vertically or horizontally, and
/// - server-side export, where bounds must be predicted without mutating live Swing components.
///
/// This class keeps a cached delegate for each of those scenarios and selects the appropriate one
/// from the legend's effective docking position. During drag preview it prefers the temporary
/// position published by [Legend]'s docking interactor, allowing the same instance to serve both
/// live layout and in-progress docking feedback.
///
/// Configuration state such as gaps, orientation, and floating direction is stored here rather
/// than on the nested delegates so it can invalidate and recreate only the affected cached
/// layouts. The class is Swing-oriented and not independently thread-safe.
public class LegendLayout implements Serializable, ServerSideLayout {

    /// Mirrors [Legend]'s temporary drag-preview client property.
    private static final String DRAG_POSITION_CLIENT_PROPERTY = "_DragPosition_Key__";

    /// Preferred-size lookup used when wrapping north/south legend rows.
    private static final ComponentSizeRequest PREFERRED_SIZE = Component::getPreferredSize;

    /// Minimum-size lookup used when computing the minimum size of wrapped north/south legends.
    private static final ComponentSizeRequest MINIMUM_SIZE = Component::getMinimumSize;

    /// Functional size probe used by the north/south wrapping logic.
    ///
    /// Both live and server-side horizontal layouts determine how many columns fit by measuring the
    /// widest row. This abstraction lets the same calculation work with preferred or minimum size
    /// semantics without duplicating the surrounding algorithm.
    @FunctionalInterface
    private interface ComponentSizeRequest extends Serializable {

        /// Returns the size that the caller should treat as the row's effective cell size.
        ///
        /// @param component the legend child being measured
        /// @return the size metric to use for wrapping or size computation
        Dimension getSize(Component component);
    }

    /// `GridLayout` variant that can traverse legend children in reverse order.
    ///
    /// The stock [java.awt.GridLayout] always assigns cells in child order. Legends need the
    /// option to anchor items to the trailing edge or bottom edge while keeping their component
    /// list stable, so this subclass flips the logical component index mapping without changing the
    /// underlying container order.
    private static class GridLayout extends java.awt.GridLayout {
        /// Whether logical layout order should start from the last child.
        protected final boolean reverseOrder;

        /// Creates the grid with explicit cell dimensions and iteration order.
        ///
        /// @param rows the requested row count, or `0` for dynamic rows
        /// @param columns the requested column count, or `0` for dynamic columns
        /// @param horizontalGap the horizontal gap between cells
        /// @param verticalGap the vertical gap between cells
        /// @param reverseOrder whether the last child should occupy the first logical cell
        public GridLayout(int rows, int columns, int horizontalGap, int verticalGap, boolean reverseOrder) {
            super(rows, columns, horizontalGap, verticalGap);
            this.reverseOrder = reverseOrder;
        }

        /// Resolves the physical component index for one logical grid cell.
        private int getComponentIndex(int cellIndex, int componentCount) {
            return reverseOrder ? componentCount - 1 - cellIndex : cellIndex;
        }

        /// Lays out children exactly like `GridLayout`, with optional reversed child traversal.
        @Override
        public void layoutContainer(Container parent) {
            synchronized (parent.getTreeLock()) {
                int componentCount = parent.getComponentCount();
                if (componentCount == 0)
                    return;

                Insets insets = parent.getInsets();
                int rows = getRows();
                int columns = getColumns();
                if (rows <= 0)
                    rows = (componentCount + columns - 1) / columns;
                else
                    columns = (componentCount + rows - 1) / rows;

                int cellWidth = parent.getWidth() - (insets.left + insets.right);
                int cellHeight = parent.getHeight() - (insets.top + insets.bottom);
                cellWidth = (cellWidth - (columns - 1) * getHgap()) / columns;
                cellHeight = (cellHeight - (rows - 1) * getVgap()) / rows;

                boolean leftToRight = parent.getComponentOrientation().isLeftToRight();
                int x = leftToRight ? insets.left : parent.getWidth() - insets.right - cellWidth;
                for (int column = 0; column < columns; column++) {
                    int y = insets.top;
                    for (int row = 0; row < rows; row++) {
                        int cellIndex = row * columns + column;
                        if (cellIndex < componentCount) {
                            int componentIndex = getComponentIndex(cellIndex, componentCount);
                            parent.getComponent(componentIndex).setBounds(x, y, cellWidth, cellHeight);
                        }
                        y += cellHeight + getVgap();
                    }
                    x += leftToRight ? cellWidth + getHgap() : -cellWidth - getHgap();
                }
            }
        }
    }

    /// Live layout used for legends docked on the north or south edge of a chart.
    ///
    /// Horizontal docks wrap legend rows into as many columns as fit the current chart width. The
    /// result is still expressed as a grid so entries line up cleanly, but the number of columns is
    /// recalculated before every size query and layout pass.
    private final class HorizontalLayout extends GridLayout {

        /// Creates the wrapping layout for one horizontal dock.
        ///
        /// @param horizontalGap the horizontal gap between legend cells
        /// @param verticalGap the vertical gap between legend rows
        /// @param reverseOrder whether rows should fill from the trailing edge
        public HorizontalLayout(int horizontalGap, int verticalGap, boolean reverseOrder) {
            super(0, 1, horizontalGap, verticalGap, reverseOrder);
        }

        /// Recomputes how many columns fit the currently available chart width.
        private void configureColumns(Container parent, ComponentSizeRequest sizeRequest) {
            Insets insets = parent.getInsets();
            Dimension availableSize = resolveChartSize();
            int availableWidth = availableSize.width - (insets.left + insets.right);
            int componentCount = parent.getComponentCount();
            if (componentCount == 0) {
                setRows(0);
                setColumns(1);
                return;
            }

            int widestCell = 1;
            for (int logicalIndex = 0; logicalIndex < componentCount; logicalIndex++) {
                int componentIndex = reverseOrder ? componentCount - 1 - logicalIndex : logicalIndex;
                int cellWidth = sizeRequest.getSize(parent.getComponent(componentIndex)).width + getHgap();
                widestCell = Math.max(widestCell, cellWidth);
            }

            int columns = Math.max(1, Math.min(availableWidth / widestCell, componentCount));
            setRows(0);
            setColumns(columns);
        }

        /// Adapts the column count to the current chart width before laying out the legend.
        @Override
        public void layoutContainer(Container parent) {
            synchronized (parent.getTreeLock()) {
                configureColumns(parent, PREFERRED_SIZE);
                super.layoutContainer(parent);
            }
        }

        /// Uses minimum child sizes when estimating the smallest viable wrapped layout.
        @Override
        public Dimension minimumLayoutSize(Container parent) {
            synchronized (parent.getTreeLock()) {
                configureColumns(parent, MINIMUM_SIZE);
                return super.minimumLayoutSize(parent);
            }
        }

        /// Uses preferred child sizes when estimating the wrapped legend size.
        @Override
        public Dimension preferredLayoutSize(Container parent) {
            synchronized (parent.getTreeLock()) {
                configureColumns(parent, PREFERRED_SIZE);
                return super.preferredLayoutSize(parent);
            }
        }
    }

    /// Server-side counterpart of [GridLayout] that predicts child bounds instead of mutating them.
    ///
    /// Export code asks this layout for rectangles when the legend is rendered into an image. The
    /// algorithm mirrors the live grid cell assignment closely so image export keeps the same row
    /// and column structure as interactive painting.
    private static class ServerSideGridLayout extends GridLayout implements ServerSideLayout {

        /// Creates the server-side grid with explicit cell dimensions and traversal order.
        ///
        /// @param rows the requested row count, or `0` for dynamic rows
        /// @param columns the requested column count, or `0` for dynamic columns
        /// @param horizontalGap the horizontal gap between cells
        /// @param verticalGap the vertical gap between cells
        /// @param reverseOrder whether the last child should occupy the first logical cell
        public ServerSideGridLayout(int rows, int columns, int horizontalGap, int verticalGap, boolean reverseOrder) {
            super(rows, columns, horizontalGap, verticalGap, reverseOrder);
        }

        /// Computes the bounds that the live grid layout would assign for the given rectangle.
        @Override
        public Map<Component, Rectangle> computeBounds(Container parent, Rectangle bounds) {
            Map<Component, Rectangle> componentBounds = new HashMap<>();
            Insets insets = parent.getInsets();
            int componentCount = parent.getComponentCount();
            if (componentCount == 0)
                return componentBounds;

            int rows = getRows();
            int columns = getColumns();
            if (rows <= 0)
                rows = (componentCount + columns - 1) / columns;
            else
                columns = (componentCount + rows - 1) / rows;

            int cellWidth = bounds.width - (insets.left + insets.right);
            int cellHeight = bounds.height - (insets.top + insets.bottom);
            cellWidth = (cellWidth - (columns - 1) * getHgap()) / columns;
            cellHeight = (cellHeight - (rows - 1) * getVgap()) / rows;

            boolean leftToRight = parent.getComponentOrientation().isLeftToRight();
            int x = leftToRight ? insets.left : bounds.width - insets.right - cellWidth;
            for (int column = 0; column < columns; column++) {
                int y = insets.top;
                for (int row = 0; row < rows; row++) {
                    int cellIndex = row * columns + column;
                    if (cellIndex < componentCount) {
                        int componentIndex = reverseOrder ? componentCount - 1 - cellIndex : cellIndex;
                        componentBounds.put(parent.getComponent(componentIndex),
                                new Rectangle(x, y, cellWidth, cellHeight));
                    }
                    y += cellHeight + getVgap();
                }
                x += leftToRight ? cellWidth + getHgap() : -cellWidth - getHgap();
            }

            return componentBounds;
        }
    }

    /// Server-side counterpart of [HorizontalLayout].
    ///
    /// Export code still needs the same width-dependent column selection as the live north/south
    /// layout, but it must base that decision on the off-screen chart or legend bounds stored under
    /// `ServerSideLayout.BOUNDS_PROPERTY` instead of on stale component geometry.
    private final class ServerSideHorizontalLayout extends ServerSideGridLayout {

        /// Creates the server-side wrapping layout for one horizontal dock.
        ///
        /// @param horizontalGap the horizontal gap between legend cells
        /// @param verticalGap the vertical gap between legend rows
        /// @param reverseOrder whether rows should fill from the trailing edge
        public ServerSideHorizontalLayout(int horizontalGap, int verticalGap, boolean reverseOrder) {
            super(1, 1, horizontalGap, verticalGap, reverseOrder);
        }

        /// Recomputes the wrapping column count from the currently available server-side bounds.
        private void configureColumns(Container parent, ComponentSizeRequest sizeRequest, Rectangle bounds) {
            Rectangle availableBounds = resolveAvailableBounds(parent, bounds);
            Insets insets = parent.getInsets();
            int availableWidth = availableBounds.width - (insets.left + insets.right);
            int componentCount = parent.getComponentCount();
            if (componentCount == 0) {
                setRows(0);
                setColumns(1);
                return;
            }

            int widestCell = 1;
            for (int logicalIndex = 0; logicalIndex < componentCount; logicalIndex++) {
                int componentIndex = reverseOrder ? componentCount - 1 - logicalIndex : logicalIndex;
                int cellWidth = sizeRequest.getSize(parent.getComponent(componentIndex)).width + getHgap();
                widestCell = Math.max(widestCell, cellWidth);
            }

            int columns = Math.max(1, Math.min(availableWidth / widestCell, componentCount));
            setRows(0);
            setColumns(columns);
        }

        /// Resolves the bounds rectangle that governs the current export layout pass.
        private Rectangle resolveAvailableBounds(Container parent, Rectangle bounds) {
            if (bounds != null)
                return bounds;

            Legend legend = getLegend();
            Chart chart = legend.getChart();
            if (chart != null && legend.getParent() == chart) {
                Object chartBounds = chart.getClientProperty(ServerSideLayout.BOUNDS_PROPERTY);
                if (chartBounds instanceof Rectangle rectangle)
                    return rectangle;
            }

            if (parent instanceof JComponent component) {
                Object parentBounds = component.getClientProperty(ServerSideLayout.BOUNDS_PROPERTY);
                if (parentBounds instanceof Rectangle rectangle)
                    return rectangle;
            }

            return parent.getBounds();
        }

        /// Computes server-side bounds after adapting the grid width to the export rectangle.
        @Override
        public Map<Component, Rectangle> computeBounds(Container parent, Rectangle bounds) {
            configureColumns(parent, PREFERRED_SIZE, bounds);
            return super.computeBounds(parent, bounds);
        }

        /// Does nothing because server-side layouts never move live components.
        @Override
        public void layoutContainer(Container parent) {
        }

        /// Uses minimum child sizes when estimating the smallest wrapped export layout.
        @Override
        public Dimension minimumLayoutSize(Container parent) {
            synchronized (parent.getTreeLock()) {
                configureColumns(parent, MINIMUM_SIZE, null);
                return super.minimumLayoutSize(parent);
            }
        }

        /// Uses preferred child sizes when estimating the wrapped export layout.
        @Override
        public Dimension preferredLayoutSize(Container parent) {
            synchronized (parent.getTreeLock()) {
                configureColumns(parent, PREFERRED_SIZE, null);
                return super.preferredLayoutSize(parent);
            }
        }
    }

    /// Server-side variant of [VerticalFlowLayout] for east and west legend docks.
    ///
    /// The live [VerticalFlowLayout] places components immediately. Image export instead needs a
    /// pure bounds calculation so [Legend] can paint each row against synthetic rectangles. This
    /// class mirrors the live column-wrapping algorithm closely enough to preserve the same visual
    /// grouping during export.
    private class ServerSideVerticalFlowLayout extends FlowLayout implements ServerSideLayout {
        /// Vertical alignment of each wrapped column.
        private final int verticalAlignment;

        /// Horizontal gap inserted between wrapped columns.
        private final int horizontalGap;

        /// Vertical gap inserted between rows and at the top and bottom padding.
        private final int verticalGap;

        /// Whether components are traversed from the last child to the first child.
        private final boolean reverseOrder;

        /// Creates the server-side vertical flow layout.
        ///
        /// @param verticalAlignment one of `VerticalFlowLayout.TOP`,
        ///     `VerticalFlowLayout.MIDDLE`, or `VerticalFlowLayout.BOTTOM`
        /// @param horizontalGap horizontal gap inserted between wrapped columns
        /// @param verticalGap vertical gap inserted between rows and at the top and bottom padding
        /// @param reverseOrder whether visible components are traversed from the end of the child
        ///     list
        public ServerSideVerticalFlowLayout(int verticalAlignment, int horizontalGap, int verticalGap,
                boolean reverseOrder) {
            this.verticalAlignment = verticalAlignment;
            this.horizontalGap = horizontalGap;
            this.verticalGap = verticalGap;
            this.reverseOrder = reverseOrder;
            super.setAlignment(FlowLayout.LEFT);
        }

        /// Stores or updates the measured size for one child component.
        private void putSize(Map<Component, Rectangle> componentBounds, Component component, int width, int height) {
            Rectangle bounds = componentBounds.get(component);
            if (bounds == null) {
                bounds = new Rectangle(width, height);
                componentBounds.put(component, bounds);
            } else {
                bounds.width = width;
                bounds.height = height;
            }
        }

        /// Stores or updates the location for one child component.
        private void putLocation(Map<Component, Rectangle> componentBounds, Component component, int x, int y) {
            Rectangle bounds = componentBounds.get(component);
            if (bounds == null) {
                bounds = new Rectangle(x, y, 0, 0);
                componentBounds.put(component, bounds);
            } else {
                bounds.x = x;
                bounds.y = y;
            }
        }

        /// Positions one wrapped column inside the exported bounds map.
        private void placeColumn(Map<Component, Rectangle> componentBounds, Container parent, int componentCount,
                int columnX, int columnY, int columnWidth, int extraVerticalSpace, int fromIndex, int toIndex) {
            int y = columnY;
            if (verticalAlignment == VerticalFlowLayout.MIDDLE)
                y += extraVerticalSpace / 2;
            else if (verticalAlignment == VerticalFlowLayout.BOTTOM)
                y += extraVerticalSpace;

            for (int logicalIndex = fromIndex; logicalIndex < toIndex; logicalIndex++) {
                int componentIndex = reverseOrder ? componentCount - 1 - logicalIndex : logicalIndex;
                Component component = parent.getComponent(componentIndex);
                Dimension size = component.getPreferredSize();
                if (component.isVisible()) {
                    int xOffset = 0;
                    if (getAlignment() == FlowLayout.CENTER)
                        xOffset = (columnWidth - size.width) / 2;
                    else if (getAlignment() == FlowLayout.RIGHT)
                        xOffset = columnWidth - size.width;
                    putLocation(componentBounds, component, columnX + xOffset, y);
                    y += verticalGap + size.height;
                }
            }
        }

        /// Predicts wrapped component rectangles for the current export bounds.
        @Override
        public Map<Component, Rectangle> computeBounds(Container parent, Rectangle bounds) {
            Map<Component, Rectangle> componentBounds = new HashMap<>();
            componentBounds.put(parent, bounds);

            Insets insets = parent.getInsets();
            int availableHeight = bounds.height - 2 * verticalGap;
            int y = insets.top;
            Dimension layoutSize = new Dimension(0, 0);
            int columnWidth = 0;
            int componentCount = parent.getComponentCount();
            int columnStartIndex = 0;

            for (int logicalIndex = 0; logicalIndex < componentCount; logicalIndex++) {
                int componentIndex = reverseOrder ? componentCount - 1 - logicalIndex : logicalIndex;
                Component component = parent.getComponent(componentIndex);
                if (!component.isVisible())
                    continue;

                Dimension preferredSize = component.getPreferredSize();
                putSize(componentBounds, component, preferredSize.width, preferredSize.height);
                if (logicalIndex == 0) {
                    y += preferredSize.height;
                    columnWidth = Math.max(columnWidth, preferredSize.width);
                } else if (y + preferredSize.height + insets.bottom <= availableHeight) {
                    y += verticalGap + preferredSize.height;
                    columnWidth = Math.max(columnWidth, preferredSize.width);
                } else {
                    placeColumn(componentBounds, parent, componentCount, layoutSize.width + insets.left,
                            insets.top + verticalGap, columnWidth, availableHeight - y - insets.bottom,
                            columnStartIndex, logicalIndex);
                    columnStartIndex = logicalIndex;
                    y = preferredSize.height + insets.top;
                    layoutSize.width += columnWidth + horizontalGap;
                    columnWidth = preferredSize.width;
                }
            }

            placeColumn(componentBounds, parent, componentCount, layoutSize.width + insets.left,
                    insets.top + verticalGap, columnWidth, availableHeight - y - insets.bottom,
                    columnStartIndex, componentCount);
            return componentBounds;
        }

        /// Does nothing because server-side layouts never move live components.
        @Override
        public void layoutContainer(Container parent) {
        }

        /// Returns the same result as [#preferredLayoutSize(Container)].
        @Override
        public Dimension minimumLayoutSize(Container parent) {
            return preferredLayoutSize(parent);
        }

        /// Estimates the wrapped export size from the off-screen chart height.
        ///
        /// Outside export mode this delegate is inactive, so it reports an empty size instead of
        /// trying to reason about stale live component geometry.
        @Override
        public Dimension preferredLayoutSize(Container parent) {
            Legend legend = getLegend();
            Chart chart = legend.getChart();
            if (chart == null || !chart.isPaintingImage())
                return new Dimension();

            Insets insets = parent.getInsets();
            Dimension rootSize = chart.getPaintContext().rootComponent.getSize();
            int availableHeight = rootSize.height - 2 * verticalGap;
            if (availableHeight <= 0)
                return computeSingleColumnSize(parent, insets);

            int y = insets.top;
            Dimension layoutSize = new Dimension(0, 0);
            int columnWidth = 0;
            int componentCount = parent.getComponentCount();
            for (int logicalIndex = 0; logicalIndex < componentCount; logicalIndex++) {
                int componentIndex = reverseOrder ? componentCount - 1 - logicalIndex : logicalIndex;
                Component component = parent.getComponent(componentIndex);
                if (!component.isVisible())
                    continue;

                Dimension preferredSize = component.getPreferredSize();
                if (logicalIndex == 0) {
                    y += preferredSize.height;
                    columnWidth = Math.max(columnWidth, preferredSize.width);
                } else if (y + preferredSize.height + insets.bottom <= availableHeight) {
                    y += verticalGap + preferredSize.height;
                    columnWidth = Math.max(columnWidth, preferredSize.width);
                } else {
                    y = preferredSize.height + insets.top;
                    layoutSize.width += columnWidth + horizontalGap;
                    columnWidth = preferredSize.width;
                }
            }

            layoutSize.height = availableHeight;
            layoutSize.width += columnWidth + insets.left + insets.right;
            return layoutSize;
        }

        /// Computes the fallback preferred size when no positive export height is available yet.
        private Dimension computeSingleColumnSize(Container parent, Insets insets) {
            Dimension layoutSize = new Dimension(0, 0);
            int componentCount = parent.getComponentCount();
            for (int logicalIndex = 0; logicalIndex < componentCount; logicalIndex++) {
                int componentIndex = reverseOrder ? componentCount - 1 - logicalIndex : logicalIndex;
                Component component = parent.getComponent(componentIndex);
                if (!component.isVisible())
                    continue;

                Dimension preferredSize = component.getPreferredSize();
                layoutSize.width = Math.max(layoutSize.width, preferredSize.width);
                if (logicalIndex > 1)
                    layoutSize.height += verticalGap;
                layoutSize.height += preferredSize.height;
            }

            layoutSize.width += insets.left + insets.right + horizontalGap * 2;
            layoutSize.height += insets.top + insets.bottom + verticalGap * 2;
            return layoutSize;
        }
    }

    /// Legend whose geometry is controlled by this layout manager.
    private final Legend legend;

    /// Orientation used by floating legends: `SwingConstants.HORIZONTAL` or
    /// `SwingConstants.VERTICAL`.
    private int floatingLayoutDirection;

    /// Order hint for north and south docks: `SwingConstants.LEADING` or
    /// `SwingConstants.TRAILING`.
    private int horizontalOrientation;

    /// Order hint for default, east/west, and vertical floating layouts: `SwingConstants.TOP` or
    /// `SwingConstants.BOTTOM`.
    private int verticalOrientation;

    /// Horizontal gap between legend entries or wrapped columns.
    private int horizontalGap;

    /// Vertical gap between legend rows.
    private int verticalGap;

    /// Cached live one-column layout used when the legend has no docking position.
    private transient LayoutManager defaultLayout;

    /// Cached server-side counterpart of [#defaultLayout].
    private transient LayoutManager serverSideDefaultLayout;

    /// Cached live north/south wrapping layout.
    private transient LayoutManager northSouthLayout;

    /// Cached server-side counterpart of [#northSouthLayout].
    private transient LayoutManager serverSideNorthSouthLayout;

    /// Cached live east/west vertical-flow layout.
    private transient LayoutManager eastWestLayout;

    /// Cached server-side counterpart of [#eastWestLayout].
    private transient LayoutManager serverSideEastWestLayout;

    /// Cached live floating layout.
    private transient LayoutManager floatingLayout;

    /// Cached server-side counterpart of [#floatingLayout].
    private transient LayoutManager serverSideFloatingLayout;

    /// Creates the layout manager with the historical default `5`-pixel gaps.
    ///
    /// @param legend the legend whose geometry this layout controls
    public LegendLayout(Legend legend) {
        this(legend, 5, 5);
    }

    /// Creates the layout manager with explicit entry gaps.
    ///
    /// The initial orientation matches the legacy defaults used by [Legend]: floating legends
    /// stack vertically, north/south docks fill from the trailing edge, and east/west or detached
    /// legends fill from the bottom.
    ///
    /// @param legend the legend whose geometry this layout controls
    /// @param horizontalGap the horizontal gap between entries or wrapped columns
    /// @param verticalGap the vertical gap between legend rows
    public LegendLayout(Legend legend, int horizontalGap, int verticalGap) {
        this.legend = legend;
        floatingLayoutDirection = SwingConstants.VERTICAL;
        horizontalOrientation = SwingConstants.TRAILING;
        verticalOrientation = SwingConstants.BOTTOM;
        this.horizontalGap = horizontalGap;
        this.verticalGap = verticalGap;
    }

    /// Forwards child registration to the concrete delegate active for the current paint mode.
    @Override
    public void addLayoutComponent(String name, Component component) {
        getActiveDelegate().addLayoutComponent(name, component);
    }

    /// Drops every cached concrete layout so the next access rebuilds it from current settings.
    ///
    /// Call this after changing gap or orientation settings outside the provided setter methods.
    public void clearCaches() {
        defaultLayout = null;
        serverSideDefaultLayout = null;
        northSouthLayout = null;
        serverSideNorthSouthLayout = null;
        eastWestLayout = null;
        serverSideEastWestLayout = null;
        floatingLayout = null;
        serverSideFloatingLayout = null;
    }

    /// Computes export-time bounds for the legend's current docking mode.
    ///
    /// The bounds rectangle is also published under `ServerSideLayout.BOUNDS_PROPERTY` on the
    /// legend component so nested server-side delegates can reuse the same available area when they
    /// need to derive wrapping decisions from the enclosing chart or legend rectangle.
    @Override
    public Map<Component, Rectangle> computeBounds(Container parent, Rectangle bounds) {
        synchronized (parent.getTreeLock()) {
            ((JComponent) parent).putClientProperty(ServerSideLayout.BOUNDS_PROPERTY, bounds);
            return ((ServerSideLayout) getServerSideDelegate()).computeBounds(parent, bounds);
        }
    }

    /// Creates the live fallback layout used when the legend has no docking slot yet.
    ///
    /// The default is a single-column grid whose order follows [#getVerticalOrientation()].
    protected LayoutManager createDefaultLayout() {
        return new GridLayout(0, 1, horizontalGap, verticalGap, verticalOrientation == SwingConstants.BOTTOM);
    }

    /// Creates the live east/west dock layout.
    ///
    /// The layout wraps rows into extra columns when the available chart height is exhausted.
    protected LayoutManager createEastWestLayout() {
        return new VerticalFlowLayout(VerticalFlowLayout.TOP, horizontalGap, verticalGap, verticalOrientation == SwingConstants.BOTTOM);
    }

    /// Creates the live floating layout.
    ///
    /// Floating legends can stack vertically or horizontally, and the secondary orientation decides
    /// whether rows fill from the leading/trailing edge or the top/bottom edge.
    protected LayoutManager createFloatingLayout() {
        if (floatingLayoutDirection == SwingConstants.HORIZONTAL)
            return new GridLayout(1, 0, horizontalGap, verticalGap, horizontalOrientation == SwingConstants.TRAILING);
        return new GridLayout(0, 1, horizontalGap, verticalGap, verticalOrientation == SwingConstants.BOTTOM);
    }

    /// Creates the live north/south dock layout.
    ///
    /// North and south docks wrap legend rows into multiple columns based on the current chart
    /// width.
    protected LayoutManager createNorthSouthLayout() {
        return new HorizontalLayout(horizontalGap, verticalGap, horizontalOrientation == SwingConstants.TRAILING);
    }

    /// Creates the server-side fallback layout used when the legend has no docking slot yet.
    private LayoutManager createServerSideDefaultLayout() {
        return new ServerSideGridLayout(0, 1, horizontalGap, verticalGap, verticalOrientation == SwingConstants.BOTTOM);
    }

    /// Creates the server-side north/south dock layout.
    private LayoutManager createServerSideNorthSouthLayout() {
        return new ServerSideHorizontalLayout(horizontalGap, verticalGap, horizontalOrientation == SwingConstants.TRAILING);
    }

    /// Creates the server-side east/west dock layout.
    private LayoutManager createServerSideEastWestLayout() {
        return new ServerSideVerticalFlowLayout(VerticalFlowLayout.TOP, horizontalGap, verticalGap,
                verticalOrientation == SwingConstants.BOTTOM);
    }

    /// Creates the server-side floating layout.
    private LayoutManager createServerSideFloatingLayout() {
        if (floatingLayoutDirection == SwingConstants.HORIZONTAL)
            return new ServerSideGridLayout(1, 0, horizontalGap, verticalGap, horizontalOrientation == SwingConstants.TRAILING);
        return new ServerSideGridLayout(0, 1, horizontalGap, verticalGap, verticalOrientation == SwingConstants.BOTTOM);
    }

    /// Returns the live delegate that matches the legend's current or preview docking position.
    protected final LayoutManager getDelegate() {
        return selectDelegate(false);
    }

    /// Returns the cached live fallback layout.
    protected LayoutManager getDefaultLayout() {
        if (defaultLayout == null)
            defaultLayout = createDefaultLayout();
        return defaultLayout;
    }

    /// Returns the cached live east/west layout.
    protected LayoutManager getEastWestLayout() {
        if (eastWestLayout == null)
            eastWestLayout = createEastWestLayout();
        return eastWestLayout;
    }

    /// Returns the cached live floating layout.
    protected LayoutManager getFloatingLayout() {
        if (floatingLayout == null)
            floatingLayout = createFloatingLayout();
        return floatingLayout;
    }

    /// Returns the floating direction used by [#createFloatingLayout()].
    public final int getFloatingLayoutDirection() {
        return floatingLayoutDirection;
    }

    /// Returns the horizontal gap between legend entries or wrapped columns.
    public int getHGap() {
        return horizontalGap;
    }

    /// Returns the orientation used by north/south docks and horizontal floating layouts.
    public final int getHorizontalOrientation() {
        return horizontalOrientation;
    }

    /// Returns the legend whose geometry this layout manages.
    public Legend getLegend() {
        return legend;
    }

    /// Returns the cached live north/south layout.
    protected LayoutManager getNorthSouthLayout() {
        if (northSouthLayout == null)
            northSouthLayout = createNorthSouthLayout();
        return northSouthLayout;
    }

    /// Returns the orientation used by detached, east/west, and vertical floating layouts.
    public final int getVerticalOrientation() {
        return verticalOrientation;
    }

    /// Returns the vertical gap between legend rows.
    public int getVGap() {
        return verticalGap;
    }

    /// Returns the delegate active for the current paint mode.
    private LayoutManager getActiveDelegate() {
        Chart chart = legend.getChart();
        return chart != null && chart.isPaintingImage() ? getServerSideDelegate() : getDelegate();
    }

    /// Returns the cached server-side fallback layout.
    private LayoutManager getServerSideDefaultLayout() {
        if (serverSideDefaultLayout == null)
            serverSideDefaultLayout = createServerSideDefaultLayout();
        return serverSideDefaultLayout;
    }

    /// Returns the cached server-side east/west layout.
    private LayoutManager getServerSideEastWestLayout() {
        if (serverSideEastWestLayout == null)
            serverSideEastWestLayout = createServerSideEastWestLayout();
        return serverSideEastWestLayout;
    }

    /// Returns the cached server-side floating layout.
    private LayoutManager getServerSideFloatingLayout() {
        if (serverSideFloatingLayout == null)
            serverSideFloatingLayout = createServerSideFloatingLayout();
        return serverSideFloatingLayout;
    }

    /// Returns the server-side delegate that matches the legend's current or preview docking
    /// position.
    private LayoutManager getServerSideDelegate() {
        return selectDelegate(true);
    }

    /// Returns the cached server-side north/south layout.
    private LayoutManager getServerSideNorthSouthLayout() {
        if (serverSideNorthSouthLayout == null)
            serverSideNorthSouthLayout = createServerSideNorthSouthLayout();
        return serverSideNorthSouthLayout;
    }

    /// Lays out the legend through the concrete delegate active for the current paint mode.
    @Override
    public void layoutContainer(Container parent) {
        synchronized (parent.getTreeLock()) {
            getActiveDelegate().layoutContainer(parent);
        }
    }

    /// Returns the smallest size reported by the active delegate, clamped to non-negative
    /// dimensions.
    @Override
    public Dimension minimumLayoutSize(Container parent) {
        synchronized (parent.getTreeLock()) {
            return clampNonNegative(getActiveDelegate().minimumLayoutSize(parent));
        }
    }

    /// Returns the preferred size reported by the active delegate, clamped to non-negative
    /// dimensions.
    @Override
    public Dimension preferredLayoutSize(Container parent) {
        synchronized (parent.getTreeLock()) {
            return clampNonNegative(getActiveDelegate().preferredLayoutSize(parent));
        }
    }

    /// Forwards child removal to the concrete delegate active for the current paint mode.
    @Override
    public void removeLayoutComponent(Component component) {
        getActiveDelegate().removeLayoutComponent(component);
    }

    /// Changes how floating legends wrap rows.
    ///
    /// Accepted values are `SwingConstants.HORIZONTAL` and `SwingConstants.VERTICAL`. Changing the
    /// value invalidates only the cached floating delegates.
    ///
    /// @param direction the new floating direction
    /// @throws IllegalArgumentException when `direction` is not a supported constant
    public void setFloatingLayoutDirection(int direction) {
        if (direction != SwingConstants.HORIZONTAL && direction != SwingConstants.VERTICAL)
            throw new IllegalArgumentException("Invalid floating layout direction.");
        if (direction != floatingLayoutDirection) {
            floatingLayoutDirection = direction;
            floatingLayout = null;
            serverSideFloatingLayout = null;
        }
    }

    /// Changes the horizontal gap between legend entries or wrapped columns.
    ///
    /// Changing this value invalidates every cached delegate because all concrete layouts consult
    /// it directly.
    public void setHGap(int horizontalGap) {
        if (horizontalGap != this.horizontalGap) {
            this.horizontalGap = horizontalGap;
            clearCaches();
        }
    }

    /// Changes the fill direction used by north/south docks and horizontal floating legends.
    ///
    /// Accepted values are `SwingConstants.LEADING` and `SwingConstants.TRAILING`.
    ///
    /// @param orientation the new horizontal orientation
    /// @throws IllegalArgumentException when `orientation` is not a supported constant
    public void setHorizontalOrientation(int orientation) {
        if (orientation != SwingConstants.LEADING && orientation != SwingConstants.TRAILING)
            throw new IllegalArgumentException("Invalid horizontal layout orientation.");
        if (orientation != horizontalOrientation) {
            horizontalOrientation = orientation;
            clearCaches();
        }
    }

    /// Changes the fill direction used by detached, east/west, and vertical floating legends.
    ///
    /// Accepted values are `SwingConstants.TOP` and `SwingConstants.BOTTOM`.
    ///
    /// @param orientation the new vertical orientation
    /// @throws IllegalArgumentException when `orientation` is not a supported constant
    public void setVerticalOrientation(int orientation) {
        if (orientation != SwingConstants.TOP && orientation != SwingConstants.BOTTOM)
            throw new IllegalArgumentException("Invalid vertical layout orientation.");
        if (orientation != verticalOrientation) {
            verticalOrientation = orientation;
            clearCaches();
        }
    }

    /// Changes the vertical gap between legend rows.
    ///
    /// Changing this value invalidates every cached delegate because all concrete layouts consult
    /// it directly.
    public void setVGap(int verticalGap) {
        if (verticalGap != this.verticalGap) {
            this.verticalGap = verticalGap;
            clearCaches();
        }
    }

    /// Clamps negative delegate sizes to zero to preserve Swing layout expectations.
    private Dimension clampNonNegative(Dimension size) {
        if (size.width >= 0 && size.height >= 0)
            return size;
        return new Dimension(Math.max(size.width, 0), Math.max(size.height, 0));
    }

    /// Returns the legend position that should drive delegate selection.
    ///
    /// During docking drag preview [Legend] publishes a temporary position under
    /// [#DRAG_POSITION_CLIENT_PROPERTY]. Once the drag ends, the normal persisted legend position
    /// takes over again.
    private String getEffectivePosition() {
        String position = (String) legend.getClientProperty(DRAG_POSITION_CLIENT_PROPERTY);
        return position != null ? position : legend.getPosition();
    }

    /// Selects either the live or the server-side delegate for the current effective position.
    private LayoutManager selectDelegate(boolean serverSide) {
        String position = getEffectivePosition();
        if (position == null)
            return serverSide ? getServerSideDefaultLayout() : getDefaultLayout();

        return switch (position) {
            case ChartLayout.NORTH_TOP, ChartLayout.NORTH_BOTTOM, ChartLayout.SOUTH_TOP,
                    ChartLayout.SOUTH_BOTTOM -> serverSide ? getServerSideNorthSouthLayout() : getNorthSouthLayout();
            case ChartLayout.ABSOLUTE -> serverSide ? getServerSideFloatingLayout() : getFloatingLayout();
            default -> serverSide ? getServerSideEastWestLayout() : getEastWestLayout();
        };
    }

    /// Returns the chart size that should govern live north/south wrapping.
    ///
    /// During export the legend still uses the root paint component size rather than its current
    /// on-screen bounds so wrapped rows match the target image geometry.
    private Dimension resolveChartSize() {
        Chart chart = legend.getChart();
        if (chart == null)
            return legend.getSize();
        if (chart.isPaintingImage())
            return chart.getPaintContext().rootComponent.getSize();
        return chart.getSize();
    }
}
