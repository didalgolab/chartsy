package one.chartsy.charting.renderers;

import java.awt.Graphics;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.StringJoiner;

import one.chartsy.charting.ChartRenderer;
import one.chartsy.charting.HiLoOpenCloseRendererLegendItem;
import one.chartsy.charting.LegendEntry;
import one.chartsy.charting.PlotStyle;
import one.chartsy.charting.data.DataSet;
import one.chartsy.charting.data.DataSource;
import one.chartsy.charting.renderers.SingleHiLoRenderer.Type;

/// Composite renderer for high/low series, candlesticks, and open-high-low-close glyphs.
///
/// The renderer consumes its datasets in fixed groups determined by [#getMode()]:
/// - [Mode#CLUSTERED] uses one [SingleHiLoRenderer] child per logical series and consumes one
///   dataset pair for that child
/// - [Mode#CANDLE] and [Mode#OPENCLOSE] use two children per logical series and consume two
///   dataset pairs: one for the shared high/low stroke and one for the candle body or open/close
///   ticks
///
/// Only complete dataset groups are materialized, so trailing incomplete pairs or quads stay
/// undisplayed until enough datasets are present. Width, overlap, border-spacing, and pixel-body
/// hints are propagated to current children and remembered for children created later. In OHLC
/// modes, legend rows are synthesized per logical series through
/// [HiLoOpenCloseRendererLegendItem] instead of exposing the two physical children separately.
public class HiLoChartRenderer extends CompositeChartRenderer implements VariableWidthRenderer {
    /// Declares how logical series are grouped into child renderers.
    public enum Mode {
        /// Uses one child per logical series and paints one dataset pair with the configured
        /// [SingleHiLoRenderer.Type].
        CLUSTERED,
        /// Uses two children per logical series and paints candle bodies for the second dataset
        /// pair.
        CANDLE,
        /// Uses two children per logical series and paints open/close ticks for the second dataset
        /// pair.
        OPENCLOSE;

        /// Returns whether this mode uses one child per logical series.
        boolean isClustered() {
            return this == CLUSTERED;
        }

        /// Returns whether this mode materializes paired hi/lo and open/close children.
        boolean isOhlc() {
            return this == CANDLE || this == OPENCLOSE;
        }
    }

    /// Legacy constant matching [Mode#CLUSTERED].
    public static final int CLUSTERED = 1;
    /// Legacy constant matching [Mode#CANDLE].
    public static final int CANDLE = 2;
    /// Legacy constant matching [Mode#OPENCLOSE].
    public static final int OPENCLOSE = 3;

    static {
        ChartRenderer.register("HiLo", HiLoChartRenderer.class);
    }

    private double configuredWidthPercent;
    private boolean useCategorySpacingAtBorders;
    private Type type;
    private Mode mode;
    private ClusteredRendererConfig clusteredConfig;
    private String[] seriesNames;
    private transient int dataSetChangeBatchDepth;
    private transient boolean refreshPending;
    private int pixelBodyWidthHint = -1;

    /// Creates setStacked100Percent clustered hi/lo renderer using bar glyphs and an `80%` width budget.
    public HiLoChartRenderer() {
        this(Mode.CLUSTERED, Type.BAR, 80.0);
    }

    /// Creates setStacked100Percent renderer with an explicit grouping mode, clustered glyph type, and width budget.
    ///
    /// `type` is used only while the renderer is in [Mode#CLUSTERED]. OHLC modes keep the value so
    /// it can be restored later when switching back to clustered mode.
    ///
    /// @param mode         child-grouping mode that defines how datasets are consumed
    /// @param type         glyph used by clustered child renderers
    /// @param widthPercent requested logical-series width as setStacked100Percent percentage of one category slot
    public HiLoChartRenderer(Mode mode, Type type, double widthPercent) {
        configuredWidthPercent = 80.0;
        validateMode(mode);
        useCategorySpacingAtBorders = false;
        this.mode = mode;
        if (mode.isClustered()) {
            ensureClusteredConfig();
            clusteredConfig.activate();
        }
        setType(type);
        setWidthPercent(widthPercent);
    }

