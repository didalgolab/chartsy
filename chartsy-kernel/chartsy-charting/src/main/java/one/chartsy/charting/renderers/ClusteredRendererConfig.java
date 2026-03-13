package one.chartsy.charting.renderers;

import java.util.Iterator;

import one.chartsy.charting.ChartRenderer;

/// Layout strategy for [CompositeChartRenderer] implementations that place variable-width children
/// side by side within one category slot.
///
/// `clusterWidth` defines the total horizontal footprint reserved for the whole cluster as a
/// percentage of the parent renderer's category width. `overlap` defines how much adjacent
/// children may cover each other instead of only touching at their edges.
class ClusteredRendererConfig extends CompositeRendererConfig {

    static final double DEFAULT_CLUSTER_WIDTH = 80.0;
    static final double DEFAULT_OVERLAP = 0.0;

    private double overlap;
    private double clusterWidth;

    ClusteredRendererConfig(CompositeChartRenderer renderer) {
        super(renderer);
        this.overlap = DEFAULT_OVERLAP;
        this.clusterWidth = DEFAULT_CLUSTER_WIDTH;
    }

    /// Recomputes clustered child widths and offsets after the child set changes.
    @Override
    void updateChildren() {
        updateChildLayout();
    }

    /// Applies the clustered layout when this configuration becomes active.
    @Override
    void activate() {
        updateChildLayout();
    }

    /// Restores unshifted child widths when another layout strategy replaces clustering.
    @Override
    void deactivate() {
        resetChildLayout();
    }

    /// Returns the total width reserved for the cluster as a percentage of the parent slot.
    public final double getClusterWidth() {
        return clusterWidth;
    }

    /// Returns the overlap percentage between adjacent clustered children.
    public double getOverlap() {
        return overlap;
    }

    private void resetChildLayout() {
        CompositeChartRenderer renderer = super.getRenderer();
        double parentWidthPercent = ((VariableWidthRenderer) renderer).getWidthPercent();

        for (Iterator<ChartRenderer> iterator = renderer.getChildIterator(); iterator.hasNext();) {
            ChartRenderer child = iterator.next();
            child.setXShift(0.0);
            ((VariableWidthRenderer) child).setWidthPercent(parentWidthPercent);
        }
    }

    private void updateChildLayout() {
        CompositeChartRenderer renderer = super.getRenderer();
        int childCount = renderer.getChildCount();
        if (childCount == 0)
            return;

        double overlapFraction = getOverlap() / 100.0;
        double clusterWidthFraction = getClusterWidth() / 100.0;
        double childWidthPercent = clusterWidthFraction / (childCount - (childCount - 1) * overlapFraction) * 100.0;
        double totalVisibleWidth = 0.0;

        for (Iterator<ChartRenderer> iterator = renderer.getChildIterator(); iterator.hasNext();) {
            VariableWidthRenderer child = (VariableWidthRenderer) iterator.next();
            child.setWidthPercent(childWidthPercent);
            totalVisibleWidth += child.getWidth() * (1.0 - overlapFraction);
        }

        double xOffset = 0.0;
        for (Iterator<ChartRenderer> iterator = renderer.getChildIterator(); iterator.hasNext();) {
            ChartRenderer child = iterator.next();
            VariableWidthRenderer variableWidthChild = (VariableWidthRenderer) child;
            double visibleWidth = variableWidthChild.getWidth() * (1.0 - overlapFraction);
            child.setXShift(xOffset + visibleWidth / 2.0 - totalVisibleWidth / 2.0);
            xOffset += visibleWidth;
        }
    }

    /// Updates the total cluster footprint.
    ///
    /// @param clusterWidth cluster width as a percentage of the parent category slot
    /// @param updateLayout whether the new width should be applied immediately
    public final void setClusterWidth(double clusterWidth, boolean updateLayout) {
        if (clusterWidth != this.clusterWidth) {
            this.clusterWidth = clusterWidth;
            if (updateLayout)
                updateChildLayout();
        }
    }

    /// Updates the overlap percentage between adjacent clustered children.
    ///
    /// @param overlap overlap percentage, where `0` means touching edges
    /// @param updateLayout whether the new overlap should be applied immediately
    public void setOverlap(double overlap, boolean updateLayout) {
        if (overlap != this.overlap) {
            this.overlap = overlap;
            if (updateLayout)
                updateChildLayout();
        }
    }
}

