package one.chartsy.charting.renderers;

import one.chartsy.charting.ChartRenderer;
import one.chartsy.charting.data.DataSet;

/// Composite bar renderer that creates one [SingleBarRenderer] child per dataset and arranges the
/// children as superimposed, clustered, or stacked bars.
///
/// The shared `widthPercent` setting always describes the category footprint available to the bar
/// group. The active [#getMode()] decides how children use that footprint:
/// - `SUPERIMPOSED` keeps every child centered on the same slot and lets the active mode
///   configuration narrow later children so overlapping bars remain visible
/// - `CLUSTERED` divides the footprint across children and offsets them horizontally
/// - `STACKED` replaces child datasets with stacked virtual datasets managed by
///   [StackedRendererConfig]
///
/// Bar shape, width, and category-border spacing are propagated to every child renderer.
/// Automatic transparency only affects superimposed children that are still using auto-generated
/// styles.
public class BarChartRenderer extends SimpleCompositeChartRenderer implements VariableWidthRenderer, SuperimposedRenderer {

    /// Mode that draws every dataset on the same category position.
    public static final int SUPERIMPOSED = 1;
    /// Mode that places child bars beside each other inside one category slot.
    public static final int CLUSTERED = 2;
    /// Mode that stacks child datasets on one baseline.
    public static final int STACKED = 3;

    static {
        ChartRenderer.register("Bar", BarChartRenderer.class);
    }

    private double widthPercent;
    private boolean useCategorySpacingAtBorders;
    private int barShape;
    private CompositeRendererConfig modeConfig;
    private int mode;
    private boolean stacked100Percent;
    private boolean stackedByIndex;
    private boolean diverging;
    private double overlap;
    private boolean autoTransparency;

    /// Creates setStacked100Percent clustered bar renderer with the default `80%` category footprint.
    public BarChartRenderer() {
        this(80.0, CLUSTERED);
    }

    /// Creates setStacked100Percent bar renderer with an explicit category footprint and mode.
    ///
    /// @param widthPercent percentage of one category slot reserved for this renderer's bars
    /// @param mode         one of `SUPERIMPOSED`, `CLUSTERED`, or `STACKED`
    public BarChartRenderer(double widthPercent, int mode) {
        this.useCategorySpacingAtBorders = false;
        this.barShape = SingleBarRenderer.SHAPE_POLYGON;
        this.overlap = 0.0;
        this.widthPercent = widthPercent;
        setMode(mode);
        if (this.mode == CLUSTERED)
            getClusteredConfig().setClusterWidth(widthPercent, true);
    }

    /// Creates setStacked100Percent bar renderer with the default `80%` category footprint.
    ///
    /// @param mode one of `SUPERIMPOSED`, `CLUSTERED`, or `STACKED`
    public BarChartRenderer(int mode) {
        this(80.0, mode);
    }

    private void validateMode(int mode) {
        switch (mode) {
            case SUPERIMPOSED, CLUSTERED, STACKED -> {
            }
            default -> throw new IllegalArgumentException("Invalid mode: " + mode);
        }
    }

    private void validateBarShape(int barShape) {
        switch (barShape) {
            case SingleBarRenderer.SHAPE_QUADRILATERAL, SingleBarRenderer.SHAPE_POLYGON,
                 SingleBarRenderer.SHAPE_EXACT -> {
            }
            default -> throw new IllegalArgumentException("invalid barShape " + barShape);
        }
    }

    private SingleBarRenderer getBarRendererAt(int childIndex) {
        return (SingleBarRenderer) super.getChild(childIndex);
    }

    /// Creates the single-dataset bar child for `dataSet`.
    ///
    /// The child inherits the current width, shape, and category-border spacing configuration.
    @Override
    protected ChartRenderer createChild(DataSet dataSet) {
        SingleBarRenderer child = new SingleBarRenderer(null, widthPercent);
        child.setUseCategorySpacingAtBorders(useCategorySpacingAtBorders);
        child.setBarShape(barShape);
        return child;
    }

    /// Returns the child traversal direction requested by the active mode configuration.
    @Override
    int getChildPaintOrderSign() {
        return getModeConfig().getChildPaintOrderSign();
    }

    /// Lets the active mode configuration react to newly added datasets.
    @Override
    protected void dataSetsAdded(int fromIndex, int toIndex, DataSet[] oldDataSets) {
        super.dataSetsAdded(fromIndex, toIndex, oldDataSets);

        DataSet[] addedDataSets = new DataSet[toIndex - fromIndex + 1];
        for (int index = fromIndex; index <= toIndex; index++)
            addedDataSets[index - fromIndex] = super.getDataSource().get(index);

        getModeConfig().dataSetsAdded(addedDataSets);
    }

