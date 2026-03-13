package one.chartsy.charting.renderers;

import one.chartsy.charting.ChartRenderer;
import one.chartsy.charting.data.DataSet;
import one.chartsy.charting.graphic.Marker;

/// Composite renderer that creates one [SinglePolylineRenderer] child per dataset and can draw the
/// children either as independent polylines or as one stacked polyline family.
///
/// In `SUPERIMPOSED` mode each child keeps its original dataset and shares the same category slot.
/// In `STACKED` mode the parent replaces each child dataset with stacked virtual data managed by
/// [StackedRendererConfig], so range calculations, picking, and painting all reflect the stacked
/// values instead of the original series.
///
/// Marker settings are copied into newly created children. The same mode contract is also reused by
/// filled subclasses such as [AreaChartRenderer], which is why this base type exposes the
/// [SuperimposedRenderer] auto-transparency flag even though the default polyline child is not
/// filled.
public class PolylineChartRenderer extends SimpleCompositeChartRenderer implements SuperimposedRenderer {
    /// Mode that draws every dataset against its own values on the same category positions.
    public static final int SUPERIMPOSED = 1;
    /// Mode that swaps child datasets for stacked values derived from all visible datasets.
    public static final int STACKED = 2;

    static {
        ChartRenderer.register("Polyline", PolylineChartRenderer.class);
    }

    private CompositeRendererConfig modeConfig;
    private int mode;
    private Marker marker;
    private int markerSize;
    private boolean stacked100Percent;
    private boolean stackedByIndex;
    private boolean autoTransparency;

    /// Creates setStacked100Percent superimposed polyline renderer.
    public PolylineChartRenderer() {
        this(SUPERIMPOSED);
    }

    /// Creates setStacked100Percent polyline renderer with an explicit layout mode.
    ///
    /// @param mode one of `SUPERIMPOSED` or `STACKED`
    public PolylineChartRenderer(int mode) {
        setMode(mode);
    }

    void validateMode(int mode) {
        switch (mode) {
            case SUPERIMPOSED, STACKED -> {
            }
            default -> throw new IllegalArgumentException("Invalid mode: " + mode);
        }
    }

