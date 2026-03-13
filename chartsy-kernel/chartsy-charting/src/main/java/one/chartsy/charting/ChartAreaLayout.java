package one.chartsy.charting;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.LayoutManager2;
import java.awt.Rectangle;
import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Iterator;

import one.chartsy.charting.util.Flags;
import one.chartsy.charting.util.GraphicUtil;

/// Lays out a [Chart.Area] by resolving the live plot rectangle from scale, renderer, and
/// margin geometry.
///
/// Margins flagged in [#getFixedMargins()] are treated as hard pixel insets. Remaining
/// sides are inferred from the bounds occupied by managed [Scale] instances and, when the
/// owning area requests it, renderer annotations. Cartesian charts iterate per side so
/// each edge can settle independently, while polar, pie, and radar charts collapse the
/// required space to a uniform inset that keeps the plot centered.
///
/// The layout caches the last live plot rectangle for [Chart.Area#getPlotRect()] and also
/// publishes temporary candidate plot rectangles through the owning area while auto-layout
/// measurement runs. Callers should therefore treat both the cached plot rectangle and the
/// [Insets] returned by [#getMargins()] as read-only views of internal state.
class ChartAreaLayout implements LayoutManager2, Serializable {
    private static final int DISABLED_FLAG = 1;
    private static final int FIXED_HORIZONTAL_MARGINS =
            Chart.Area.FIXED_LEFT_MARGIN | Chart.Area.FIXED_RIGHT_MARGIN;
    private static final int FIXED_VERTICAL_MARGINS =
            Chart.Area.FIXED_TOP_MARGIN | Chart.Area.FIXED_BOTTOM_MARGIN;
    private static final int FIXED_ALL_MARGINS = FIXED_HORIZONTAL_MARGINS | FIXED_VERTICAL_MARGINS;

    private final ArrayList<Scale> managedScales;
    private Insets margins;
    private Chart.Area area;
    private final Flags stateFlags;
    private int fixedMargins;

    transient Rectangle cachedPlotRect;
    transient boolean plotRectValid;

    ChartAreaLayout() {
        managedScales = new ArrayList<>(2);
        margins = new Insets(0, 0, 0, 0);
        stateFlags = new Flags();
        initializeTransients();
    }

    private static Insets computeInsetsBetween(Rectangle outerBounds, Rectangle innerBounds, Insets reuse) {
        Insets insets = (reuse != null) ? reuse : new Insets(0, 0, 0, 0);
        insets.left = innerBounds.x - outerBounds.x;
        insets.top = innerBounds.y - outerBounds.y;
        insets.right = outerBounds.width + outerBounds.x - innerBounds.width - innerBounds.x;
        insets.bottom = outerBounds.height + outerBounds.y - innerBounds.height - innerBounds.y;
        return insets;
    }

    private static Rectangle expandByInsets(Rectangle rectangle, Insets insets) {
        rectangle.x -= insets.left;
        rectangle.y -= insets.top;
        rectangle.width += insets.left + insets.right;
        rectangle.height += insets.top + insets.bottom;
        return rectangle;
    }

    /// Detaches this layout from its previously owning chart area.
    ///
    /// Subsequent layout requests fail until [#attachArea(Chart.Area)] binds the layout to a
    /// new area.
    void detachArea() {
        area = null;
    }

    /// Attaches this layout to the supplied chart area.
    ///
    /// The layout expects all future callbacks to originate from that same [Chart.Area]
    /// instance.
    void attachArea(Chart.Area area) {
        this.area = area;
    }

