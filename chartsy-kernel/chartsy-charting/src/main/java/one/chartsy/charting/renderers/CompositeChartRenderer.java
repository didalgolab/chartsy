package one.chartsy.charting.renderers;

import java.awt.Graphics;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

import one.chartsy.charting.ChartDataPicker;
import one.chartsy.charting.ChartRenderer;
import one.chartsy.charting.DataInterval;
import one.chartsy.charting.DisplayPoint;
import one.chartsy.charting.LegendEntry;
import one.chartsy.charting.PlotStyle;
import one.chartsy.charting.data.DataSet;
import one.chartsy.charting.data.DataSource;
import one.chartsy.charting.graphic.DataAnnotation;
import one.chartsy.charting.graphic.DataRenderingHint;
import one.chartsy.charting.util.GraphicUtil;

/// Base class for renderers that own other [ChartRenderer] instances and combine their output into
/// one logical renderer.
///
/// A composite renderer keeps an ordered child list and delegates painting, hit testing, bounds,
/// range calculation, annotations, and rendering hints to the child that actually displays each
/// dataset. Subclasses decide how children are created through [#createChild(DataSet)] and may
/// recompute child wiring in [#updateChildren()].
///
/// The default layering model paints children in insertion order and performs data picking in the
/// reverse order so the visually top-most child wins hit tests first. Subclasses can override
/// [#getChildPaintOrderSign()] to invert that direction.
///
/// Calling [#setStyles(PlotStyle[])] assigns at most one style per child, matched by child index.
/// Passing `null` clears those explicit assignments and lets child renderers fall back to their own
/// default-style logic.
///
/// Composite renderers are mutable UI objects and are not thread-safe.
public abstract class CompositeChartRenderer extends ChartRenderer {

    private static <T> Iterator<T> reversedIterator(ListIterator<T> iterator) {
        return new Iterator<>() {
            @Override
            public boolean hasNext() {
                return iterator.hasPrevious();
            }

            @Override
            public T next() {
                return iterator.previous();
            }
        };
    }

    private final ArrayList<ChartRenderer> children;
    private PlotStyle[] childStyles;

    protected CompositeChartRenderer() {
        children = new ArrayList<>();
    }

    /// Adds `child` at `index`, or appends it when `index` is `-1`.
    ///
    /// If the child does not already define styles and this composite currently owns explicit
    /// child styles, the style at the inserted index is propagated to the new child.
    void insertChild(int index, ChartRenderer child) {
        int childIndex = (index >= 0) ? index : children.size();
        if (index >= 0)
            children.add(index, child);
        else
            children.add(child);

        PlotStyle[] childOwnStyles = child.getStyles();
        if ((childOwnStyles == null || childOwnStyles.length == 0) && childStyles != null && childIndex < childStyles.length)
            child.setStyles(new PlotStyle[]{childStyles[childIndex]});

        connectChild(child);
        updateChildren();
    }

    /// Replaces the child at `index` and preserves the previous child's data source.
    void replaceChild(int index, ChartRenderer child) {
        ChartRenderer previousChild = getChild(index);
        if (child == previousChild)
            return;

        if (previousChild != null)
            child.setDataSource(previousChild.getDataSource());

        children.set(index, child);
        connectChild(child);
        if (previousChild != null)
            disconnectChild(previousChild);
        updateChildren();
    }

    /// Returns the zero-based child index of `child`, or `-1` when it is not attached here.
    int indexOfChild(ChartRenderer child) {
        return children.indexOf(child);
    }

    @Override
    public void collectDisplayItems(ChartDataPicker picker, ArrayList<DisplayPoint> displayItems) {
        Iterator<ChartRenderer> iterator = childHitTestIterator();
        while (iterator.hasNext()) {
            ChartRenderer child = iterator.next();
            if (child.isViewable())
                child.collectDisplayItems(picker, displayItems);
        }
    }

    /// Creates the child renderer that should handle `dataSet`.
    ///
    /// Subclasses decide whether one child owns one dataset, one dataset pair, or a larger grouped
    /// view assembled from multiple datasets.
    protected abstract ChartRenderer createChild(DataSet dataSet);