    /// Creates the single-dataset polyline child for `dataSet`.
    ///
    /// New children inherit the current marker template and marker size.
    @Override
    protected ChartRenderer createChild(DataSet dataSet) {
        SinglePolylineRenderer child = new SinglePolylineRenderer();
        if (marker != null) {
            child.setMarker(marker);
            child.setMarkerSize(markerSize);
        }
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

    /// Lets the active mode configuration react to removed datasets.
    @Override
    protected void dataSetsRemoved(int fromIndex, int toIndex, DataSet[] oldDataSets) {
        super.dataSetsRemoved(fromIndex, toIndex, oldDataSets);

        DataSet[] removedDataSets = new DataSet[toIndex - fromIndex + 1];
        System.arraycopy(oldDataSets, fromIndex, removedDataSets, 0, toIndex - fromIndex + 1);
        getModeConfig().dataSetsRemoved(removedDataSets);
    }

    /// Returns the marker template copied into newly created child polylines.
    public final Marker getMarker() {
        return marker;
    }

    /// Returns the marker size copied into newly created child polylines.
    public final int getMarkerSize() {
        return markerSize;
    }

    /// Returns the current polyline layout mode.
    public final int getMode() {
        return mode;
    }

    /// Returns the child polyline currently responsible for `dataSet`.
    ///
    /// @return the matching child, or `null` when this renderer does not currently display
    ///         `dataSet`
    public SinglePolylineRenderer getPolyline(DataSet dataSet) {
        return (SinglePolylineRenderer) super.getChild(dataSet);
    }

    /// Returns the child polyline currently responsible for the dataset at `dataSetIndex`.
    public SinglePolylineRenderer getPolyline(int dataSetIndex) {
        return (SinglePolylineRenderer) super.getChild(super.getDataSource().get(dataSetIndex));
    }

    CompositeRendererConfig createModeConfig() {
        return switch (getMode()) {
            case STACKED -> new StackedRendererConfig(this, stacked100Percent, stackedByIndex, false);
            default -> new CompositeRendererConfig(this);
        };
    }

    @Override
    public boolean isAutoTransparency() {
        return autoTransparency;
    }

    /// Returns whether stacked mode currently normalizes each stack to `100%`.
    public boolean isStacked100Percent() {
        return stacked100Percent;
    }

    /// Returns whether stacked mode currently groups series by item index instead of x value.
    public boolean isStackedByIndex() {
        return stackedByIndex;
    }

    @Override
    public boolean isSuperimposed() {
        return mode == SUPERIMPOSED;
    }

    private void rebuildModeConfig() {
        if (modeConfig != null)
            modeConfig.deactivate();
        modeConfig = createModeConfig();
        modeConfig.activate();
    }

    private final CompositeRendererConfig getModeConfig() {
        return modeConfig;
    }

    private final StackedRendererConfig getStackedConfig() {
        return (StackedRendererConfig) getModeConfig();
    }

    /// Enables or disables automatic transparency for filled subclasses that overlap in
    /// `SUPERIMPOSED` mode.
    @Override
    public void setAutoTransparency(boolean autoTransparency) {
        if (autoTransparency != this.autoTransparency) {
            this.autoTransparency = autoTransparency;
            super.refreshChildAutoStyles();
        }
    }

    /// Updates the marker template used for polyline children created after this call.
    ///
    /// Existing children are not rebuilt.
    public void setMarker(Marker marker) {
        this.marker = marker;
    }

    /// Updates the marker template and size used for polyline children created after this call.
    ///
    /// Existing children are not rebuilt.
    public void setMarker(Marker marker, int markerSize) {
        this.marker = marker;
        this.markerSize = markerSize;
    }

    /// Updates the marker size used for polyline children created after this call.
    ///
    /// Existing children are not rebuilt.
    public void setMarkerSize(int markerSize) {
        this.markerSize = markerSize;
    }

    /// Switches the renderer between superimposed and stacked dataset handling.
    ///
    /// Changing the mode rebuilds the internal mode configuration and reconnects any stacked virtual
    /// datasets. When automatic transparency is enabled, entering or leaving `SUPERIMPOSED` mode
    /// also refreshes child auto styles.
    ///
    /// @param mode one of `SUPERIMPOSED` or `STACKED`
    public void setMode(int mode) {
        if (mode == this.mode)
            return;

        validateMode(mode);
        int previousMode = this.mode;
        this.mode = mode;
        rebuildModeConfig();
        if ((mode == SUPERIMPOSED || previousMode == SUPERIMPOSED) && autoTransparency)
            super.refreshChildAutoStyles();

        super.triggerChange(4);
    }

    /// Enables or disables `100%` normalization for stacked polylines.
    ///
    /// The flag is remembered in every mode and is forwarded immediately only while the renderer is
    /// currently in `STACKED` mode.
    public void setStacked100Percent(boolean stacked100Percent) {
        if (stacked100Percent != this.stacked100Percent) {
            this.stacked100Percent = stacked100Percent;
            if (getMode() == STACKED) {
                getStackedConfig().setStacked100Percent(stacked100Percent);
                super.triggerChange(4);
            }
        }
    }

    /// Switches stacked polylines between x-based and index-based stacking.
    ///
    /// The flag is remembered in every mode and is forwarded immediately only while the renderer is
    /// currently in `STACKED` mode.
    public void setStackedByIndex(boolean stackedByIndex) {
        if (stackedByIndex != this.stackedByIndex) {
            this.stackedByIndex = stackedByIndex;
            if (getMode() == STACKED) {
                getStackedConfig().setStackedByIndex(stackedByIndex);
                super.triggerChange(4);
            }
        }
    }
}