    /// Recomputes the cached plot rectangle for the attached area.
    ///
    /// The method honors the disabled flag and any active [ChartAreaSynchronizer]. When all
    /// four margins are fixed it applies the stored insets directly. Otherwise it runs the
    /// iterative auto-layout pass that measures attached scales and renderers.
    ///
    /// @throws IllegalArgumentException if this layout is not attached to `container`
    void layoutArea(Container container) {
        if (area == null || container != area)
            throw new IllegalArgumentException("layout not attached or not to a Chart.Area");

        Object chartLock = area.getChart().getLock();
        synchronized (chartLock) {
            if (isDisabled())
                return;

            ChartAreaSynchronizer synchronizer = ChartAreaSynchronizer.getInstalledSynchronizer(area.getChart());
            if (synchronizer != null)
                synchronizer.synchronizeMargins();

            if (getFixedMargins() != FIXED_ALL_MARGINS)
                autoLayoutPlotRect();
            else
                applyExplicitMargins();
        }
    }

    private boolean isResolved(Insets requiredMargins) {
        if (!isLeftFixed() && requiredMargins.left != 0)
            return false;
        if (!isRightFixed() && requiredMargins.right != 0)
            return false;
        if (!isBottomFixed() && requiredMargins.bottom != 0)
            return false;
        return isTopFixed() || requiredMargins.top == 0;
    }

    /// Fixes the left plot margin to an explicit pixel value.
    void setLeftMargin(int leftMargin) {
        boolean changed = leftMargin != margins.left;
        margins.left = leftMargin;
        updateFixedMargins(getFixedMargins() | Chart.Area.FIXED_LEFT_MARGIN, changed);
    }

    private void updateFixedMargins(int fixedMargins, boolean changed) {
        boolean revalidate = changed;
        if (fixedMargins != this.fixedMargins) {
            this.fixedMargins = fixedMargins;
            revalidate = true;
        }
        if (revalidate)
            area.revalidateLayout();
    }

    /// Fixes the left and right plot margins to explicit pixel values.
    void setHorizontalMargins(int leftMargin, int rightMargin) {
        boolean changed = leftMargin != margins.left || rightMargin != margins.right;
        margins.left = leftMargin;
        margins.right = rightMargin;
        updateFixedMargins(getFixedMargins() | FIXED_HORIZONTAL_MARGINS, changed);
    }