    private static void validateMode(Mode mode) {
        if (mode == null)
            throw new NullPointerException("mode");
    }

    /// Binds the clustered child at `childIndex` to the matching dataset pair.
    private void bindClusteredChild(int childIndex) {
        if (!getMode().isClustered())
            return;

        SingleHiLoRenderer child = (SingleHiLoRenderer) super.getChild(childIndex);
        DataSource childDataSource = child.getDataSource();
        childDataSource.setAll(new DataSet[]{
                super.getDataSource().get(childIndex * 2),
                super.getDataSource().get(childIndex * 2 + 1)
        });
        child.setType(type);
    }

    /// Binds the OHLC child pair starting at `firstChildIndex` to one logical series.
    private void bindOhlcChildPair(int firstChildIndex) {
        bindOhlcChild(firstChildIndex, Type.STICK);
        bindOhlcChild(firstChildIndex + 1, getMode() == Mode.CANDLE ? Type.BAR : Type.MARKED);
    }

    private void bindOhlcChild(int childIndex, Type childType) {
        SingleHiLoRenderer child = (SingleHiLoRenderer) super.getChild(childIndex);
        DataSource childDataSource = child.getDataSource();
        childDataSource.setAll(new DataSet[]{
                super.getDataSource().get(childIndex * 2),
                super.getDataSource().get(childIndex * 2 + 1)
        });
        child.setType(childType);
    }

    /// Creates setStacked100Percent detached child renderer that inherits the current shared child settings.
    @Override
    protected ChartRenderer createChild(DataSet dataSet) {
        SingleHiLoRenderer child = new SingleHiLoRenderer(null, null, type, configuredWidthPercent);
        child.setUseCategorySpacingAtBorders(useCategorySpacingAtBorders);
        child.setPixelBodyWidthHint(pixelBodyWidthHint);
        return child;
    }

    /// Returns one legend entry per logical series.
    ///
    /// OHLC modes collapse the paired physical children into one composite legend row so candle
    /// bodies and wick/tick glyphs stay together.
    @Override
    protected Iterable<LegendEntry> createLegendEntries() {
        if (getMode().isClustered())
            return super.createLegendEntries();
        if (!isViewable() || !isLegended())
            return Collections.emptySet();

        int logicalSeriesCount = getDataSource().size() / 4;
        List<LegendEntry> legendEntries = new ArrayList<>(logicalSeriesCount);
        for (int logicalSeriesIndex = 0; logicalSeriesIndex < logicalSeriesCount; logicalSeriesIndex++) {
            SingleHiLoRenderer hiLoChild = (SingleHiLoRenderer) getChild(logicalSeriesIndex * 2);
            SingleHiLoRenderer openCloseChild = (SingleHiLoRenderer) getChild(logicalSeriesIndex * 2 + 1);
            legendEntries.add(new HiLoOpenCloseRendererLegendItem(this, hiLoChild, openCloseChild));
        }
        return legendEntries;
    }

    /// Defers setStacked100Percent full child regroup until the outermost dataset batch ends.
    @Override
    protected void dataSetsAdded(int fromIndex, int toIndex, DataSet[] oldDataSets) {
        if (dataSetChangeBatchDepth <= 0)
            refreshChildrenForMode();
        else
            refreshPending = true;
    }

    @Override
    protected void dataSetsChangesBatchEnding() {
        super.dataSetsChangesBatchEnding();
        if (dataSetChangeBatchDepth > 0 && --dataSetChangeBatchDepth == 0 && refreshPending) {
            refreshPending = false;
            refreshChildrenForMode();
        }
    }

    @Override
    protected void dataSetsChangesBatchStarting() {
        super.dataSetsChangesBatchStarting();
        dataSetChangeBatchDepth++;
    }

    /// Defers setStacked100Percent full child regroup until the outermost dataset batch ends.
    @Override
    protected void dataSetsRemoved(int fromIndex, int toIndex, DataSet[] oldDataSets) {
        if (dataSetChangeBatchDepth <= 0)
            refreshChildrenForMode();
        else
            refreshPending = true;
    }