    @Override
    protected Iterable<LegendEntry> createLegendEntries() {
        if (!isViewable())
            return Collections.emptyList();

        List<LegendEntry> legendEntries = new ArrayList<>();
        for (ChartRenderer child : getChildren())
            child.getLegendEntryProvider().createLegendEntries().forEach(legendEntries::add);
        return legendEntries;
    }

    /// Returns the sign that controls child paint order and hit-test order.
    ///
    /// A positive value means "paint in insertion order and hit-test in reverse order". A negative
    /// value flips that traversal.
    int getChildPaintOrderSign() {
        return 1;
    }

    /// Removes `child` from this composite and disconnects it from the parent chain.
    void removeChild(ChartRenderer child) {
        children.remove(child);
        disconnectChild(child);
    }

    @Override
    public void draw(Graphics g) {
        Iterator<ChartRenderer> iterator = childPaintIterator();
        while (iterator.hasNext()) {
            ChartRenderer child = iterator.next();
            if (child.isViewable())
                child.draw(g);
        }
    }

    @Override
    public void drawAnnotations(Graphics g) {
        Iterator<ChartRenderer> iterator = childPaintIterator();
        while (iterator.hasNext()) {
            ChartRenderer child = iterator.next();
            if (child.isViewable())
                child.drawAnnotations(g);
        }
    }

    @Override
    public void drawLegendMarker(LegendEntry legend, Graphics g, int x, int y, int w, int h) {
    }

    /// Returns whether explicit per-child styles are currently absent.
    boolean usesAutomaticChildStyles() {
        return childStyles == null;
    }

    private void connectChild(ChartRenderer child) {
        if (child.getChart() != null)
            throw new IllegalArgumentException("renderer already added to a chart");
        child.setParent(this);
    }

    /// Clears auto-generated child styles recursively so they can be recreated from current state.
    void refreshChildAutoStyles() {
        for (int childIndex = 0; childIndex < getChildCount(); childIndex++) {
            ChartRenderer child = getChild(childIndex);
            if (child instanceof SingleChartRenderer singleChild) {
                singleChild.refreshAutoStyle();
            } else if (child instanceof CompositeChartRenderer compositeChild) {
                compositeChild.refreshChildAutoStyles();
            } else if (child instanceof SimpleChartRenderer simpleChild) {
                simpleChild.refreshAutoStyle();
            }
        }
    }

    private void disconnectChild(ChartRenderer child) {
        if (child.getParent() != this)
            throw new IllegalArgumentException("renderer not attached to this composite");
        if (child.getChart() != super.getChart())
            throw new IllegalArgumentException("child renderer not connected to same chart");
        child.setParent(null);
    }

    /// Removes all child references without touching the current child instances.
    void clearChildReferences() {
        children.clear();
    }

    @Override
    public DataAnnotation getAnnotation(DataSet dataSet, int itemIndex) {
        ChartRenderer child = getChild(dataSet);
        return (child != null) ? child.getAnnotation(dataSet, itemIndex) : null;
    }

    @Override
    public Rectangle2D getBounds(DataSet dataSet, int fromIndex, int toIndex, Rectangle2D bounds,
                                 boolean includeAnnotationBounds) {
        Rectangle2D result = (bounds != null) ? bounds : new Rectangle2D.Double();
        result.setRect(0.0, 0.0, 0.0, 0.0);

        Rectangle2D childBounds = null;
        for (int childIndex = 0; childIndex < getChildCount(); childIndex++) {
            ChartRenderer child = getChild(childIndex);
            if (child.isViewable() && child.isDisplayingDataSet(dataSet)) {
                childBounds = child.getBounds(dataSet, fromIndex, toIndex, childBounds, includeAnnotationBounds);
                GraphicUtil.addToRect(result, childBounds);
            }
        }
        return result;
    }

    @Override
    public Rectangle2D getBounds(Rectangle2D bounds, boolean includeAnnotationBounds) {
        Rectangle2D result = (bounds != null) ? bounds : new Rectangle2D.Double();
        result.setRect(0.0, 0.0, 0.0, 0.0);

        Rectangle2D childBounds = null;
        for (int childIndex = 0; childIndex < getChildCount(); childIndex++) {
            ChartRenderer child = getChild(childIndex);
            if (child.isViewable()) {
                childBounds = child.getBounds(childBounds, includeAnnotationBounds);
                GraphicUtil.addToRect(result, childBounds);
            }
        }
        return (Rectangle2D) result.clone();
    }