    /// Computes a plot rectangle for `drawRect` without installing it as the live layout result.
    ///
    /// During measurement the owning [Chart.Area] temporarily exposes candidate plot
    /// rectangles through [Chart.Area#getPlotRect()] so scales and renderers can measure
    /// themselves against the same geometry. The chart's scale caches are marked stale again
    /// before the method returns.
    Rectangle computePlotRect(Rectangle drawRect) {
        Chart chart = area.getChart();
        Object chartLock = chart.getLock();
        synchronized (chartLock) {
            Rectangle plotRect;
            try {
                chart.scalesUpToDate = true;

                Rectangle initialDrawRect = drawRect;
                area.exposedPlotRect = null;
                area.cachedPlotRect = initialDrawRect;
                Rectangle initialOccupiedBounds = computeOccupiedBounds(area.cachedPlotRect);
                Insets initialMargins = computeRequiredMargins(initialOccupiedBounds, drawRect, 0, 0, true);

                if (chart.getType() != Chart.CARTESIAN) {
                    int uniformInset = 0;
                    uniformInset = Math.max(uniformInset, initialMargins.right);
                    uniformInset = Math.max(uniformInset, initialMargins.bottom);
                    uniformInset = Math.max(uniformInset, initialMargins.left);
                    uniformInset = Math.max(uniformInset, initialMargins.top);
                    plotRect = new Rectangle(initialDrawRect);
                    plotRect.grow(-uniformInset, -uniformInset);
                } else {
                    Rectangle firstPassPlotRect = new Rectangle(initialDrawRect);
                    shrinkRect(firstPassPlotRect, initialMargins, 0, 0, 0, 0);
                    int topThreshold = -initialMargins.top;
                    int leftThreshold = -initialMargins.left;
                    int bottomThreshold = -initialMargins.bottom;
                    int rightThreshold = -initialMargins.right;

                    area.exposedPlotRect = null;
                    area.cachedPlotRect = firstPassPlotRect;
                    Rectangle firstPassBounds = computeOccupiedBounds(area.cachedPlotRect);
                    Insets secondPassMargins = computeRequiredMargins(firstPassBounds, drawRect, 0, 0, false);
                    if (isResolved(secondPassMargins)) {
                        plotRect = firstPassPlotRect;
                    } else {
                        Rectangle secondPassPlotRect = new Rectangle(firstPassPlotRect);
                        shrinkRect(secondPassPlotRect, secondPassMargins, topThreshold, leftThreshold, bottomThreshold,
                                rightThreshold);
                        topThreshold -= secondPassMargins.top;
                        leftThreshold -= secondPassMargins.left;
                        bottomThreshold -= secondPassMargins.bottom;
                        rightThreshold -= secondPassMargins.right;

                        area.exposedPlotRect = null;
                        area.cachedPlotRect = secondPassPlotRect;
                        Rectangle secondPassBounds = computeOccupiedBounds(area.cachedPlotRect);
                        Insets finalMargins = computeRequiredMargins(secondPassBounds, drawRect, 0, 0, false);
                        if (isResolved(finalMargins)) {
                            plotRect = secondPassPlotRect;
                        } else {
                            boolean widthSaturated =
                                    !(firstPassPlotRect.width == secondPassPlotRect.width && secondPassPlotRect.width != 0)
                                            && firstPassBounds.width == secondPassBounds.width
                                            && secondPassBounds.width > drawRect.width;
                            boolean heightSaturated =
                                    !(firstPassPlotRect.height == secondPassPlotRect.height
                                            && secondPassPlotRect.height != 0)
                                            && firstPassBounds.height == secondPassBounds.height
                                            && secondPassBounds.height > drawRect.height;

                            if (!widthSaturated && !heightSaturated) {
                                plotRect = new Rectangle(secondPassPlotRect);
                                relaxRect(plotRect, finalMargins, topThreshold, leftThreshold, bottomThreshold,
                                        rightThreshold);
                            } else {
                                int minimumWidth = widthSaturated ? secondPassBounds.width : 0;
                                int minimumHeight = heightSaturated ? secondPassBounds.height : 0;

                                initialMargins = computeRequiredMargins(initialOccupiedBounds, drawRect, minimumWidth,
                                        minimumHeight, true);
                                firstPassPlotRect = new Rectangle(initialDrawRect);
                                shrinkRect(firstPassPlotRect, initialMargins, 0, 0, 0, 0);
                                topThreshold = -initialMargins.top;
                                leftThreshold = -initialMargins.left;
                                bottomThreshold = -initialMargins.bottom;
                                rightThreshold = -initialMargins.right;

                                area.exposedPlotRect = null;
                                area.cachedPlotRect = firstPassPlotRect;
                                firstPassBounds = computeOccupiedBounds(area.cachedPlotRect);
                                secondPassMargins = computeRequiredMargins(firstPassBounds, drawRect, minimumWidth,
                                        minimumHeight, false);
                                if (isResolved(secondPassMargins)) {
                                    plotRect = firstPassPlotRect;
                                } else {
                                    secondPassPlotRect = new Rectangle(firstPassPlotRect);
                                    shrinkRect(secondPassPlotRect, secondPassMargins, topThreshold, leftThreshold,
                                            bottomThreshold, rightThreshold);
                                    topThreshold -= secondPassMargins.top;
                                    leftThreshold -= secondPassMargins.left;
                                    bottomThreshold -= secondPassMargins.bottom;
                                    rightThreshold -= secondPassMargins.right;

                                    area.exposedPlotRect = null;
                                    area.cachedPlotRect = secondPassPlotRect;
                                    secondPassBounds = computeOccupiedBounds(area.cachedPlotRect);
                                    finalMargins = computeRequiredMargins(secondPassBounds, drawRect, minimumWidth,
                                            minimumHeight, false);
                                    if (isResolved(finalMargins))
                                        plotRect = secondPassPlotRect;
                                    else {
                                        plotRect = new Rectangle(secondPassPlotRect);
                                        relaxRect(plotRect, finalMargins, topThreshold, leftThreshold, bottomThreshold,
                                                rightThreshold);
                                    }
                                }
                            }
                        }
                    }
                }
            } finally {
                area.exposedPlotRect = null;
                area.cachedPlotRect = null;
                chart.invalidateScales();
            }
            return plotRect;
        }
    }