    /// Draws one composite legend marker for OHLC logical series and otherwise delegates to every
    /// child marker.
    @Override
    public void drawLegendMarker(LegendEntry legend, Graphics g, int x, int y, int w, int h) {
        if (getMode().isOhlc() && legend instanceof HiLoOpenCloseRendererLegendItem item) {
            if (getMode() == Mode.OPENCLOSE) {
                item.getHiLoChildRenderer().drawLegendMarker(legend, g, x, y, w, h);
                item.getOpenCloseChildRenderer().drawLegendMarker(legend, g, x, y, w, h);
                return;
            }

            int markerSize = Math.min(w, h);
            int markerX = x + (w - markerSize) / 2;
            int markerY = y + (h - markerSize) / 2;
            SingleHiLoRenderer hiLoChild = item.getHiLoChildRenderer();
            hiLoChild.getLegendStyle().drawLine(g, markerX + markerSize / 2, markerY, markerX + markerSize / 2, markerY + h);

            SingleHiLoRenderer openCloseChild = item.getOpenCloseChildRenderer();
            openCloseChild.getRiseStyle().plotRect(g, markerX, (int) (markerY + 0.2 * h), markerSize, (int) (h * 0.4));
            openCloseChild.getFallStyle().plotRect(g, markerX, (int) (markerY + h * 0.6 - 1.0), markerSize, (int) (h * 0.3));
            return;
        }

        for (int childIndex = 0; childIndex < super.getChildCount(); childIndex++)
            super.getChild(childIndex).drawLegendMarker(legend, g, x, y, w, h);
    }

    /// Returns the child renderer currently responsible for `dataSet`.
    ///
    /// @return the matching child, or `null` when this renderer does not currently display
    ///         `dataSet`
    public SingleHiLoRenderer getHiLo(DataSet dataSet) {
        return (SingleHiLoRenderer) super.getChild(dataSet);
    }

    /// Returns the legend label for `legend`.
    ///
    /// OHLC legend items delegate to [HiLoOpenCloseRendererLegendItem]. Otherwise an explicitly
    /// configured renderer name wins, and the fallback joins child legend texts with `/`.
    @Override
    public String getLegendText(LegendEntry legend) {
        if (getMode().isOhlc() && legend instanceof HiLoOpenCloseRendererLegendItem hiLoLegend)
            return hiLoLegend.getLegendText();
        if (getName() != null)
            return getName();

        StringJoiner legendText = new StringJoiner("/");
        for (int childIndex = 0; childIndex < super.getChildCount(); childIndex++)
            legendText.add(super.getChild(childIndex).getLegendText(legend));
        return legendText.toString();
    }

    /// Returns the current dataset-grouping mode.
    public Mode getMode() {
        return mode;
    }

    /// Returns the explicit name for the first logical series.
    @Override
    public String getName() {
        return getName(0);
    }

    /// Returns the explicit name for one logical series.
    ///
    /// @return the configured name, or `null` when that series currently has no explicit name
    public String getName(int index) {
        if (seriesNames != null && index < seriesNames.length)
            return seriesNames[index];
        return null;
    }

    /// Returns the current per-series names, or `null` when none are configured.
    public String[] getNames() {
        return seriesNames;
    }

    /// Returns the stored clustered-mode overlap percentage.
    public double getOverlap() {
        return (clusteredConfig == null) ? 0.0 : clusteredConfig.getOverlap();
    }

    /// Returns the requested logical-series width budget before clustered layout redistributes it
    /// between siblings.
    public double getSpecWidthPercent() {
        return configuredWidthPercent;
    }

    /// Returns the clustered glyph type remembered for current or future clustered children.
    public final Type getType() {
        return type;
    }

    /// Returns the optional pixel hint forwarded to child candle/open-close bodies.
    ///
    /// Non-positive values disable the hint once normalized by [SingleHiLoRenderer].
    public int getPixelBodyWidthHint() {
        return pixelBodyWidthHint;
    }

    /// Returns the effective visible width occupied by the current children.
    ///
    /// Clustered mode sums child widths because siblings sit side by side. OHLC modes return the
    /// widest child because each logical series overlays its paired children.
    @Override
    public double getWidth() {
        double width = 0.0;
        for (int childIndex = 0; childIndex < super.getChildCount(); childIndex++) {
            SingleHiLoRenderer child = (SingleHiLoRenderer) super.getChild(childIndex);
            if (getMode().isClustered())
                width += child.getWidth();
            else
                width = Math.max(width, child.getWidth());
        }
        return width;
    }