    /// Returns the child currently responsible for `dataSet`.
    ///
    /// @return the matching child, or `null` when no child currently displays `dataSet`
    public ChartRenderer getChild(DataSet dataSet) {
        for (int childIndex = 0; childIndex < getChildCount(); childIndex++) {
            ChartRenderer child = getChild(childIndex);
            if (child.isDisplayingDataSet(dataSet))
                return child;
        }
        return null;
    }

    @Override
    public ChartRenderer getChild(int childIndex) {
        return (childIndex < 0 || childIndex >= children.size()) ? null : children.get(childIndex);
    }

    /// Returns the child that currently displays the first dataset whose name equals `name`.
    ///
    /// @return the matching child, or `null` when no dataset with that name is present
    public final ChartRenderer getChild(String name) {
        if (name == null)
            return null;

        DataSource dataSource = super.getDataSource();
        for (int dataSetIndex = 0; dataSetIndex < dataSource.size(); dataSetIndex++) {
            String dataSetName = dataSource.get(dataSetIndex).getName();
            if (dataSetName != null && dataSetName.equals(name))
                return getChild(dataSource.get(dataSetIndex));
        }
        return null;
    }

    @Override
    public int getChildCount() {
        return children.size();
    }

    @Override
    public Iterator<ChartRenderer> getChildIterator() {
        return Collections.unmodifiableList(children).iterator();
    }

    /// Returns a shallow copy of the current child list.
    @Override
    public List<ChartRenderer> getChildren() {
        return new ArrayList<>(children);
    }

    @Override
    public DisplayPoint getDisplayItem(ChartDataPicker picker) {
        return ChartRenderer.getDisplayItem(childHitTestIterator(), picker);
    }

    @Override
    public DisplayPoint getDisplayPoint(DataSet dataSet, int itemIndex) {
        ChartRenderer child = getChild(dataSet);
        return (child != null) ? child.getDisplayPoint(dataSet, itemIndex) : null;
    }

    @Override
    public String getLegendText(LegendEntry legend) {
        return "";
    }

    @Override
    public DisplayPoint getNearestItem(ChartDataPicker picker, double[] distanceHolder) {
        return ChartRenderer.getNearestItem(childHitTestIterator(), picker, distanceHolder);
    }

    @Override
    public DisplayPoint getNearestPoint(ChartDataPicker picker) {
        return ChartRenderer.getNearestPoint(childHitTestIterator(), picker);
    }

    @Override
    public DataRenderingHint getRenderingHint(DataSet dataSet) {
        ChartRenderer child = getChild(dataSet);
        return (child != null) ? child.getRenderingHint(dataSet) : null;
    }

    @Override
    public DataRenderingHint getRenderingHint(DataSet dataSet, int itemIndex) {
        ChartRenderer child = getChild(dataSet);
        return (child != null) ? child.getRenderingHint(dataSet, itemIndex) : null;
    }

    @Override
    public PlotStyle getStyle(DataSet dataSet, int itemIndex) {
        ChartRenderer child = getChild(dataSet);
        return (child != null) ? child.getStyle(dataSet, itemIndex) : null;
    }

    @Override
    public PlotStyle[] getStyles() {
        ArrayList<PlotStyle> styles = new ArrayList<>();
        for (int childIndex = 0; childIndex < getChildCount(); childIndex++) {
            PlotStyle[] childOwnStyles = getChild(childIndex).getStyles();
            Collections.addAll(styles, childOwnStyles);
        }
        return styles.toArray(new PlotStyle[0]);
    }

    @Override
    public DataInterval getXRange(DataInterval range) {
        DataInterval result = (range != null) ? range : new DataInterval();
        result.empty();

        DataInterval childRange = null;
        for (int childIndex = 0; childIndex < getChildCount(); childIndex++) {
            ChartRenderer child = getChild(childIndex);
            if (child.isViewable()) {
                childRange = child.getXRange(childRange);
                result.add(childRange);
            }
        }
        return result;
    }