    private void applyFixedMargins(Rectangle rectangle, Insets insets) {
        if (isRightFixed())
            rectangle.width -= insets.right;
        if (isLeftFixed()) {
            rectangle.translate(insets.left, 0);
            rectangle.width -= insets.left;
        }
        if (isTopFixed()) {
            rectangle.translate(0, insets.top);
            rectangle.height -= insets.top;
        }
        if (isBottomFixed())
            rectangle.height -= insets.bottom;
    }

    private void shrinkRect(Rectangle rectangle, Insets insets, int topThreshold, int leftThreshold,
            int bottomThreshold, int rightThreshold) {
        if (isRightFixed() || insets.right >= rightThreshold)
            rectangle.width -= insets.right;

        if (isLeftFixed() || insets.left >= leftThreshold) {
            rectangle.x += insets.left;
            rectangle.width -= insets.left;
        }
        if (rectangle.width < 0)
            rectangle.width = 0;

        if (isTopFixed() || insets.top >= topThreshold) {
            rectangle.y += insets.top;
            rectangle.height -= insets.top;
        }

        if (isBottomFixed() || insets.bottom >= bottomThreshold)
            rectangle.height -= insets.bottom;
        if (rectangle.height < 0)
            rectangle.height = 0;
    }

    /// Computes the margins required to fit `occupiedBounds` inside `drawRect`.
    ///
    /// `minimumWidth` and `minimumHeight` let the Cartesian auto-layout retry with a
    /// non-collapsible center area when the measured content is wider or taller than the
    /// available draw rectangle.
    Insets computeRequiredMargins(Rectangle occupiedBounds, Rectangle drawRect, int minimumWidth, int minimumHeight,
            boolean includeFixedMargins) {
        Insets requiredMargins = new Insets(0, 0, 0, 0);
        if (isLeftFixed())
            requiredMargins.left = includeFixedMargins ? margins.left : 0;
        if (isRightFixed())
            requiredMargins.right = includeFixedMargins ? margins.right : 0;
        if (isTopFixed())
            requiredMargins.top = includeFixedMargins ? margins.top : 0;
        if (isBottomFixed())
            requiredMargins.bottom = includeFixedMargins ? margins.bottom : 0;

        if (occupiedBounds == null || occupiedBounds.isEmpty())
            return requiredMargins;

        int widthDelta = Math.max(occupiedBounds.width, minimumWidth) - Math.max(drawRect.width, minimumWidth);
        if (!isLeftFixed() && !isRightFixed()) {
            if (widthDelta == occupiedBounds.width - drawRect.width) {
                requiredMargins.left = drawRect.x - occupiedBounds.x;
                requiredMargins.right =
                        occupiedBounds.x + occupiedBounds.width - (drawRect.x + drawRect.width);
            } else {
                requiredMargins.left =
                        (drawRect.x - occupiedBounds.x + (widthDelta - occupiedBounds.width + drawRect.width)) >> 1;
                requiredMargins.right = widthDelta - requiredMargins.left;
                if (requiredMargins.left < 0) {
                    int correction = Math.min(-requiredMargins.left, requiredMargins.right);
                    if (correction > 0) {
                        requiredMargins.left += correction;
                        requiredMargins.right -= correction;
                    }
                } else if (requiredMargins.right > 0) {
                    int correction = Math.min(requiredMargins.left, -requiredMargins.right);
                    if (correction > 0) {
                        requiredMargins.left -= correction;
                        requiredMargins.right += correction;
                    }
                }
            }
        } else if (!isLeftFixed()) {
            requiredMargins.left = widthDelta - requiredMargins.right;
        } else if (!isRightFixed()) {
            requiredMargins.right = widthDelta - requiredMargins.left;
        }

        int heightDelta = Math.max(occupiedBounds.height, minimumHeight) - Math.max(drawRect.height, minimumHeight);
        if (!isTopFixed() && !isBottomFixed()) {
            if (heightDelta == occupiedBounds.height - drawRect.height) {
                requiredMargins.top = drawRect.y - occupiedBounds.y;
                requiredMargins.bottom =
                        occupiedBounds.y + occupiedBounds.height - (drawRect.y + drawRect.height);
            } else {
                requiredMargins.bottom =
                        (occupiedBounds.y - drawRect.y + (heightDelta + occupiedBounds.height - drawRect.height)) >> 1;
                requiredMargins.top = heightDelta - requiredMargins.bottom;
                if (requiredMargins.top < 0) {
                    int correction = Math.min(-requiredMargins.top, requiredMargins.bottom);
                    if (correction > 0) {
                        requiredMargins.top += correction;
                        requiredMargins.bottom -= correction;
                    }
                } else if (requiredMargins.bottom > 0) {
                    int correction = Math.min(requiredMargins.top, -requiredMargins.bottom);
                    if (correction > 0) {
                        requiredMargins.top -= correction;
                        requiredMargins.bottom += correction;
                    }
                }
            }
        } else if (!isTopFixed()) {
            requiredMargins.top = heightDelta - requiredMargins.bottom;
        } else if (!isBottomFixed()) {
            requiredMargins.bottom = heightDelta - requiredMargins.top;
        }
        return requiredMargins;
    }

