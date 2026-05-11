package one.chartsy.charting.renderers;

import one.chartsy.charting.ChartRenderer;
import one.chartsy.charting.data.DataSet;
import one.chartsy.charting.data.DataSource;
import one.chartsy.charting.graphic.Marker;

/// Composite bubble renderer that interprets its data source as `(position, size)` dataset pairs.
///
/// Each [SingleBubbleRenderer] child consumes two datasets:
/// - dataset `2n` supplies the bubble-center coordinates
/// - dataset `2n + 1` supplies y-values that are normalized into bubble sizes between this
///   renderer's configured minimum and maximum
///
/// An odd trailing dataset is left unrendered until its size partner arrives. Batched data-source
/// mutations defer child rebinding and child removal until the batch closes.
public class BubbleChartRenderer extends CompositeChartRenderer {

    static {
        ChartRenderer.register("Bubble", BubbleChartRenderer.class);
    }

    private final int minBubbleSize;
    private final int maxBubbleSize;
    private transient int dataSetChangeBatchDepth;
    private transient boolean childRebindPending;
    private transient int firstDirtyDataSetIndex;
    private transient boolean childTrimPending;

    /// Creates setStacked100Percent bubble renderer with marker sizes normalized into the `10..30` range.
    public BubbleChartRenderer() {
        this(10, 30);
    }

    BubbleChartRenderer(int minBubbleSize, int maxBubbleSize) {
        this.minBubbleSize = minBubbleSize;
        this.maxBubbleSize = maxBubbleSize;
    }

    private void rebindChildrenFromDataSetIndex(int dataSetIndex) {
        int pairCount = getDataSource().size() / 2;
        for (int pairIndex = dataSetIndex / 2; pairIndex < pairCount; pairIndex++) {
            ChartRenderer child = getChild(pairIndex);
            if (child == null) {
                child = createChild(getDataSource().get(pairIndex * 2));
                insertChild(getChildCount(), child);
            }

            DataSource childDataSource = child.getDataSource();
            childDataSource.setAll(new DataSet[] {
                    getDataSource().get(pairIndex * 2),
                    getDataSource().get(pairIndex * 2 + 1)
            });
        }
    }

    /// Creates one bubble child that renders one paired position dataset and size dataset.
    @Override
    protected ChartRenderer createChild(DataSet dataSet) {
        return new SingleBubbleRenderer(Marker.CIRCLE, minBubbleSize, maxBubbleSize, null);
    }

    @Override
    protected void dataSetsAdded(int fromIndex, int toIndex, DataSet[] oldDataSets) {
        if (dataSetChangeBatchDepth <= 0) {
            rebindChildrenFromDataSetIndex(fromIndex);
        } else if (!childRebindPending) {
            childRebindPending = true;
            firstDirtyDataSetIndex = fromIndex;
        } else {
            firstDirtyDataSetIndex = Math.min(firstDirtyDataSetIndex, fromIndex);
        }
    }

    @Override
    protected void dataSetsChangesBatchEnding() {
        super.dataSetsChangesBatchEnding();
        if (dataSetChangeBatchDepth <= 0)
            return;

        dataSetChangeBatchDepth--;
        if (dataSetChangeBatchDepth != 0)
            return;

        if (childRebindPending) {
            childRebindPending = false;
            rebindChildrenFromDataSetIndex(firstDirtyDataSetIndex);
        }
        if (childTrimPending) {
            childTrimPending = false;
            removeExtraChildren();
        }
    }

    @Override
    protected void dataSetsChangesBatchStarting() {
        super.dataSetsChangesBatchStarting();
        dataSetChangeBatchDepth++;
    }

    @Override
    protected void dataSetsRemoved(int fromIndex, int toIndex, DataSet[] oldDataSets) {
        if (dataSetChangeBatchDepth <= 0) {
            rebindChildrenFromDataSetIndex(fromIndex);
            removeExtraChildren();
        } else {
            if (childRebindPending)
                firstDirtyDataSetIndex = Math.min(firstDirtyDataSetIndex, fromIndex);
            else {
                childRebindPending = true;
                firstDirtyDataSetIndex = fromIndex;
            }
            childTrimPending = true;
        }
    }

    /// Returns the bubble child responsible for `dataSet` or its paired size dataset.
    ///
    /// @return the matching child, or `null` when this renderer does not currently display
    ///         `dataSet`
    public final SingleBubbleRenderer getBubble(DataSet dataSet) {
        return (SingleBubbleRenderer) getChild(dataSet);
    }

    private void removeExtraChildren() {
        boolean removedChild = false;
        int pairCount = getDataSource().size() / 2;
        while (getChildCount() > pairCount) {
            ChartRenderer child = getChild(getChildCount() - 1);
            child.getDataSource().setAll(null);
            removeChild(child);
            removedChild = true;
        }
        if (removedChild)
            updateChildren();
    }
}