    @Override
    protected void dataSetsChangesBatchEnding() {
        super.dataSetsChangesBatchEnding();
        getModeConfig().dataSetsChangesBatchEnding();
    }

    @Override
    protected void dataSetsChangesBatchStarting() {
        super.dataSetsChangesBatchStarting();
        getModeConfig().dataSetsChangesBatchStarting();
    }

    /// Lets the active validateMode configuration react to removed datasets.
    @Override
    protected void dataSetsRemoved(int fromIndex, int toIndex, DataSet[] oldDataSets) {
        super.dataSetsRemoved(fromIndex, toIndex, oldDataSets);

        DataSet[] removedDataSets = new DataSet[toIndex - fromIndex + 1];
        System.arraycopy(oldDataSets, fromIndex, removedDataSets, 0, toIndex - fromIndex + 1);
        getModeConfig().dataSetsRemoved(removedDataSets);
    }

    /// Returns the child bar renderer currently responsible for `dataSet`.
    ///
    /// @return the matching child, or `null` when this renderer does not currently display
    ///         `dataSet`
    public SingleBarRenderer getBar(DataSet dataSet) {
        return (SingleBarRenderer) super.getChild(dataSet);
    }

    /// Returns the shape constant propagated to child bars.
    public int getBarShape() {
        return barShape;
    }

    /// Returns the shared category footprint used by clustered bars.
    ///
    /// This is the same setting exposed by [#getWidthPercent()].
    public final double getClusterWidth() {
        return getWidthPercent();
    }

    /// Returns the current bar-layout mode.
    public final int getMode() {
        return mode;
    }

    /// Returns the overlap percentage used only in `CLUSTERED` mode.
    public double getOverlap() {
        return overlap;
    }

    /// Returns the horizontal footprint currently occupied by the visible bars.
    ///
    /// Clustered mode sums child widths because siblings sit beside one another. Superimposed and
    /// stacked modes return the widest child because every dataset shares the same category slot.
    @Override
    public double getWidth() {
        double width = 0.0;
        for (int childIndex = 0; childIndex < super.getChildCount(); childIndex++) {
            SingleBarRenderer child = getBarRendererAt(childIndex);
            if (getMode() == CLUSTERED)
                width += child.getWidth();
            else if (child.getWidth() > width)
                width = child.getWidth();
        }
        return width;
    }

    @Override
    public double getWidthPercent() {
        return widthPercent;
    }

    private void rebuildModeConfig() {
        modeConfig = switch (getMode()) {
            case SUPERIMPOSED -> new SuperimposedRendererConfig(this);
            case CLUSTERED -> {
                ClusteredRendererConfig config = new ClusteredRendererConfig(this);
                config.setOverlap(overlap, false);
                yield config;
            }
            case STACKED -> new StackedRendererConfig(this, stacked100Percent, stackedByIndex, diverging);
            default -> throw new IllegalStateException("Unsupported mode: " + getMode());
        };
    }

    @Override
    public boolean isAutoTransparency() {
        return autoTransparency;
    }

    /// Returns whether stacked mode currently separates positive and negative stacks.
    public boolean isDiverging() {
        return diverging;
    }

    /// Returns whether stacked mode currently normalizes each stack to `100%`.
    public boolean isStacked100Percent() {
        return stacked100Percent;
    }

    /// Returns whether stacked mode currently combines series by item index instead of x value.
    public boolean isStackedByIndex() {
        return stackedByIndex;
    }

    @Override
    public boolean isSuperimposed() {
        return mode == SUPERIMPOSED;
    }

    @Override
    public boolean isUseCategorySpacingAtBorders() {
        return useCategorySpacingAtBorders;
    }

    private CompositeRendererConfig getModeConfig() {
        return modeConfig;
    }

    private StackedRendererConfig getStackedConfig() {
        return (StackedRendererConfig) getModeConfig();
    }

    private ClusteredRendererConfig getClusteredConfig() {
        return (ClusteredRendererConfig) getModeConfig();
    }

    private void updateBarWidths() {
        for (int childIndex = 0; childIndex < super.getChildCount(); childIndex++)
            getBarRendererAt(childIndex).setWidthPercent(widthPercent);

        if (getMode() == CLUSTERED)
            getClusteredConfig().setClusterWidth(widthPercent, true);
        else if (getMode() == SUPERIMPOSED)
            getModeConfig().updateChildren();
    }

    /// Enables or disables automatic transparency for superimposed bars.
    ///
    /// The flag matters only when the parent renderer is in `SUPERIMPOSED` mode and child bars are
    /// still using auto-generated styles. Toggling it forces those child defaults to be regenerated.
    @Override
    public void setAutoTransparency(boolean autoTransparency) {
        if (autoTransparency != this.autoTransparency) {
            this.autoTransparency = autoTransparency;
            super.refreshChildAutoStyles();
        }
    }