    /// Registers or unregisters a [Scale] whose bounds should contribute to auto-layout.
    synchronized void setScaleManaged(Scale scale, boolean managed) {
        if (managed)
            managedScales.add(scale);
        else
            managedScales.remove(scale);
    }

    @Override
    public void addLayoutComponent(Component component, Object constraints) {
    }

    @Override
    public void addLayoutComponent(String name, Component component) {
    }

    /// Fixes the right plot margin to an explicit pixel value.
    void setRightMargin(int rightMargin) {
        boolean changed = rightMargin != margins.right;
        margins.right = rightMargin;
        updateFixedMargins(getFixedMargins() | Chart.Area.FIXED_RIGHT_MARGIN, changed);
    }

    /// Fixes the top and bottom plot margins to explicit pixel values.
    void setVerticalMargins(int topMargin, int bottomMargin) {
        boolean changed = topMargin != margins.top || bottomMargin != margins.bottom;
        margins.top = topMargin;
        margins.bottom = bottomMargin;
        updateFixedMargins(getFixedMargins() | FIXED_VERTICAL_MARGINS, changed);
    }

    /// Measures the bounds occupied by managed scales, and optionally renderer annotations,
    /// when they are laid out against `plotRect`.
    private synchronized Rectangle computeManagedBounds(Rectangle plotRect) {
        int scaleCount = managedScales.size();
        if (scaleCount == 0)
            return null;

        Rectangle occupiedBounds = new Rectangle();
        Rectangle2D previousBounds = null;
        Rectangle[] previousScaleBounds = new Rectangle[scaleCount];
        for (int scaleIndex = 0; scaleIndex < scaleCount; scaleIndex++)
            previousScaleBounds[scaleIndex] = (Rectangle) managedScales.get(scaleIndex).getPlotRect().clone();

        try {
            for (int scaleIndex = 0; scaleIndex < scaleCount; scaleIndex++)
                managedScales.get(scaleIndex).setPlotRect(plotRect, true);
            for (int scaleIndex = 0; scaleIndex < scaleCount; scaleIndex++)
                managedScales.get(scaleIndex).invalidatePreparedState();
            for (int scaleIndex = 0; scaleIndex < scaleCount; scaleIndex++)
                managedScales.get(scaleIndex).prepare();

            for (int scaleIndex = 0; scaleIndex < scaleCount; scaleIndex++) {
                Scale scale = managedScales.get(scaleIndex);
                if (scale.isViewable()) {
                    Rectangle scaleBounds = scale.getBoundsUsingCache(previousBounds).getBounds();
                    previousBounds = scaleBounds;
                    GraphicUtil.addToRect(occupiedBounds, scaleBounds);
                }
            }

            if (area.isPlotRectIncludingAnnotations()) {
                Chart chart = area.getChart();
                int rendererCount = chart.getRendererCount();
                for (int rendererIndex = 0; rendererIndex < rendererCount; rendererIndex++) {
                    ChartRenderer renderer = chart.getRenderer(rendererIndex);
                    if (renderer.isViewable()) {
                        Rectangle rendererBounds = renderer.getBounds(previousBounds, true).getBounds();
                        previousBounds = rendererBounds;
                        GraphicUtil.addToRect(occupiedBounds, rendererBounds);
                    }
                }
            }
        } finally {
            for (int scaleIndex = 0; scaleIndex < scaleCount; scaleIndex++)
                managedScales.get(scaleIndex).setPlotRect(previousScaleBounds[scaleIndex], true);
        }
        return occupiedBounds;
    }