    /// Returns the effective width percentage currently occupied by the current children.
    @Override
    public double getWidthPercent() {
        double widthPercent = 0.0;
        for (int childIndex = 0; childIndex < super.getChildCount(); childIndex++) {
            SingleHiLoRenderer child = (SingleHiLoRenderer) super.getChild(childIndex);
            if (getMode().isClustered())
                widthPercent += child.getWidthPercent();
            else
                widthPercent = Math.max(widthPercent, child.getWidthPercent());
        }
        return widthPercent;
    }

    /// Lazily creates the clustered layout config so clustered-only settings can be remembered
    /// while another mode is active.
    private void ensureClusteredConfig() {
        clusteredConfig = new ClusteredRendererConfig(this);
        clusteredConfig.setClusterWidth(configuredWidthPercent, false);
    }

    /// Returns whether child widths should reserve category-border padding.
    @Override
    public boolean isUseCategorySpacingAtBorders() {
        return useCategorySpacingAtBorders;
    }

    /// Rebuilds the child list so it matches the current mode and complete dataset groups.
    private void refreshChildrenForMode() {
        int dataSetCount = super.getDataSource().size();
        int expectedChildCount;
        if (getMode().isClustered()) {
            expectedChildCount = dataSetCount / 2;
            for (int childIndex = 0; childIndex < expectedChildCount; childIndex++) {
                ensureChild(childIndex, childIndex * 2);
                bindClusteredChild(childIndex);
            }
        } else {
            int logicalSeriesCount = dataSetCount / 4;
            expectedChildCount = logicalSeriesCount * 2;
            for (int logicalSeriesIndex = 0; logicalSeriesIndex < logicalSeriesCount; logicalSeriesIndex++) {
                int firstChildIndex = logicalSeriesIndex * 2;
                ensureChild(firstChildIndex, logicalSeriesIndex * 4);
                ensureChild(firstChildIndex + 1, logicalSeriesIndex * 4 + 2);
                bindOhlcChildPair(firstChildIndex);
            }
        }

        boolean removedChildren = false;
        while (super.getChildCount() > expectedChildCount) {
            ChartRenderer child = super.getChild(super.getChildCount() - 1);
            child.getDataSource().setAll(null);
            super.removeChild(child);
            removedChildren = true;
        }

        if (removedChildren)
            super.updateChildren();
        if (getMode().isClustered())
            clusteredConfig.updateChildren();
    }

    private void ensureChild(int childIndex, int dataSetIndex) {
        ChartRenderer child = super.getChild(childIndex);
        if (child == null) {
            child = createChild(super.getDataSource().get(dataSetIndex));
            super.insertChild(childIndex, child);
        }
    }

    /// Switches how logical series are grouped into child renderers.
    ///
    /// Changing the mode recreates or rebinds children as needed, keeps any remembered clustered
    /// overlap settings, and preserves the clustered [#getType()] value so it can be restored when
    /// returning to [Mode#CLUSTERED].
    public void setMode(Mode mode) {
        if (this.mode == mode)
            return;

        validateMode(mode);
        if (this.mode.isClustered() && clusteredConfig != null)
            clusteredConfig.deactivate();
        this.mode = mode;
        if (this.mode.isClustered()) {
            if (clusteredConfig == null)
                ensureClusteredConfig();
            clusteredConfig.activate();
        }
        refreshChildrenForMode();
        super.triggerChange(4);
    }

    /// Assigns the explicit legend name for one logical series.
    public void setName(int index, String name) {
        if (seriesNames != null && index < seriesNames.length) {
            if (name != null ? name.equals(seriesNames[index]) : seriesNames[index] == null)
                return;
            seriesNames[index] = name;
        } else {
            String[] newSeriesNames = new String[index + 1];
            if (seriesNames != null)
                System.arraycopy(seriesNames, 0, newSeriesNames, 0, seriesNames.length);
            newSeriesNames[index] = name;
            seriesNames = newSeriesNames;
        }
        super.triggerChange(6);
    }