    /// Propagates setStacked100Percent bar shape constant to every current and future child bar renderer.
    ///
    /// @param barShape one of the `SingleBarRenderer.SHAPE_*` constants
    public void setBarShape(int barShape) {
        validateBarShape(barShape);
        if (barShape == this.barShape)
            return;

        this.barShape = barShape;
        for (int childIndex = 0; childIndex < super.getChildCount(); childIndex++)
            getBarRendererAt(childIndex).setBarShape(barShape);
    }

    /// Updates the shared category footprint when the renderer is currently clustered.
    ///
    /// Calls made in other modes are ignored.
    public final void setClusterWidth(double widthPercent) {
        if (getMode() == CLUSTERED)
            setWidthPercent(widthPercent);
    }

    /// Enables or disables diverging stacked bars.
    ///
    /// The setting is forwarded immediately only in `STACKED` mode.
    public void setDiverging(boolean diverging) {
        if (diverging != this.diverging) {
            this.diverging = diverging;
            if (getMode() == STACKED) {
                getStackedConfig().setDiverging(diverging);
                super.triggerChange(4);
            }
        }
    }

    /// Switches the layout strategy used for all current and future child bars.
    ///
    /// Changing the mode rebuilds the internal mode configuration, reapplies the current width
    /// policy, and reconnects any stacked virtual datasets. When automatic transparency is enabled,
    /// entering or leaving `SUPERIMPOSED` mode also refreshes child auto styles.
    ///
    /// @param mode one of `SUPERIMPOSED`, `CLUSTERED`, or `STACKED`
    public void setMode(int mode) {
        if (mode == this.mode)
            return;

        validateMode(mode);
        if (modeConfig != null)
            modeConfig.deactivate();

        int previousMode = this.mode;
        this.mode = mode;
        rebuildModeConfig();
        updateBarWidths();
        modeConfig.activate();

        if ((mode == SUPERIMPOSED || previousMode == SUPERIMPOSED) && autoTransparency)
            super.refreshChildAutoStyles();

        super.triggerChange(4);
    }

    /// Sets the overlap percentage between adjacent clustered child bars.
    ///
    /// The setting is stored in every mode but only affects layout while the renderer is in
    /// `CLUSTERED` mode.
    public void setOverlap(double overlap) {
        if (overlap != this.overlap) {
            this.overlap = overlap;
            if (getMode() == CLUSTERED) {
                getClusteredConfig().setOverlap(overlap, true);
                super.triggerChange(4);
            }
        }
    }

    /// Enables or disables `100%` normalization for stacked bars.
    ///
    /// The setting is forwarded immediately only in `STACKED` mode.
    public void setStacked100Percent(boolean stacked100Percent) {
        if (stacked100Percent != this.stacked100Percent) {
            this.stacked100Percent = stacked100Percent;
            if (getMode() == STACKED) {
                getStackedConfig().setStacked100Percent(stacked100Percent);
                super.triggerChange(4);
            }
        }
    }

    /// Switches stacked bars between x-based and index-based stacking.
    ///
    /// The setting is forwarded immediately only in `STACKED` mode.
    public void setStackedByIndex(boolean stackedByIndex) {
        if (stackedByIndex != this.stackedByIndex) {
            this.stackedByIndex = stackedByIndex;
            if (getMode() == STACKED) {
                getStackedConfig().setStackedByIndex(stackedByIndex);
                super.triggerChange(4);
            }
        }
    }

    /// Propagates the category-border spacing policy to every current child bar renderer.
    @Override
    public void setUseCategorySpacingAtBorders(boolean useCategorySpacingAtBorders) {
        if (useCategorySpacingAtBorders != this.useCategorySpacingAtBorders) {
            this.useCategorySpacingAtBorders = useCategorySpacingAtBorders;
            for (int childIndex = 0; childIndex < super.getChildCount(); childIndex++)
                getBarRendererAt(childIndex).setUseCategorySpacingAtBorders(useCategorySpacingAtBorders);
        }
    }

    /// Updates the shared category footprint used when child bars are laid out.
    @Override
    public void setWidthPercent(double widthPercent) {
        if (widthPercent != this.widthPercent) {
            this.widthPercent = widthPercent;
            updateBarWidths();
        }
    }

    /// Reapplies the current mode layout and updates each child's dataset index.
    @Override
    protected void updateChildren() {
        super.updateChildren();
        getModeConfig().updateChildren();
        for (int childIndex = 0; childIndex < super.getChildCount(); childIndex++)
            getBarRendererAt(childIndex).setChildIndex(childIndex);
    }
}