    private void relaxRect(Rectangle rectangle, Insets insets, int topThreshold, int leftThreshold,
            int bottomThreshold, int rightThreshold) {
        if (!isRightFixed() && insets.right >= rightThreshold)
            rectangle.width -= insets.right;
        if (!isLeftFixed() && insets.left >= leftThreshold) {
            rectangle.x += insets.left;
            rectangle.width -= insets.left;
        }
        if (rectangle.width < 0)
            rectangle.width = 0;
        if (!isTopFixed() && insets.top >= topThreshold) {
            rectangle.y += insets.top;
            rectangle.height -= insets.top;
        }
        if (!isBottomFixed() && insets.bottom >= bottomThreshold)
            rectangle.height -= insets.bottom;
        if (rectangle.height < 0)
            rectangle.height = 0;
    }

    private void applyExplicitMargins() {
        Rectangle plotRect = area.getDrawRect();
        applyFixedMargins(plotRect, margins);
        updatePlotRect(plotRect);
    }

    /// Fixes the top plot margin to an explicit pixel value.
    void setTopMargin(int topMargin) {
        boolean changed = topMargin != margins.top;
        margins.top = topMargin;
        updateFixedMargins(getFixedMargins() | Chart.Area.FIXED_TOP_MARGIN, changed);
    }

    private Rectangle computeOccupiedBounds(Rectangle plotRect) {
        Rectangle occupiedBounds = computeManagedBounds(plotRect);
        Insets rendererMargins = getRendererPreferredMargins();
        if (!GraphicUtil.isEmpty(rendererMargins)) {
            Rectangle plotBoundsWithMargins = expandByInsets(new Rectangle(plotRect), rendererMargins);
            if (occupiedBounds == null)
                occupiedBounds = plotBoundsWithMargins;
            else
                occupiedBounds.add(plotBoundsWithMargins);
        }
        return occupiedBounds;
    }

    private Insets getRendererPreferredMargins() {
        Insets rendererMargins = new Insets(0, 0, 0, 0);
        Iterator<ChartRenderer> rendererIterator = area.getChart().getRendererIterator();
        while (rendererIterator.hasNext()) {
            ChartRenderer renderer = rendererIterator.next();
            if (renderer.isViewable())
                rendererMargins = GraphicUtil.mergeInsets(rendererMargins, renderer.getPreferredMargins());
        }
        return rendererMargins;
    }

    /// Fixes the bottom plot margin to an explicit pixel value.
    void setBottomMargin(int bottomMargin) {
        boolean changed = bottomMargin != margins.bottom;
        margins.bottom = bottomMargin;
        updateFixedMargins(getFixedMargins() | Chart.Area.FIXED_BOTTOM_MARGIN, changed);
    }

