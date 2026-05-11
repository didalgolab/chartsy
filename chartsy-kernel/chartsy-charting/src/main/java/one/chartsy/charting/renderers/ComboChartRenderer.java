package one.chartsy.charting.renderers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import one.chartsy.charting.ChartRenderer;
import one.chartsy.charting.LegendEntry;
import one.chartsy.charting.data.DataSet;
import one.chartsy.charting.data.DataSource;
import one.chartsy.charting.graphic.DataAnnotation;
import one.chartsy.charting.graphic.DataRenderingHint;

/// Composite renderer that groups datasets by per-dataset renderer type and delegates each group
/// to an appropriate child renderer created by [ChartRenderer#createRenderer(int)].
///
/// Use [#setType(DataSet, int)] to tag a dataset before it is added. Datasets without that marker
/// default to `ChartRenderer.POLYLINE`. Datasets that share the same renderer type are routed into
/// one composite child renderer, while [#getSingleChild(DataSet)] exposes the leaf renderer that
/// actually paints a particular dataset.
///
/// Legend creation intentionally follows the parent data-source order instead of the grouped child
/// order so mixed renderer combinations keep a stable legend sequence.
public class ComboChartRenderer extends CompositeChartRenderer {

    static final String RENDERING_TYPE_PROPERTY = "__ComboRenderingType__";

    private final Map<Integer, ChartRenderer> childrenByRendererType;
    private transient int dataSetChangeBatchDepth;
    private transient boolean rebuildPending;

    public ComboChartRenderer() {
        childrenByRendererType = new HashMap<>();
    }

    /// Returns the renderer type marker previously stored for `dataSet`.
    ///
    /// Datasets without an explicit marker default to `ChartRenderer.POLYLINE`.
    public static int getType(DataSet dataSet) {
        Integer rendererType = (Integer) dataSet.getProperty(RENDERING_TYPE_PROPERTY);
        return (rendererType != null) ? rendererType : ChartRenderer.POLYLINE;
    }

    /// Stores the renderer type that [ComboChartRenderer] should use for `dataSet`.
    ///
    /// The marker is stored as a dataset property and is read the next time this renderer rebuilds
    /// its grouped children.
    public static void setType(DataSet dataSet, int rendererType) {
        dataSet.putProperty(RENDERING_TYPE_PROPERTY, Integer.valueOf(rendererType), false);
    }

    /// Creates the grouped child renderer responsible for `dataSet`'s declared renderer type.
    @Override
    protected ChartRenderer createChild(DataSet dataSet) {
        return ChartRenderer.createRenderer(getType(dataSet));
    }

    /// Creates legend entries in the parent data-source order.
    ///
    /// Grouped children are organized by renderer type, but legend consumers typically expect the
    /// legend to track dataset order instead. This override therefore resolves the leaf child for
    /// each dataset and asks that child for its entries.
    @Override
    protected Iterable<LegendEntry> createLegendEntries() {
        if (!super.isViewable())
            return Collections.emptyList();

        List<LegendEntry> legendEntries = new ArrayList<>();
        DataSource dataSource = super.getDataSource();
        for (int dataSetIndex = 0; dataSetIndex < dataSource.size(); dataSetIndex++) {
            ChartRenderer child = getSingleChild(dataSource.get(dataSetIndex));
            if (child != null)
                child.getLegendEntryProvider().createLegendEntries().forEach(legendEntries::add);
        }
        return legendEntries;
    }

    @Override
    protected void dataSetsAdded(int fromIndex, int toIndex, DataSet[] oldDataSets) {
        if (dataSetChangeBatchDepth <= 0)
            rebuildChildrenByType();
        else
            rebuildPending = true;
    }

    @Override
    protected void dataSetsChangesBatchEnding() {
        super.dataSetsChangesBatchEnding();
        if (dataSetChangeBatchDepth <= 0)
            return;

        dataSetChangeBatchDepth--;
        if (dataSetChangeBatchDepth == 0 && rebuildPending) {
            rebuildPending = false;
            rebuildChildrenByType();
        }
    }

    @Override
    protected void dataSetsChangesBatchStarting() {
        super.dataSetsChangesBatchStarting();
        dataSetChangeBatchDepth++;
    }

    @Override
    protected void dataSetsRemoved(int fromIndex, int toIndex, DataSet[] oldDataSets) {
        if (dataSetChangeBatchDepth <= 0)
            rebuildChildrenByType();
        else
            rebuildPending = true;
    }

    /// Returns the leaf renderer currently responsible for `dataSet`.
    ///
    /// The returned renderer is the dataset-specific child nested under the grouped renderer-type
    /// bucket, not the top-level grouped child itself.
    ///
    /// @return the leaf renderer for `dataSet`, or `null` when the dataset is not currently shown
    public ChartRenderer getSingleChild(DataSet dataSet) {
        for (int childIndex = 0; childIndex < super.getChildCount(); childIndex++) {
            ChartRenderer child = ((CompositeChartRenderer) super.getChild(childIndex)).getChild(dataSet);
            if (child != null)
                return child;
        }
        return null;
    }

    private void rebuildChildrenByType() {
        DataSource dataSource = super.getDataSource();

        while (super.getChildCount() > 0) {
            ChartRenderer child = super.getChild(super.getChildCount() - 1);
            child.getDataSource().setAll(null);
            super.removeChild(child);
        }

        childrenByRendererType.clear();
        int childIndex = 0;
        for (int dataSetIndex = 0; dataSetIndex < dataSource.size(); dataSetIndex++) {
            DataSet dataSet = dataSource.get(dataSetIndex);
            Integer rendererType = (Integer) dataSet.getProperty(RENDERING_TYPE_PROPERTY);
            if (rendererType == null)
                rendererType = ChartRenderer.POLYLINE;

            ChartRenderer child = childrenByRendererType.get(rendererType);
            if (child == null) {
                child = createChild(dataSet);
                childrenByRendererType.put(rendererType, child);
                super.insertChild(childIndex++, child);
            }
            child.getDataSource().add(dataSet);
        }
    }

    /// Propagates one annotation object to every grouped child renderer.
    ///
    /// When multiple grouped children exist, the surrounding chart renderer-change batch keeps the
    /// chart from reacting once per child update.
    @Override
    public void setAnnotation(DataAnnotation annotation) {
        int childCount = super.getChildCount();
        if (childCount > 1 && super.getChart() != null)
            super.getChart().startRendererChanges();

        try {
            for (int childIndex = 0; childIndex < childCount; childIndex++)
                super.getChild(childIndex).setAnnotation(annotation);
        } finally {
            if (childCount > 1 && super.getChart() != null)
                super.getChart().endRendererChanges();
        }
    }

    /// Propagates one rendering hint object to every grouped child renderer.
    @Override
    public void setRenderingHint(DataRenderingHint renderingHint) {
        for (int childIndex = 0; childIndex < super.getChildCount(); childIndex++)
            super.getChild(childIndex).setRenderingHint(renderingHint);
    }

    /// Gives each grouped child renderer a chance to refresh its own nested children.
    @Override
    protected void updateChildren() {
        for (int childIndex = 0; childIndex < super.getChildCount(); childIndex++)
            ((CompositeChartRenderer) super.getChild(childIndex)).updateChildren();
    }
}