    /// Assigns the explicit legend name for the first logical series.
    @Override
    public void setName(String name) {
        setName(0, name);
    }

    /// Replaces the per-series legend names.
    ///
    /// The input array is cloned when non-null so later caller-side mutations do not affect this
    /// renderer automatically.
    public void setNames(String[] names) {
        if (!Arrays.equals(seriesNames, names)) {
            seriesNames = (names == null) ? null : names.clone();
            super.triggerChange(6);
        }
    }

    /// Stores the overlap percentage to use the next time clustered layout is active.
    ///
    /// When the renderer is already in [Mode#CLUSTERED], the new value is applied immediately.
    public void setOverlap(double overlap) {
        if (clusteredConfig == null) {
            if (overlap == 0.0)
                return;
            ensureClusteredConfig();
        } else if (overlap == clusteredConfig.getOverlap()) {
            return;
        }

        clusteredConfig.setOverlap(overlap, getMode().isClustered());
        if (getMode().isClustered())
            super.triggerChange(4);
    }

    /// Assigns two style slots per physical child renderer.
    ///
    /// In clustered mode that means two styles per logical series. In OHLC modes each logical
    /// series owns two children, so callers must supply four styles per logical series.
    @Override
    public void setStyles(PlotStyle[] styles) {
        int childCount = super.getChildCount();
        if (styles.length < 2 * childCount)
            throw new IllegalArgumentException("Cannot set the styles: the array should have at least 2 styles per child");

        for (int childIndex = 0; childIndex < childCount; childIndex++) {
            ChartRenderer child = super.getChild(childIndex);
            child.setStyles(new PlotStyle[]{styles[2 * childIndex], styles[2 * childIndex + 1]});
        }
    }

    /// Stores the clustered glyph type and reapplies it immediately when clustered layout is
    /// active.
    public void setType(Type type) {
        if (type != this.type) {
            this.type = type;
            if (getMode().isClustered()) {
                refreshChildrenForMode();
                super.triggerChange(4);
            }
        }
    }

    /// Forwards an optional pixel-perfect candle/open-close body width hint to every child.
    ///
    /// Non-positive values disable the hint once each child normalizes it.
    public void setPixelBodyWidthHint(int pixelBodyWidthHint) {
        if (this.pixelBodyWidthHint != pixelBodyWidthHint) {
            this.pixelBodyWidthHint = pixelBodyWidthHint;
            for (int childIndex = 0; childIndex < super.getChildCount(); childIndex++) {
                SingleHiLoRenderer child = (SingleHiLoRenderer) super.getChild(childIndex);
                child.setPixelBodyWidthHint(pixelBodyWidthHint);
            }
        }
    }

    /// Propagates the category-border spacing policy to every current and future child.
    @Override
    public void setUseCategorySpacingAtBorders(boolean useCategorySpacingAtBorders) {
        if (useCategorySpacingAtBorders != this.useCategorySpacingAtBorders) {
            this.useCategorySpacingAtBorders = useCategorySpacingAtBorders;
            for (int childIndex = 0; childIndex < super.getChildCount(); childIndex++) {
                SingleHiLoRenderer child = (SingleHiLoRenderer) super.getChild(childIndex);
                child.setUseCategorySpacingAtBorders(useCategorySpacingAtBorders);
            }
        }
    }

    /// Updates the requested logical-series width budget.
    ///
    /// The value is propagated immediately to current children. If clustered layout is currently
    /// active, the clustered config also reapplies its sibling redistribution. Otherwise the value
    /// is merely remembered for the next return to clustered mode.
    @Override
    public void setWidthPercent(double widthPercent) {
        if (widthPercent != configuredWidthPercent) {
            configuredWidthPercent = widthPercent;
            for (int childIndex = 0; childIndex < super.getChildCount(); childIndex++) {
                SingleHiLoRenderer child = (SingleHiLoRenderer) super.getChild(childIndex);
                child.setWidthPercent(widthPercent);
            }
            if (clusteredConfig != null)
                clusteredConfig.setClusterWidth(widthPercent, getMode().isClustered());
        }
    }
}