    /// Installs `plotRect` as the current live plot rectangle and notifies dependent state.
    ///
    /// The chart-area change event is fired with the new rectangle, and the previous plot
    /// rectangle is added to the dirty repaint region so incremental repaint bookkeeping can
    /// cover any area vacated by the layout change.
    private void updatePlotRect(Rectangle plotRect) {
        if (plotRectValid && plotRect.equals(cachedPlotRect))
            return;

        Rectangle previousPlotRect = new Rectangle(cachedPlotRect);
        cachedPlotRect.setBounds(plotRect);
        plotRectValid = true;

        Chart chart = area.getChart();
        chart.updateScales(plotRect);
        chart.fireChartAreaChanged(plotRect);
        area.addDirtyRegion(previousPlotRect);
    }

    private void autoLayoutPlotRect() {
        Rectangle drawRect = area.getDrawRect();
        Rectangle plotRect = computePlotRect(drawRect);
        updatePlotRect(plotRect);
        margins = computeInsetsBetween(drawRect, plotRect, margins);
    }

    private boolean isTopFixed() {
        return (fixedMargins & Chart.Area.FIXED_TOP_MARGIN) != 0;
    }

    private boolean isBottomFixed() {
        return (fixedMargins & Chart.Area.FIXED_BOTTOM_MARGIN) != 0;
    }

    /// Returns the bit mask describing which margins are currently fixed explicitly.
    public final int getFixedMargins() {
        return fixedMargins;
    }

    @Override
    public float getLayoutAlignmentX(Container container) {
        return 0.5f;
    }

    @Override
    public float getLayoutAlignmentY(Container container) {
        return 0.5f;
    }

    /// Returns the current margin state tracked by this layout.
    ///
    /// When auto layout is active the returned [Insets] hold the most recently computed
    /// margins rather than a `null` sentinel. The object is live layout state and must be
    /// treated as read-only by callers.
    public Insets getMargins() {
        return margins;
    }

    private boolean isLeftFixed() {
        return (fixedMargins & Chart.Area.FIXED_LEFT_MARGIN) != 0;
    }

    private boolean isRightFixed() {
        return (fixedMargins & Chart.Area.FIXED_RIGHT_MARGIN) != 0;
    }

    @Override
    public void invalidateLayout(Container container) {
    }

    /// Returns whether live layout recomputation is currently suppressed.
    public boolean isDisabled() {
        return stateFlags.getFlag(DISABLED_FLAG);
    }

    private void initializeTransients() {
        cachedPlotRect = new Rectangle(0, 0, 0, 0);
        plotRectValid = false;
    }

    @Override
    public void layoutContainer(Container container) {
        Object treeLock = container.getTreeLock();
        synchronized (treeLock) {
            layoutArea(container);
        }
    }

    @Override
    public Dimension maximumLayoutSize(Container container) {
        return new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE);
    }

    @Override
    public Dimension minimumLayoutSize(Container container) {
        return new Dimension(1, 1);
    }

    @Override
    public Dimension preferredLayoutSize(Container container) {
        return area.getSize();
    }

    @Serial
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        initializeTransients();
    }

    @Override
    public void removeLayoutComponent(Component component) {
    }

    /// Enables or disables live layout updates.
    ///
    /// When layout is re-enabled with `refresh` set, the owning area is invalidated,
    /// validated, and repainted immediately so the deferred plot rectangle becomes visible.
    public void setDisabled(boolean disabled, boolean refresh) {
        if (disabled != isDisabled()) {
            stateFlags.setFlag(DISABLED_FLAG, disabled);
            if (!disabled && refresh) {
                area.invalidate();
                area.validate();
                area.repaint();
            }
        }
    }

    /// Replaces all explicit plot margins in one call.
    ///
    /// Passing `null` clears fixed-margin mode and lets the next layout pass recompute the
    /// margins automatically. Until that pass runs, [#getMargins()] continues to expose the
    /// last known insets.
    public void setMargins(Insets margins) {
        if (margins == null)
            updateFixedMargins(0, false);
        else {
            boolean changed = !margins.equals(this.margins);
            this.margins = (Insets) margins.clone();
            updateFixedMargins(FIXED_ALL_MARGINS, changed);
        }
    }

    @Override
    public String toString() {
        return "ChartAreaLayout [Fixed: " + getFixedMargins() + "]: " + margins;
    }
}