    @Override
    public DataInterval getYRange(DataInterval range) {
        DataInterval result = (range != null) ? range : new DataInterval();
        result.empty();

        DataInterval childRange = null;
        for (int childIndex = 0; childIndex < getChildCount(); childIndex++) {
            ChartRenderer child = getChild(childIndex);
            if (child.isViewable()) {
                childRange = child.getYRange(childRange);
                result.add(childRange);
            }
        }
        return result;
    }

    @Override
    public DataInterval getYRange(DataInterval xRange, DataInterval range) {
        DataInterval result = (range != null) ? range : new DataInterval();
        result.empty();

        DataInterval childRange = null;
        for (int childIndex = 0; childIndex < getChildCount(); childIndex++) {
            ChartRenderer child = getChild(childIndex);
            if (child.isViewable()) {
                childRange = child.getYRange(xRange, childRange);
                result.add(childRange);
            }
        }
        return result;
    }

    Iterator<ChartRenderer> childPaintIterator() {
        return (getChildPaintOrderSign() < 0)
                ? reversedIterator(children.listIterator(children.size()))
                : children.iterator();
    }

    @Override
    public boolean holdsAnnotations() {
        for (int childIndex = 0; childIndex < getChildCount(); childIndex++) {
            if (getChild(childIndex).holdsAnnotations())
                return true;
        }
        return false;
    }

    private Iterator<ChartRenderer> childHitTestIterator() {
        return (getChildPaintOrderSign() < 0)
                ? children.iterator()
                : reversedIterator(children.listIterator(children.size()));
    }

    @Override
    public boolean isViewable() {
        if (!super.isVisible())
            return false;

        for (int childIndex = 0; childIndex < getChildCount(); childIndex++) {
            if (getChild(childIndex).isViewable())
                return true;
        }
        return false;
    }

    @Override
    public void setAnnotation(DataSet dataSet, DataAnnotation annotation) {
        ChartRenderer child = getChild(dataSet);
        if (child != null)
            child.setAnnotation(dataSet, annotation);
    }

    @Override
    public void setAnnotation(DataSet dataSet, int itemIndex, DataAnnotation annotation) {
        ChartRenderer child = getChild(dataSet);
        if (child != null)
            child.setAnnotation(dataSet, itemIndex, annotation);
    }

    @Override
    public void setDataPoint(DataSet dataSet, int itemIndex, double x, double y) {
        ChartRenderer child = getChild(dataSet);
        if (child != null)
            child.setDataPoint(dataSet, itemIndex, x, y);
    }

    @Override
    public void setDisplayPoint(DataSet dataSet, int itemIndex, double x, double y) {
        ChartRenderer child = getChild(dataSet);
        if (child != null)
            child.setDisplayPoint(dataSet, itemIndex, x, y);
    }

    @Override
    public void setRenderingHint(DataSet dataSet, DataRenderingHint renderingHint) {
        ChartRenderer child = getChild(dataSet);
        if (child != null)
            child.setRenderingHint(dataSet, renderingHint);
    }

    @Override
    public void setRenderingHint(DataSet dataSet, int itemIndex, DataRenderingHint renderingHint) {
        ChartRenderer child = getChild(dataSet);
        if (child != null)
            child.setRenderingHint(dataSet, itemIndex, renderingHint);
    }

    /// Assigns at most one explicit style per child renderer.
    ///
    /// Styles are matched positionally to the current child list. Passing `null` clears any
    /// previously propagated child styles and asks each child to recreate its own defaults.
    @Override
    public void setStyles(PlotStyle[] styles) {
        if (styles == null) {
            for (int childIndex = 0; childIndex < getChildCount(); childIndex++)
                getChild(childIndex).setStyles(null);
        } else {
            int styledChildCount = Math.min(styles.length, getChildCount());
            for (int childIndex = 0; childIndex < styledChildCount; childIndex++)
                getChild(childIndex).setStyles(new PlotStyle[]{styles[childIndex]});
        }
        childStyles = styles;
    }

    /// Lets subclasses refresh child wiring, layout, or grouping after the child set changes.
    protected void updateChildren() {
    }
}

