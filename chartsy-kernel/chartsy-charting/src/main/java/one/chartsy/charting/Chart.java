package one.chartsy.charting;

import java.awt.Color;
import java.awt.Component;
import java.awt.ComponentOrientation;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.Paint;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.event.ContainerAdapter;
import java.awt.event.ContainerEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.beans.Beans;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageOutputStream;
import javax.swing.BoundedRangeModel;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JLayeredPane;
import javax.swing.JScrollBar;
import javax.swing.LookAndFeel;
import javax.swing.SwingUtilities;
import javax.swing.border.Border;
import javax.swing.event.EventListenerList;

import one.chartsy.charting.data.AbstractDataSet;
import one.chartsy.charting.data.DataSet;
import one.chartsy.charting.data.DataSource;
import one.chartsy.charting.data.DefaultDataSet;
import one.chartsy.charting.data.DefaultDataSource;
import one.chartsy.charting.event.AxisChangeEvent;
import one.chartsy.charting.event.AxisListener;
import one.chartsy.charting.event.AxisRangeEvent;
import one.chartsy.charting.event.ChartAreaEvent;
import one.chartsy.charting.event.ChartDrawEvent;
import one.chartsy.charting.event.ChartDrawListener;
import one.chartsy.charting.event.ChartListener;
import one.chartsy.charting.event.ChartRendererEvent;
import one.chartsy.charting.event.ChartRendererListener;
import one.chartsy.charting.event.ChartRendererListener2;
import one.chartsy.charting.event.DataSetContentsEvent;
import one.chartsy.charting.event.LegendDockingEvent;
import one.chartsy.charting.internal.AbstractPaintAction;
import one.chartsy.charting.internal.AbstractProjector;
import one.chartsy.charting.internal.BoundedRangeModelConnector;
import one.chartsy.charting.internal.CartesianProjector;
import one.chartsy.charting.internal.ChartDrawableComparator;
import one.chartsy.charting.internal.ChartInteractorManager;
import one.chartsy.charting.internal.PaintAction;
import one.chartsy.charting.internal.PolarProjector;
import one.chartsy.charting.swing.JLabelExt;
import one.chartsy.charting.util.ChartUtil;
import one.chartsy.charting.util.Flags;
import one.chartsy.charting.util.GraphicUtil;
import one.chartsy.charting.util.IntInterval;
import one.chartsy.charting.util.IntIntervalSet;
import one.chartsy.charting.util.collections.PreorderIterator;
import one.chartsy.charting.util.java2d.G2D;
import one.chartsy.charting.util.swing.MouseEventFilter;
import one.chartsy.charting.util.text.BidiUtil;
import one.chartsy.charting.util.text.NumberFormatFactory;

/// Swing chart component that coordinates axes, renderers, decorations, interactors, and
/// image export for the charting module.
///
/// A `Chart` owns one primary [Area] child where scales, grids, renderers, and
/// [ChartDecoration] instances are painted. The chart keeps a single x axis plus zero or more y
/// axes, routes each renderer to one of those axis pairs, and delegates auto-range computation to
/// the configured [DataRangePolicy].
///
/// Instances are UI-oriented and share mutable layout, scale, and export state across their
/// renderers and nested helper types. Most mutating operations therefore synchronize on
/// [#getLock()]. `setUsingEventThread(boolean)` only controls whether Swing `repaint()` and
/// `revalidate()` work is forwarded automatically; it does not make the broader API thread-safe.
///
/// ### API Note
///
/// Besides normal on-screen painting, a chart can render into an off-screen
/// [ChartPaintContext]. Export helpers such as [#toImage(int, int, Color)] and
/// [#toPNG(OutputStream, int, int, boolean)] temporarily switch the chart into that session mode so
/// layout and drawing can run without a live Swing hierarchy update.
public class Chart extends JLayeredPane {

    /// Off-screen paint context backed by a dedicated [BufferedImage].
    ///
    /// Export helpers use this variant when the chart itself owns the target image. The context
    /// keeps the inherited layout metadata from [ChartPaintContext] together with the actual raster
    /// that painting will target.
    public static final class ChartImagePaintContext extends Chart.ChartPaintContext {
        private transient BufferedImage image;

        /// Creates a new image-backed export context.
        ///
        /// @param width         the destination image width in pixels
        /// @param height        the destination image height in pixels
        /// @param rootComponent the component whose layout should drive the export session
        public ChartImagePaintContext(int width, int height, JComponent rootComponent) {
            super(width, height, rootComponent);
            image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        }

        /// Returns a graphics context for the backing image.
        ///
        /// The caller owns the returned graphics instance and should dispose it after painting.
        public Graphics getGraphics() {
            return getImage().getGraphics();
        }

        /// Returns the backing image that will receive chart painting.
        public BufferedImage getImage() {
            return image;
        }
    }

    /// Mutable layout state for an off-screen chart paint session.
    ///
    /// A paint context captures the export root, the requested session bounds, the draw rectangle
    /// actually assigned to the chart area, and optional component bounds computed by
    /// [ChartLayout]. Nested components such as [Legend] and renderers consult this state while a
    /// chart is painting without a live Swing hierarchy update.
    public static class ChartPaintContext implements Serializable {
        Rectangle drawRect;
        Rectangle plotRect;
        Rectangle sessionBounds;
        JComponent rootComponent;
        Map<Component, Rectangle> componentBounds;

        /// Creates a context for the requested export size and root component.
        ///
        /// @param width         the session width in pixels
        /// @param height        the session height in pixels
        /// @param rootComponent the component whose layout should be exported
        public ChartPaintContext(int width, int height, JComponent rootComponent) {
            sessionBounds = new Rectangle(0, 0, width, height);
            drawRect = sessionBounds;
            this.rootComponent = rootComponent;
        }

        /// Returns the per-component bounds computed for the active export session.
        ///
        /// The returned map is the one currently retained by the context and may be `null` when
        /// the export path does not compute child bounds explicitly.
        public final Map<Component, Rectangle> getBounds() {
            return componentBounds;
        }

        /// Returns the rectangle currently assigned to the exported chart area.
        ///
        /// Area-only exports use the full session bounds. Full-chart exports replace this with the
        /// chart-area rectangle computed by [ChartLayout].
        public final Rectangle getDrawRect() {
            return drawRect;
        }

        /// Installs the component-bounds map for the current session.
        ///
        /// The map reference is retained directly because nested chart components look it up while
        /// the export is in progress.
        ///
        /// @param bounds component bounds keyed by the exported components
        public final void setBounds(Map<Component, Rectangle> bounds) {
            componentBounds = bounds;
        }
    }

    /// Paint action used when exporting the whole chart rather than only the chart area.
    ///
    /// The action keeps the chart itself as the export root, so the normal component hierarchy and
    /// layout contracts remain in force during the paint pass.
    private static class ChartPaintAction extends AbstractPaintAction {

        /// Creates the helper for a chart-level export pass.
        ///
        /// @param temporaryParent transient parent used only when the exported chart is detached
        public ChartPaintAction(Container temporaryParent) {
            super(temporaryParent);
        }

        /// Uses the chart itself as the export root.
        @Override
        protected JComponent getRootComponent(JComponent c) {
            return c;
        }

        /// Resizes the exported chart to the requested image size before painting.
        @Override
        protected void resizeComponent(JComponent c, Dimension size) {
            c.setSize(size.width, size.height);
            c.getParent().validate();
        }
    }

    /// Re-applies chart-specific bidirectional shaping after a header or footer label text change.
    ///
    /// The listener suppresses self-recursion while it normalizes the label text.
    final class JLabelTextChangeListener implements PropertyChangeListener, Serializable {
        private boolean updatingText;

        /// Creates the listener in its idle state.
        JLabelTextChangeListener() {
            updatingText = false;
        }

        /// Normalizes changed plain-text label content for the chart's resolved text direction.
        @Override
        public void propertyChange(PropertyChangeEvent event) {
            if (!updatingText) {
                JLabel label = (JLabel) event.getSource();
                try {
                    updatingText = true;
                    label.setText(BidiUtil.getCombinedString(label.getText(), getResolvedBaseTextDirection(),
                            getComponentOrientation(), true));
                } finally {
                    updatingText = false;
                }
            }
        }
    }

    /// Coalesces batched dataset-change events for one renderer/dataset pair.
    ///
    /// When a dataset reports batched mutations, the chart stores one instance per
    /// [RendererDataSetKey] and merges compatible updates until the batch ends. Flushing then
    /// replays the smallest chart update that still preserves correct layout and repaint behavior.
    class DataSetEventAccumulator extends RendererDataSetKey {
        private int pendingEventType;
        private IntIntervalSet changedRanges;
        private boolean layoutRequired;
        private int firstAddedIndex;
        private int lastAddedIndex;

        /// Creates an empty accumulator for the supplied renderer and dataset.
        DataSetEventAccumulator(ChartRenderer renderer, DataSet dataSet) {
            super(renderer, dataSet);
            pendingEventType = -2;
        }

        /// Flushes the currently merged event to the enclosing chart and resets the accumulator.
        void flush() {
            Iterator intervalIterator;
            switch (pendingEventType) {
                case -2:
                    break;

                case 2:
                    intervalIterator = changedRanges.intervalIterator();
                    while (true) {
                        if (!intervalIterator.hasNext())
                            break;
                        IntInterval changedRange = (IntInterval) intervalIterator.next();
                        Chart.this.handleDataChangedRange(super.renderer, super.dataSet, changedRange.getFirst(), changedRange.getLast(), layoutRequired);
                    }
                    changedRanges = null;
                    break;

                case 3:
                    Chart.this.handleDataChanged(super.dataSet);
                    break;

                case 4:
                    if (firstAddedIndex > lastAddedIndex)
                        break;
                    Chart.this.handleDataAddedRange(super.renderer, super.dataSet, firstAddedIndex, lastAddedIndex);
                    break;

                case 6:
                    Chart.this.handleFullDataUpdate(super.dataSet);
                    break;

                default:
                    throw new IllegalStateException();

            }
            pendingEventType = -2;
        }

        /// Records a `DATA_ADDED` range and merges adjacent additions when possible.
        void recordAddedRange(int firstIndex, int lastIndex) {
            if (firstIndex <= lastIndex)
                if (pendingEventType != 6)
                    if (pendingEventType != 4) {
                        if (pendingEventType != -2)
                            this.flush();
                        pendingEventType = 4;
                        firstAddedIndex = firstIndex;
                        lastAddedIndex = lastIndex;
                    } else if (firstAddedIndex > lastAddedIndex) {
                        firstAddedIndex = firstIndex;
                        lastAddedIndex = lastIndex;
                    } else if (firstIndex != lastAddedIndex + 1)
                        pendingEventType = 6;
                    else
                        lastAddedIndex = lastIndex;
        }

        /// Records an `AFTER_DATA_CHANGED` range.
        ///
        /// `layoutRequired` tracks whether the changed range contains annotations that can enlarge the plot
        /// rectangle and therefore require layout work rather than a paint-only update.
        void recordChangedRange(int firstIndex, int lastIndex, boolean layoutRequired) {
            if (firstIndex <= lastIndex)
                if (pendingEventType != 6) {
                    if (pendingEventType != 2) {
                        if (pendingEventType != -2)
                            this.flush();
                        pendingEventType = 2;
                        changedRanges = new IntIntervalSet();
                        this.layoutRequired = false;
                    }
                    changedRanges.add(firstIndex, lastIndex);
                    if (layoutRequired)
                        this.layoutRequired = true;
                }
        }

        /// Records a `DATA_CHANGED` event that requires data-range recomputation.
        void recordDataChanged() {
            if (pendingEventType != 6)
                if (pendingEventType != 3) {
                    if (pendingEventType != -2)
                        this.flush();
                    pendingEventType = 3;
                }
        }

        /// Escalates the accumulator to a `FULL_UPDATE`.
        void markFullUpdate() {
            if (pendingEventType != 6) {
                pendingEventType = 6;
                changedRanges = null;
            }
        }
    }

    /// Identity key used for pending batched dataset-change accumulators.
    ///
    /// Equality is based on renderer and dataset identity rather than `equals(...)` so one queued
    /// accumulator tracks the exact pair currently connected to the chart.
    static class RendererDataSetKey {
        final ChartRenderer renderer;
        final DataSet dataSet;

        /// Creates the key for the supplied renderer/dataset pair.
        RendererDataSetKey(ChartRenderer renderer, DataSet dataSet) {
            this.renderer = renderer;
            this.dataSet = dataSet;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof RendererDataSetKey key))
                return false;
            int equal;
            block:
            {
                if (renderer == key.renderer)
                    if (dataSet == key.dataSet) {
                        equal = 1;
                        break block;
                    }
                equal = 0;
            } // end block

            return equal != 0;
        }

        @Override
        public int hashCode() {
            return 167 * renderer.hashCode() + dataSet.hashCode();
        }
    }

    /// Chart-level listener attached to owned axes.
    ///
    /// The listener invalidates scale caches and refreshes the chart area whenever an axis change
    /// affects layout or visible ranges.
    class ChartAxisListener implements AxisListener, Serializable {

        ChartAxisListener() {
        }

        /// Reacts to non-range axis changes that can invalidate scale layout.
        @Override
        public void axisChanged(AxisChangeEvent event) {
            if (event.getType() != 1) {
                invalidateScales();
                if (one.chartsy.charting.Chart.this.areRefreshUpdatesEnabled())
                    chartArea.repaint();
                else
                    chartArea.setFullRepaintPending(true);
            }
        }

        /// Reacts to committed visible-range changes.
        @Override
        public void axisRangeChanged(AxisRangeEvent event) {
            if (!event.isAboutToChangeEvent())
                if (event.isVisibleRangeEvent()) {
                    invalidateScales();
                    if (one.chartsy.charting.Chart.this.areRefreshUpdatesEnabled())
                        chartArea.repaint();
                    else
                        chartArea.setFullRepaintPending(true);
                }
        }
    }

    /// Internal ownership object for one chart axis slot.
    ///
    /// Each `AxisElement` ties together an [Axis], its optional [Scale], optional [Grid], optional
    /// [ValueFormatter], and the [CoordinateSystem] shared with the corresponding crossing axis.
    /// The enclosing chart keeps one element for the x axis and one for each y axis, letting
    /// renderers and decorations reason about an axis slot without separately tracking every
    /// attached helper object.
    ///
    /// The element also owns the re-wiring logic required when an x axis is replaced: every y-axis
    /// coordinate system and scale must be rebound to the new x axis before layout can proceed.
    final class AxisElement implements Serializable {
        private Axis axis;
        private Scale scale;
        private final CoordinateSystem coordinateSystem;
        private Grid grid;
        private ValueFormatter valueFormatter;
        private final int axisIndex;
        // *synthetic*/ static final boolean getTargetArea = Chart.class.desiredAssertionStatus();

        /// Creates an axis slot bound to one axis and its coordinate system.
        ///
        /// @param axis             the axis owned by this slot
        /// @param coordinateSystem the coordinate system pairing that axis with its crossing axis
        /// @param axisIndex        the chart slot index, where `-1` denotes the x axis and non-negative
        ///                             values denote y axes
        AxisElement(Axis axis, CoordinateSystem coordinateSystem, int axisIndex) {
            this.setAxis(Objects.requireNonNull(axis, "Axis"));
            this.coordinateSystem = coordinateSystem;
            this.axisIndex = axisIndex;
        }

        /// Returns the slot index used by chart renderers and layout code.
        int getAxisIndex() {
            return axisIndex;
        }

        void setAxis(Axis axis) {
            int index;
            Chart.AxisElement element;
            if (this.axis != null) {
                this.axis.removeAxisListener(getOrCreateSharedAxisListener());
                if (this.axis.isXAxis()) {
                    index = axisElements.size();
                    while (true) {
                        index--;
                        if (index < 1)
                            break;
                        element = one.chartsy.charting.Chart.this.getAxisElement(index);
                        if (element.getScale() != null)
                            element.getScale().setAxisElement(null);
                    }
                }
                if (scale != null)
                    scale.setAxisElement(null);
                this.axis.setChart(null);
            }
            this.axis = axis;
            if (axis == null) {
                this.setScale((Scale) null);
                this.setGrid((Grid) null);
            } else {
                if (axis.isXAxis()) {
                    index = axisElements.size();
                    while (true) {
                        index--;
                        if (index < 1)
                            break;
                        element = one.chartsy.charting.Chart.this.getAxisElement(index);
                        element.getCoordinateSystem().setXAxis(axis);
                        if (element.getScale() != null)
                            element.getScale().setAxisElement(element);
                    }
                } else if (this.getCoordinateSystem() != null)
                    this.getCoordinateSystem().setYAxis(axis);
                axis.addAxisListener(getOrCreateSharedAxisListener());
                if (scale != null)
                    scale.setAxisElement(this);
                this.axis.setChart(one.chartsy.charting.Chart.this);
            }
        }

        /// Formats an axis value using the explicit formatter, attached scale, or chart fallback format.
        String formatValue(double value) {
            if (valueFormatter != null)
                return valueFormatter.formatValue(value);
            if (scale != null)
                return scale.computeLabel(value);
            return createFallbackNumberFormat().format(value);
        }

        void setGrid(Grid grid) {
            if (grid != this.grid) {
                if (this.grid != null) {
                    Chart.this.detachDrawable(this.grid);
                    this.grid.setAxisElement(null);
                }
                this.grid = grid;
                if (this.grid != null) {
                    this.grid.setAxisElement(this);
                    Chart.this.attachDrawable(this.grid);
                }
            }
        }

        void setScale(Scale scale) {
            if (this.scale != null) {
                ((ChartAreaLayout) getChartArea().getLayout()).setScaleManaged(this.scale, false);
                Chart.this.detachDrawable(this.scale);
                this.scale.setAxisElement(null);
            }
            this.scale = scale;
            if (this.scale != null) {
                this.scale.setAxisElement(this);
                ((ChartAreaLayout) getChartArea().getLayout()).setScaleManaged(this.scale, true);
                if (!axis.isXAxis())
                    this.scale.setScaleConfiguration(chartConfig.createYScaleConfig());
                else
                    this.scale.setScaleConfiguration(chartConfig.createXScaleConfig());
                Chart.this.attachDrawable(this.scale);
            }
            getChartArea().revalidateLayout();
        }

        void setValueFormatter(ValueFormatter formatter) {
            valueFormatter = formatter;
        }

        Axis getAxis() {
            return axis;
        }

        Scale getScale() {
            return scale;
        }

        Grid getGrid() {
            return grid;
        }

        Chart getChart() {
            return one.chartsy.charting.Chart.this;
        }

        Axis getCrossAxis() {
            Axis crossAxis = (!axis.isXAxis()) ? coordinateSystem.getXAxis() : coordinateSystem.getYAxis();
            return crossAxis;
        }

        /// Returns the coordinate system pairing this slot's axis with its crossing axis.
        CoordinateSystem getCoordinateSystem() {
            return coordinateSystem;
        }
    }

    /// Primary painting surface for a [Chart].
    ///
    /// `Area` owns the plot rectangle, chart-area layout, plot background styling, renderer
    /// painting, and most incremental repaint bookkeeping. The enclosing chart delegates nearly all
    /// scale, grid, decoration, and renderer drawing to this component.
    ///
    /// Callers usually interact with an `Area` through [Chart#getChartArea()] to tune margins,
    /// export only the plot surface, or query plot-space geometry. The instance is tightly bound to
    /// its enclosing chart and is not intended to be reused independently.
    ///
    /// ### API Note
    ///
    /// [#getPlotRect()] returns a mutable [Rectangle] describing the current plot bounds. The area
    /// detects destructive modifications to the previously returned instance and fails fast with
    /// [ConcurrentModificationException], so callers should treat that rectangle as read-only.
    public class Area extends JComponent {

        /// Paint action used by area-only image exports.
        ///
        /// The action resizes the enclosing chart rather than the area alone so the chart layout can
        /// recompute header, footer, legend, and plot geometry consistently before the area is
        /// painted into an image.
        private final class ChartAreaPaintAction extends AbstractPaintAction {

            /// Creates an area export action rooted in the supplied hidden print container.
            public ChartAreaPaintAction(Container printRoot) {
                super(printRoot);
            }

            @Override
            protected JComponent getRootComponent(JComponent component) {
                return getChart();
            }

            @Override
            protected void resizeComponent(JComponent component, Dimension size) {
                component.setSize(size);
                Chart chart = getChart();
                chart.setPreferredSize(null);
                chart.setSize(chart.getPreferredSize());
                chart.validate();
            }
        }

        /// Value-gradient paint that keeps plot gradients aligned with the current visible axis range.
        ///
        /// The gradient recomputes its sample values whenever the underlying axis or chart area
        /// changes so banded plot backgrounds track zooming and scrolling.
        class PlotGradient extends ValueGradientPaint {
            private final int stopCount;

            /// Creates a gradient anchored to the default axis direction for the supplied colors.
            PlotGradient(Chart chart, Color[] colors) {
                super(chart, new double[colors.length], colors);
                stopCount = colors.length;
            }

            /// Creates a gradient anchored to the requested axis index for the supplied colors.
            PlotGradient(Chart chart, int axisIndex, Color[] colors) {
                super(chart, axisIndex, new double[colors.length], colors);
                stopCount = colors.length;
            }

            @Override
            protected void update() {
                super.setValues(one.chartsy.charting.Chart.Area.this.computeGradientStops(super.getAxis(), stopCount));
            }
        }

        /// Fixed-margin mask for the left plot edge.
        public static final int FIXED_LEFT_MARGIN = 1;
        /// Fixed-margin mask for the right plot edge.
        public static final int FIXED_RIGHT_MARGIN = 2;
        /// Fixed-margin mask for the top plot edge.
        public static final int FIXED_TOP_MARGIN = 4;
        /// Fixed-margin mask for the bottom plot edge.
        public static final int FIXED_BOTTOM_MARGIN = 8;
        private boolean includeAnnotationsInPlotRect;
        private transient Rectangle2D dirtyRepaintBounds;
        private PlotStyle plotStyle;
        private boolean scaleFontWithChart;
        private transient Rectangle previousDrawRect;
        private Color backgroundOverride;
        private Paint backgroundPaintOverride;
        Rectangle cachedPlotRect;
        transient Rectangle exposedPlotRect;
        private transient Rectangle exposedPlotRectSnapshot;
        private final MouseEventFilter systemMouseEventFilter;

        Area() {
            includeAnnotationsInPlotRect = false;
            scaleFontWithChart = false;
            previousDrawRect = null;
            systemMouseEventFilter = MouseEventFilter.createSystemEventFilter();
            super.setLocale(Locale.getDefault());
            this.initializeDirtyRepaintBounds();
            super.putClientProperty(CHART_COMPONENT_CLIENT_PROPERTY, Boolean.TRUE);
            super.enableEvents(511L);
            this.initializeAreaState();
        }

        void resetPlotStyle() {
            plotStyle = new PlotStyle(super.getForeground(), Chart.DEFAULT_PLOT_FILL_COLOR);
        }

        private double[] computeGradientStops(Axis axis, int stopCount) {
            DataInterval visibleRange = axis.getVisibleRange();
            if (getType() == 2)
                if (((PolarProjector) getProjector2D()).isSymmetric())
                    visibleRange.setMin(0.0);
            double increment = visibleRange.getLength() / (stopCount - 1);
            double[] stops = new double[stopCount];
            double currentValue = visibleRange.isEmpty() ? 0.0 : visibleRange.getMin();
            int index = 0;
            while (true) {
                if (index >= stops.length)
                    break;
                stops[index] = currentValue;
                index++;
                currentValue = currentValue + increment;
            }
            return stops;
        }

        void setFullRepaintPending(boolean fullRepaintPending) {
            stateFlags.setFlag(1024, fullRepaintPending);
        }

        void paintDrawables(Graphics g, Rectangle clipRect, boolean aboveRenderers) {
            int drawOrderIndex = 0;
            block:
            if (!aboveRenderers)
                while (true) {
                    if (drawOrderIndex >= drawables.size())
                        break block;
                    if (((ChartDrawable) drawables.get(drawOrderIndex))
                            .getDrawOrder() >= 0)
                        break block;
                    drawOrderIndex++;
                }

            Object previousBounds = null;
            while (true) {
                if (drawOrderIndex >= drawables.size())
                    break;
                int drawOrder = ((ChartDrawable) drawables.get(drawOrderIndex)).getDrawOrder();
                if (aboveRenderers)
                    if (drawOrder >= 0)
                        break;
                int groupEndIndex = drawOrderIndex + 1;
                while (true) {
                    if (groupEndIndex >= drawables.size())
                        break;
                    if (((ChartDrawable) drawables.get(groupEndIndex))
                            .getDrawOrder() != drawOrder)
                        break;
                    groupEndIndex++;
                }
                int itemIndex = drawOrderIndex;
                ChartDrawable drawable;
                Rectangle2D drawableBounds;
                while (true) {
                    if (itemIndex >= groupEndIndex)
                        break;
                    drawable = (ChartDrawable) drawables.get(itemIndex);
                    block_2:
                    if (drawable instanceof ChartDecoration)
                        if (drawable.isVisible()) {
                            drawableBounds = (!(drawable instanceof Scale)) ? drawable.getBounds((Rectangle2D) previousBounds)
                                    : ((Scale) drawable).getBoundsUsingCache((Rectangle2D) previousBounds);
                            previousBounds = drawableBounds;
                            if (clipRect != null)
                                if (!((Rectangle2D) previousBounds).intersects(clipRect))
                                    break block_2;
                            ((ChartDecoration) drawable).beforeDraw(g);
                        }

                    itemIndex++;
                }
                itemIndex = drawOrderIndex;
                while (true) {
                    if (itemIndex >= groupEndIndex)
                        break;
                    drawable = (ChartDrawable) drawables.get(itemIndex);
                    block_3:
                    if (drawable.isVisible()) {
                        drawableBounds = (!(drawable instanceof Scale)) ? drawable.getBounds((Rectangle2D) previousBounds)
                                : ((Scale) drawable).getBoundsUsingCache((Rectangle2D) previousBounds);
                        previousBounds = drawableBounds;
                        if (clipRect != null)
                            if (!((Rectangle2D) previousBounds).intersects(clipRect))
                                break block_3;
                        drawable.draw(g);
                    } // end block_3

                    itemIndex++;
                }
                itemIndex = drawOrderIndex;
                while (true) {
                    if (itemIndex >= groupEndIndex)
                        break;
                    drawable = (ChartDrawable) drawables.get(itemIndex);
                    block_4:
                    if (drawable instanceof ChartDecoration)
                        if (drawable.isVisible()) {
                            drawableBounds = (!(drawable instanceof Scale)) ? drawable.getBounds((Rectangle2D) previousBounds)
                                    : ((Scale) drawable).getBoundsUsingCache((Rectangle2D) previousBounds);
                            previousBounds = drawableBounds;
                            if (clipRect != null)
                                if (!((Rectangle2D) previousBounds).intersects(clipRect))
                                    break block_4;
                            ((ChartDecoration) drawable).afterDraw(g);
                        }

                    itemIndex++;
                }
                drawOrderIndex = groupEndIndex;
            }
        }

        void paintCurrentThread(Graphics2D g) {
            paintCurrentThread(g, null);
        }

        private Rectangle computePlotRect(Rectangle drawRect) {
            return this.getChartAreaLayout().computePlotRect(drawRect);
        }

        final void addDirtyRegion(Rectangle2D dirtyBounds) {
            if (dirtyBounds != null)
                GraphicUtil.addToRect(dirtyRepaintBounds, dirtyBounds);
        }

        final ChartAreaLayout getChartAreaLayout() {
            return (ChartAreaLayout) super.getLayout();
        }

        private void paintChartContents(Graphics g) {
            Rectangle drawRect = getDrawRect();
            Shape originalClip = g.getClip();
            if (!isPaintingImage())
                if (originalClip != null)
                    if (!originalClip.intersects(drawRect))
                        return;
            Rectangle plotRect = getPlotRect();
            g.clipRect(drawRect.x, drawRect.y, drawRect.width, drawRect.height);
            int chartType = getType();
            PlotStyle plotStyle = getPlotStyle();
            int hasPlotStyle = (plotStyle == null) ? 0 : 1;
            int stylePresent = hasPlotStyle;
            Object plotShape = null;
            CoordinateSystem coordinateSystem = getCoordinateSystem(0);
            Object clipBeforeRendererPaint;
            if (stylePresent != 0)
                if (plotStyle.isFillOn())
                    if (chartType != 1) {
                        clipBeforeRendererPaint = coordinateSystem.getVisibleWindow();
                        plotShape = getProjector().getShape((DataWindow) clipBeforeRendererPaint, plotRect, coordinateSystem);
                        plotStyle.fill(g, (Shape) plotShape);
                    } else
                        plotStyle.fillRect(g, plotRect.x, plotRect.y, plotRect.width - 1, plotRect.height - 1);
            clipBeforeRendererPaint = g.getClipBounds();
            updateScalesIfNeeded();
            this.paintDrawables(g, (Rectangle) clipBeforeRendererPaint, true);
            Object polarClip = null;
            if (stylePresent != 0)
                if (plotStyle.isStrokeOn())
                    if (chartType == 1)
                        plotStyle.drawRect(g, plotRect.x, plotRect.y, plotRect.width - 1, plotRect.height - 1);
                    else {
                        if (plotShape == null) {
                            polarClip = coordinateSystem.getVisibleWindow();
                            plotShape = getProjector().getShape((DataWindow) polarClip, plotRect, coordinateSystem);
                        }
                        plotStyle.draw(g, (Shape) plotShape);
                    }
            polarClip = null;
            block:
            {
                if (chartType != 2)
                    if (chartType != 4)
                        break block;
                if (coordinateSystem.getVisibleWindow().getYMax() != coordinateSystem.getYAxis().getDataMax()) {
                    polarClip = g.getClipBounds();
                    DataWindow visibleWindow = coordinateSystem.getVisibleWindow();
                    plotShape = getProjector().getShape(visibleWindow, plotRect, coordinateSystem);
                    g.setClip((Shape) plotShape);
                }
            } // end block

            int rendererCount = getRendererCount();
            int rendererIndex = 0;
            while (true) {
                if (rendererIndex >= rendererCount)
                    break;
                ChartRenderer renderer = getRenderer(rendererIndex);
                if (renderer.isViewable()) {
                    renderer.draw(g);
                    renderer.drawAnnotations(g);
                }
                rendererIndex++;
            }
            if (polarClip != null)
                g.setClip(((Rectangle) polarClip).x, ((Rectangle) polarClip).y, ((Rectangle) polarClip).width, ((Rectangle) polarClip).height);
            this.paintDrawables(g, (Rectangle) clipBeforeRendererPaint, false);
            if (interactorManager != null)
                interactorManager.handleExpose(g);
            g.setClip(originalClip);
        }

        boolean isFullRepaintPending() {
            return stateFlags.getFlag(1024);
        }

        void clearLayoutCachesAfterPaint() {
            if (!isPaintingImage())
                this.getChartAreaLayout().plotRectValid = false;
            else
                activePaintContext.plotRect = null;
        }

        final void repaintDirtyRegion() {
            repaint2D(dirtyRepaintBounds);
            dirtyRepaintBounds.setRect(0.0, 0.0, 0.0, 0.0);
        }

        final boolean hasDirtyRegion() {
            return !dirtyRepaintBounds.isEmpty();
        }

        final void flushPendingRepaint() {
            if (this.isFullRepaintPending())
                this.repaint();
            else if (this.hasDirtyRegion())
                this.repaintDirtyRegion();
        }

        @Override
        public Color getBackground() {
            if (backgroundOverride != null)
                return backgroundOverride;
            return super.getBackground();
        }

        /// Returns the paint used when the chart area clears its background.
        ///
        /// When no explicit paint is configured, this falls back to [#getBackground()].
        public final Paint getBackgroundPaint() {
            return (backgroundPaintOverride == null) ? getBackground() : backgroundPaintOverride;
        }

        /// Returns the enclosing chart that owns this area.
        public final Chart getChart() {
            return one.chartsy.charting.Chart.this;
        }

        /// Returns the drawable rectangle inside the area after applying component insets.
        ///
        /// During image export the rectangle is derived from the active paint context rather than the
        /// live Swing bounds.
        public Rectangle getDrawRect() {
            Insets insets = super.getInsets();
            Rectangle drawRect = (!isPaintingImage()) ? super.getBounds()
                    : new Rectangle(activePaintContext.drawRect);
            drawRect.x = insets.left;
            drawRect.y = insets.top;
            drawRect.width -= insets.left + insets.right;
            drawRect.height -= insets.top + insets.bottom;
            return drawRect;
        }

        /// Returns the bit mask describing which margins are fixed by the current layout.
        public int getFixedMargins() {
            return this.getChartAreaLayout().getFixedMargins();
        }

        @Override
        public Font getFont() {
            if (scaleFontWithChart) {
                ScalableFontManager fontManager = getChart().getFontManager();
                if (fontManager != null)
                    return fontManager.getDeriveFont(super.getFont());
            }
            return super.getFont();
        }

        @Override
        public Graphics getGraphics() {
            Graphics g = super.getGraphics();
            if (g == null)
                return null;
            if (isAntiAliasing())
                GraphicUtil.startAntiAliasing(g);
            if (isAntiAliasingText())
                GraphicUtil.startTextAntiAliasing(g);
            return g;
        }

        /// Returns the current margins tracked by the active [ChartAreaLayout].
        ///
        /// The returned [Insets] reflect either explicit fixed margins or the most recent
        /// auto-computed margins. Callers must treat the instance as read-only.
        public Insets getMargins() {
            return this.getChartAreaLayout().getMargins();
        }

        /// Returns the current plot fill paint extracted from the plot style.
        public final Paint getPlotBackground() {
            return (plotStyle == null) ? null : plotStyle.getFillPaint();
        }

        /// Returns the rectangle currently used as the plot region.
        ///
        /// The returned rectangle reflects the current layout state or the active image-export
        /// session. Callers must treat the instance as read-only; destructive mutations are detected
        /// and reported on the next access.
        public Rectangle getPlotRect() {
            Rectangle plotRect = null;
            if (cachedPlotRect != null) {
                Object lock = getLock();
                synchronized (lock) {
                    if (cachedPlotRect != null)
                        plotRect = cachedPlotRect;
                }
            }
            if (plotRect == null)
                if (!isPaintingImage()) {
                    ChartAreaLayout layout = this.getChartAreaLayout();
                    if (!layout.plotRectValid)
                        layout.layoutArea(this);
                    plotRect = new Rectangle(layout.cachedPlotRect);
                } else {
                    if (activePaintContext.plotRect == null)
                        activePaintContext.plotRect = this.computePlotRect(getDrawRect());
                    plotRect = new Rectangle(activePaintContext.plotRect);
                }
            if (exposedPlotRect != null)
                if (!exposedPlotRect.equals(exposedPlotRectSnapshot))
                    throw new ConcurrentModificationException(
                            "getPlotRect() result was destructively modified from " +
                                    exposedPlotRectSnapshot + " to " + exposedPlotRect);
            if (!plotRect.equals(exposedPlotRectSnapshot))
                invalidateScales();
            exposedPlotRect = plotRect;
            exposedPlotRectSnapshot = (Rectangle) plotRect.clone();
            return plotRect;
        }

        /// Returns the plot style currently used for plot fill and stroke painting.
        public PlotStyle getPlotStyle() {
            if (stateFlags.getFlag(FLAG_PLOT_STYLE_TRACKS_FOREGROUND)) {
                Color foreground = super.getForeground();
                if (plotStyle != null)
                    if (plotStyle.getStrokeColor() != foreground)
                        plotStyle.setStrokePaintInternal(foreground);
            }
            return plotStyle;
        }

        @Override
        public Dimension getPreferredSize() {
            Dimension preferredSize = super.getPreferredSize();
            block:
            {
                if (preferredSize.width > 0)
                    if (preferredSize.height > 0)
                        break block;
                preferredSize.width = DEFAULT_PREFERRED_WIDTH;
                preferredSize.height = DEFAULT_PREFERRED_HEIGHT;
            } // end block

            return preferredSize;
        }

        private void initializeAreaState() {
            stateFlags.setFlag(FLAG_PLOT_STYLE_TRACKS_FOREGROUND, true);
            stateFlags.setFlag(FLAG_PLOT_GRADIENT_ACTIVE, false);
            setLayout(new ChartAreaLayout());
            this.resetPlotStyle();
        }

        private ValueGradientPaint getPlotGradient() {
            return (ValueGradientPaint) plotStyle.getFillPaint();
        }

        /// Returns whether repaint requests paint immediately instead of scheduling a normal Swing repaint.
        public boolean isDirectRedrawEnabled() {
            return stateFlags.getFlag(FLAG_DIRECT_REDRAW_ENABLED);
        }

        /// Returns whether the plot style currently fills the plotting area shape.
        public boolean isFilledPlottingArea() {
            return plotStyle != null && plotStyle.isFillOn();
        }

        /// Returns whether plot bounds should expand to include annotation geometry.
        ///
        /// When enabled, dataset and renderer changes that affect annotations may force layout
        /// instead of a cheaper repaint.
        public boolean isPlotRectIncludingAnnotations() {
            return includeAnnotationsInPlotRect;
        }

        @Override
        public boolean isValidateRoot() {
            return true;
        }

        private void initializeDirtyRepaintBounds() {
            dirtyRepaintBounds = new Rectangle2D.Double();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Object lock = getLock();
            synchronized (lock) {
                Graphics2D g2 = (Graphics2D) g;
                one.chartsy.charting.Chart.this.flushPendingDataSetEvents();
                if (super.isOpaque()) {
                    g2.setPaint(getBackgroundPaint());
                    if (!isPaintingImage())
                        G2D.fillRect(g, 0, 0, super.getWidth(), super.getHeight());
                    else {
                        Rectangle exportedDrawRect = activePaintContext.drawRect;
                        G2D.fillRect(g, 0, 0, exportedDrawRect.width, exportedDrawRect.height);
                    }
                }
                int stopShapeAntiAliasing;
                block:
                {
                    if (isAntiAliasing())
                        if (!GraphicUtil.startAntiAliasing(g)) {
                            stopShapeAntiAliasing = 1;
                            break block;
                        }
                    stopShapeAntiAliasing = 0;
                } // end block

                int stopTextAntiAliasing = stopShapeAntiAliasing;
                block_2:
                {
                    if (isAntiAliasingText())
                        if (!GraphicUtil.startTextAntiAliasing(g)) {
                            stopTextAntiAliasing = 1;
                            break block_2;
                        }
                    stopTextAntiAliasing = 0;
                } // end block_2

                g.setColor(super.getForeground());
                ChartDrawEvent drawEvent = new ChartDrawEvent(one.chartsy.charting.Chart.this, g);
                one.chartsy.charting.Chart.this.fireBeforeDraw(drawEvent);
                this.paintChartContents(g);
                g.setColor(super.getForeground());
                one.chartsy.charting.Chart.this.fireAfterDraw(drawEvent);
                if (stopShapeAntiAliasing != 0)
                    GraphicUtil.stopAntiAliasing(g);
                if (stopTextAntiAliasing != 0)
                    GraphicUtil.stopTextAntiAliasing(g);
            }
        }

        /// Paints the chart area immediately using the active chart paint context.
        ///
        /// This is the area-scoped counterpart to [Chart#paintCurrentThread(Graphics2D, Color)] and
        /// is primarily used by area-only export paths.
        ///
        /// @param g          the graphics context to paint into
        /// @param background the background override to use, or `null` to resolve the normal area
        ///                       background paint
        public void paintCurrentThread(Graphics2D g, Color background) {
            Object lock = getLock();
            synchronized (lock) {
                one.chartsy.charting.Chart.this.updateScales(getPlotRect());
                Paint resolvedBackground = background;
                if (resolvedBackground == null) {
                    resolvedBackground = getBackgroundPaint();
                    if (resolvedBackground == null)
                        resolvedBackground = Chart.findOpaqueBackground((Component) this);
                }
                if (resolvedBackground != null) {
                    g.setPaint(resolvedBackground);
                    G2D.fillRect(g, activePaintContext.sessionBounds.x,
                            activePaintContext.sessionBounds.y,
                            activePaintContext.sessionBounds.width,
                            activePaintContext.sessionBounds.height);
                }
                paintComponent(g);
                if (super.getBorder() != null)
                    super.getBorder().paintBorder(this, g, activePaintContext.drawRect.x,
                            activePaintContext.drawRect.y,
                            activePaintContext.drawRect.width,
                            activePaintContext.drawRect.height);
                invalidateScales();
            }
        }

        @Override
        protected void processKeyEvent(KeyEvent event) {
            if (interactorManager != null)
                interactorManager.processKeyEvent(event);
            super.processKeyEvent(event);
        }

        @Override
        protected void processMouseEvent(MouseEvent event) {
            MouseEvent filteredEvent = event;
            if (systemMouseEventFilter != null)
                filteredEvent = systemMouseEventFilter.filter(filteredEvent);
            if (filteredEvent.getID() == 501)
                if (!GraphicsEnvironment.isHeadless())
                    super.requestFocus();
            if (interactorManager != null)
                interactorManager.processMouseEvent(filteredEvent);
            super.processMouseEvent(filteredEvent);
        }

        @Override
        protected void processMouseMotionEvent(MouseEvent event) {
            MouseEvent filteredEvent = event;
            if (systemMouseEventFilter != null)
                filteredEvent = systemMouseEventFilter.filter(filteredEvent);
            if (interactorManager != null)
                interactorManager.processMouseMotionEvent(filteredEvent);
            super.processMouseMotionEvent(filteredEvent);
        }

        private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
            in.defaultReadObject();
            Chart.this.defaultUsingEventThread = Chart.this.isEventThreadUpdateEnabledByDefault();
            Chart.this.usingEventThread = Chart.this.defaultUsingEventThread;
            this.initializeDirtyRepaintBounds();
        }

        @Override
        public void repaint() {
            if (!isUsingEventThread())
                return;
            this.setFullRepaintPending(false);
            dirtyRepaintBounds.setRect(0.0, 0.0, 0.0, 0.0);
            if (!isDirectRedrawEnabled())
                super.repaint();
            else
                super.paintImmediately(0, 0, super.getWidth(), super.getHeight());
        }

        public void repaint(DataWindow window, int yAxis) {
            if (!isUsingEventThread())
                return;
            CoordinateSystem coordinateSystem = getCoordinateSystem(yAxis);
            window.intersection(coordinateSystem.getXAxis().getVisibleRange(), coordinateSystem.getYAxis().getVisibleRange());
            if (window.isEmpty())
                return;
            Rectangle damageRect = getProjector().toRectangle(window, getPlotRect(), coordinateSystem);
            super.repaint(damageRect.x - 1, damageRect.y - 1, damageRect.width + 2, damageRect.height + 2);
        }

        public final void repaint2D(Rectangle2D damageRect) {
            if (!damageRect.isEmpty())
                if (isUsingEventThread()) {
                    double flooredX = Math.floor(damageRect.getX());
                    double flooredY = Math.floor(damageRect.getY());
                    super.repaint((int) flooredX - 1, (int) flooredY - 1, (int) Math.ceil(damageRect.getX() - flooredX + damageRect.getWidth()) + 2,
                            (int) Math.ceil(damageRect.getY() - flooredY + damageRect.getHeight()) + 2);
                }
        }

        @Override
        public void revalidate() {
            Object lock = getLock();
            synchronized (lock) {
                if (!isUsingEventThread())
                    return;
                if (super.getParent() != null && SwingUtilities.isEventDispatchThread() && !super.isShowing()) {
                    super.invalidate();
                    super.validate();
                    return;
                }
                super.revalidate();
            }
        }

        /// Revalidates the chart-area layout while respecting renderer batching and export sessions.
        ///
        /// During nested renderer changes the request is deferred until the batch closes. During
        /// image export, where Swing invalidation is suppressed, the method drops transient plot
        /// caches instead of scheduling a live revalidate.
        public void revalidateLayout() {
            if (rendererChangeDepth > 0)
                rendererLayoutPending = true;
            else if (!isUsingEventThread())
                this.clearLayoutCachesAfterPaint();
            else {
                revalidate();
                this.repaint();
            }
        }

        /// Sets the paint used when the area clears its own background.
        ///
        /// @param backgroundPaint the background paint override, or `null` to use [#getBackground()]
        public void setBackgroundPaint(Paint backgroundPaint) {
            backgroundPaintOverride = backgroundPaint;
        }

        /// Fixes the bottom layout margin to an explicit pixel value.
        public void setBottomMargin(int bottomMargin) {
            this.getChartAreaLayout().setBottomMargin(bottomMargin);
        }

        @Override
        public void setBounds(int x, int y, int width, int height) {
            block:
            {
                if (x == super.getX())
                    if (y == super.getY())
                        break block;
                ChartAreaSynchronizer synchronizer = ChartAreaSynchronizer.getInstalledSynchronizer(one.chartsy.charting.Chart.this);
                if (synchronizer != null)
                    super.invalidate();
            } // end block

            int sizeChanged;
            block_2:
            {
                if (super.getWidth() == width)
                    if (super.getHeight() == height) {
                        sizeChanged = 0;
                        break block_2;
                    }
                sizeChanged = 1;
            } // end block_2

            int boundsChanged = sizeChanged;
            super.setBounds(x, y, width, height);
            if (boundsChanged != 0) {
                Rectangle drawRect = getDrawRect();
                if (previousDrawRect != null)
                    Chart.this.updateManualVisibleRangesAfterResize(previousDrawRect, drawRect);
                previousDrawRect = drawRect;
            }
        }

        /// Enables or disables immediate painting for repaint requests.
        ///
        /// When enabled, [#repaint()] paints synchronously through `paintImmediately(...)` instead
        /// of enqueueing a normal Swing repaint.
        public void setDirectRedrawEnabled(boolean directRedrawEnabled) {
            stateFlags.setFlag(FLAG_DIRECT_REDRAW_ENABLED, directRedrawEnabled);
        }

        /// Enables or disables filling the plot area shape.
        public void setFilledPlottingArea(boolean filledPlottingArea) {
            if (plotStyle != null)
                plotStyle = plotStyle.setFillOn(filledPlottingArea);
        }

        @Override
        public void setFont(Font font) {
            super.setFont(font);
            scaleFontWithChart = font != null;
            invalidateScales();
            if (isUsingEventThread()) {
                revalidate();
                this.repaint();
            }
        }

        /// Fixes the left and right layout margins to explicit pixel values.
        public void setHorizontalMargins(int leftMargin, int rightMargin) {
            this.getChartAreaLayout().setHorizontalMargins(leftMargin, rightMargin);
        }

        /// Installs the layout manager used by this chart area.
        ///
        /// Only [ChartAreaLayout] is supported because scale, grid, and plot-rectangle computation
        /// depend on its contract.
        @Override
        public void setLayout(LayoutManager layout) {
            if (!(layout instanceof ChartAreaLayout))
                throw new IllegalArgumentException("setLayout: layout must be an instance of ChartAreaLayout");
            if (super.getLayout() != null)
                ((ChartAreaLayout) super.getLayout()).detachArea();
            ((ChartAreaLayout) layout).attachArea(this);
            super.setLayout(layout);
        }

        /// Fixes the left layout margin to an explicit pixel value.
        public void setLeftMargin(int leftMargin) {
            this.getChartAreaLayout().setLeftMargin(leftMargin);
        }

        /// Replaces all explicit plot margins in one call.
        ///
        /// Passing `null` clears the fixed-margin override so the layout can fall back to computed
        /// margins.
        public void setMargins(Insets margins) {
            this.getChartAreaLayout().setMargins(margins);
        }

        public void setPlotBackground(Color[] colors, boolean useYAxisGradient) {
            if (colors.length == 0)
                throw new IllegalArgumentException("A least one color must be specified");
            Object fillPaint;
            if (colors.length == 1) {
                fillPaint = getPlotStyle();
                fillPaint = (fillPaint != null) ? ((PlotStyle) fillPaint).setFillPaint(colors[0]) : new PlotStyle(colors[0], super.getForeground());
                setPlotStyle((PlotStyle) fillPaint);
                return;
            }
            fillPaint = useYAxisGradient ? new Chart.Area.PlotGradient(getChart(), colors) : new Chart.Area.PlotGradient(getChart(), 0, colors);
            PlotStyle updatedPlotStyle = getPlotStyle();
            updatedPlotStyle = (updatedPlotStyle != null) ? updatedPlotStyle.setFillPaint((Paint) fillPaint) : new PlotStyle((Paint) fillPaint);
            setPlotStyle(updatedPlotStyle);
            stateFlags.setFlag(FLAG_PLOT_GRADIENT_ACTIVE, true);
        }

        public void setPlotBackground(Paint paint) {
            if (plotStyle != null)
                plotStyle = plotStyle.setFillPaint(paint);
        }

        /// Controls whether annotations contribute to the computed plot rectangle.
        ///
        /// @param includeAnnotations `true` to include annotations in plot-bounds calculation,
        ///                               `false` to keep the plot rectangle tied to axes and renderers alone
        public void setPlotRectIncludingAnnotations(boolean includeAnnotations) {
            if (includeAnnotations != includeAnnotationsInPlotRect) {
                includeAnnotationsInPlotRect = includeAnnotations;
                revalidateLayout();
            }
        }

        /// Replaces the plot style used for plot fill and stroke painting.
        ///
        /// Existing gradient resources are disposed before the new style is installed.
        ///
        /// @param plotStyle the new plot style, or `null` to clear plot fill and stroke decoration
        public void setPlotStyle(PlotStyle plotStyle) {
            if (stateFlags.getFlag(FLAG_PLOT_GRADIENT_ACTIVE)) {
                ValueGradientPaint plotGradient = this.getPlotGradient();
                plotGradient.dispose();
                stateFlags.setFlag(FLAG_PLOT_GRADIENT_ACTIVE, false);
            }
            if (plotStyle != null)
                if (plotStyle.getStrokePaint() != super.getForeground())
                    stateFlags.setFlag(FLAG_PLOT_STYLE_TRACKS_FOREGROUND, false);
            this.plotStyle = plotStyle;
        }

        /// Fixes the right layout margin to an explicit pixel value.
        public void setRightMargin(int rightMargin) {
            this.getChartAreaLayout().setRightMargin(rightMargin);
        }

        /// Fixes the top layout margin to an explicit pixel value.
        public void setTopMargin(int topMargin) {
            this.getChartAreaLayout().setTopMargin(topMargin);
        }

        /// Fixes the top and bottom layout margins to explicit pixel values.
        public void setVerticalMargins(int topMargin, int bottomMargin) {
            this.getChartAreaLayout().setVerticalMargins(topMargin, bottomMargin);
        }

        /// Renders only the chart area into a buffered image using its current component size.
        ///
        /// @param image           the image to reuse, or `null` to allocate one sized from the current area
        /// @param clearBackground whether the export should clear the image before painting
        /// @return the rendered image
        public BufferedImage toImage(BufferedImage image, boolean clearBackground) {
            return Chart.paintToImage(this, new Chart.Area.ChartAreaPaintAction(/* TODO: hidden print window */null), image, clearBackground);
        }

        /// Renders only the chart area into a new image of the requested size.
        ///
        /// @param width      the export width in pixels
        /// @param height     the export height in pixels
        /// @param background the background override to use, or `null` to resolve the normal area
        ///                       background
        /// @return the rendered image
        public BufferedImage toImage(int width, int height, Color background) {
            return Chart.this.paintToImage(width, height, background, this);
        }

        /// Writes only the chart area as PNG using its current component size.
        ///
        /// @param out the destination stream
        /// @throws IOException if the PNG write fails
        public void toPNG(OutputStream out) throws IOException {
            this.toPNG(out, false);
        }

        /// Writes only the chart area as PNG using its current component size.
        ///
        /// @param out                   the destination stream
        /// @param transparentBackground `true` to encode the nearest opaque ancestor background as
        ///                                  transparent PNG metadata
        /// @throws IOException if the PNG write fails
        public void toPNG(OutputStream out, boolean transparentBackground) throws IOException {
            BufferedImage image = this.toImage(null, true);
            Color transparentColor = (!transparentBackground) ? null : Chart.findOpaqueBackground((Component) this);
            Chart.writePng(image, out, transparentColor);
        }

    }

    /// Chart type backed by the rectangular [CartesianProjector].
    public static final int CARTESIAN = 1;
    /// Chart type backed by the radial [PolarProjector].
    public static final int POLAR = 2;
    /// Chart type that renders one or more datasets as pie slices.
    public static final int PIE = 3;
    /// Chart type that renders category spokes around a radial center.
    public static final int RADAR = 4;
    /// Chart type reserved for treemap-style renderers.
    public static final int TREEMAP = 5;
    /// Drawable order value for content that must paint before chart renderers.
    public static final int DRAW_BELOW = -1;
    /// Drawable order value for content that must paint after chart renderers.
    public static final int DRAW_ABOVE = 1;
    private static final int DEFAULT_PREFERRED_HEIGHT = 100;
    private static final int DEFAULT_EXPORT_WIDTH = 320;
    private static final int DEFAULT_PREFERRED_WIDTH = 200;
    private static final int DEFAULT_EXPORT_HEIGHT = 200;
    private static boolean noEventThreadUpdate = false;
    private transient boolean defaultUsingEventThread;
    private transient boolean usingEventThread;
    private static final int FLAG_AXIS_CHANGE_UPDATES_ENABLED = 16;
    private static final int FLAG_SHIFT_SCROLL_ENABLED = 32;
    private static final int FLAG_LAYOUT_LOCKED = 256;
    private static final int FLAG_PLOT_GRADIENT_ACTIVE = 512;
    private static final int FLAG_DIRECT_REDRAW_ENABLED = 2048;
    private static final int FLAG_PLOT_STYLE_TRACKS_FOREGROUND = 4096;
    private static final int FLAG_DYNAMIC_STYLING = 0x4000;
    private static final int FLAG_OPTIMIZED_REPAINT = 0x8000;
    private static final int FLAG_CURSOR_EXPLICITLY_SET = 0x10000;
    private static final int FLAG_AUTO_SCALE_TITLE_ROTATION = 0x20000;
    private static final int DEFAULT_STATE_FLAGS = 0xd010;
    private static final String DEFAULT_LEGEND_POSITION = "North_Bottom";
    static final String CHART_COMPONENT_CLIENT_PROPERTY = "__Chart_Component__";
    private static final ChartInteractor[] EMPTY_INTERACTORS = new ChartInteractor[0];
    private static HashMap<BoundedRangeModel, BoundedRangeModelConnector> boundedRangeModelConnectors = null;
    static final Color DEFAULT_PLOT_FILL_COLOR = Color.white;
    public static final int STYLE_DEFAULT_MASK = 384;
    private final ArrayList<Chart.AxisElement> axisElements;
    private AxisListener sharedAxisListener;
    private ChartProjector projector;
    private Double savedStartingAngle;
    private double savedAngleRange;
    private final ArrayList<ChartRenderer> renderers;
    private ArrayList<ChartDecoration> decorations;
    private final ArrayList<ChartDrawable> drawables;
    private final Flags stateFlags;
    private double scrollRatio;
    private int baseTextDirection;
    private transient Chart.JLabelTextChangeListener labelTextChangeListener;
    private ChartConfig chartConfig;
    private int defaultRendererType;
    private EventListenerList chartAreaListeners;
    private EventListenerList rendererChangeListeners;
    private EventListenerList drawListeners;
    private ChartInteractorManager interactorManager;
    private transient Point floatingLegendLocation;
    private String legendPosition;
    private Chart.Area chartArea;
    private JComponent header;
    private JComponent footer;
    private Legend legend;
    private JScrollBar xScrollBar;
    private ChartResizingPolicy resizingPolicy;
    private DataRangePolicy dataRangePolicy;
    private transient boolean fontManagerReferenceSizeDirty;
    private Color[] fallbackRendererColors;
    // TODO: TBR
    // private transient ChartPrintContext bk;
    private Chart.ChartPaintContext activePaintContext;
    transient boolean scalesUpToDate;
    HashMap<RendererDataSetKey, DataSetEventAccumulator> pendingDataSetEventsByKey;
    private Color backgroundOverride;
    private Paint backgroundPaint;
    private static final String TEXT_PROPERTY_NAME = "text";
    transient int rendererChangeDepth;
    transient boolean rendererLayoutPending;
    // private URL bu;
    /* synthetic */ static final boolean bv;

    /// Sets the process-wide default for new charts' Swing event-thread updates.
    ///
    /// When enabled, newly constructed charts start with `repaint()` and `revalidate()` forwarding
    /// disabled until a caller explicitly re-enables event-thread updates on the instance.
    ///
    /// @param noEventThreadUpdate `true` to disable automatic event-thread updates by default for
    ///                                future chart instances
    public static void setNoEventThreadUpdate(boolean noEventThreadUpdate) {
        Chart.noEventThreadUpdate = noEventThreadUpdate;
    }

    /// Returns whether new chart instances start with event-thread updates disabled.
    ///
    /// @return `true` when freshly constructed charts suppress automatic `repaint()` and
    ///     `revalidate()` forwarding until re-enabled per instance
    public static boolean getNoEventThreadUpdate() {
        return Chart.noEventThreadUpdate;
    }

    /// Returns whether newly created charts should forward Swing invalidation work by default.
    private boolean isEventThreadUpdateEnabledByDefault() {
        return !Chart.getNoEventThreadUpdate();
    }

    /// Enables or disables automatic Swing invalidation work for this chart.
    ///
    /// When disabled, `repaint()` and `revalidate()` become no-ops on both the chart and its
    /// [Area]. Export sessions also temporarily force this flag off while an off-screen paint pass
    /// is in progress.
    ///
    /// @param usingEventThread `true` to forward `repaint()` and `revalidate()` to Swing, `false`
    ///                             to suppress them
    public void setUsingEventThread(boolean usingEventThread) {
        defaultUsingEventThread = usingEventThread;
        this.usingEventThread = usingEventThread;
    }

    /// Returns whether this chart currently forwards invalidation work to Swing.
    ///
    /// The returned value can temporarily differ from the default configured through
    /// [#setUsingEventThread(boolean)] while an image-export session is active.
    ///
    /// @return `true` when `repaint()` and `revalidate()` currently propagate to Swing
    public boolean isUsingEventThread() {
        return usingEventThread;
    }

    boolean isUsingEventThreadByDefault() {
        return defaultUsingEventThread;
    }

    static void assertNotOnEventDispatchThreadForDetachedChart() {
        if (Chart.getNoEventThreadUpdate())
            if (SwingUtilities.isEventDispatchThread()) {
                boolean holdingTreeLock = Thread.holdsLock(new Container().getTreeLock());
                String message = "Executing in AWT Thread!! holdingAWTTreeLock=" + holdingTreeLock;
                if (holdingTreeLock)
                    throw new RuntimeException(message);
                try {
                    throw new RuntimeException(message);
                } catch (Exception e1) {
                    e1.printStackTrace();
                }
            }
    }

    void assertNotOnEventDispatchThread() {
        this.assertNotOnEventDispatchThread(false);
    }

    void assertNotOnEventDispatchThread(boolean allowUnparentedComponent) {
        block:
        if (!this.isUsingEventThreadByDefault())
            if (SwingUtilities.isEventDispatchThread()) {
                if (allowUnparentedComponent)
                    if (super.getParent() == null)
                        break block;
                boolean holdingTreeLock = Thread.holdsLock(new Container().getTreeLock());
                String message = "Executing in AWT Thread!! holdingAWTTreeLock=" + holdingTreeLock +
                        " " + this;
                if (!holdingTreeLock)
                    try {
                        throw new RuntimeException(message);
                    } catch (Exception e1) {
                        e1.printStackTrace();
                    }
                else {
                    System.err.println("parents:");
                    Container parent = this;
                    while (true) {
                        if (parent == null)
                            break;
                        System.err.println(parent);
                        parent = parent.getParent();
                    }
                    throw new RuntimeException(message);
                }
            }

    }

    /// Returns the monitor used to guard shared chart state.
    ///
    /// The implementation currently delegates to Swing's tree lock so axes, scales, layout caches,
    /// and export sessions observe a consistent view of the component tree.
    ///
    /// @return the synchronization lock used by this chart and its nested helpers
    // TODO: remove all treeLock uses
    public Object getLock() {
        // ManagerView view = JComponentGraphic.getContainingView(this);
        // if (view == null) {
        return super.getTreeLock();
        // }
        // return view.getManager().getTreeLock();
    }

    /// Creates the fallback formatter used when neither a [Scale] nor a [ValueFormatter] supplies
    /// an axis label.
    private NumberFormat createFallbackNumberFormat() {
        return NumberFormatFactory.getInstance(getLocale());
    }

    /// Creates a Cartesian chart with its default scales and grids installed.
    public Chart() {
        this(1);
    }

    /// Creates a chart of the requested type with its default scales and grids installed.
    ///
    /// @param type one of [#CARTESIAN], [#POLAR], [#PIE], [#RADAR], or [#TREEMAP]
    public Chart(int type) {
        this(type, true);
    }

    /// Creates a chart of the requested type and optionally installs the default scales and grids
    /// for that configuration.
    ///
    /// The constructor always creates the base x/y axes, chart area, projector, and font manager.
    /// When `installDefaultScaleAndGrid` is `false`, callers are expected to attach scales and
    /// grids explicitly before relying on layout or export.
    ///
    /// @param type                       one of [#CARTESIAN], [#POLAR], [#PIE], [#RADAR], or [#TREEMAP]
    /// @param installDefaultScaleAndGrid `true` to create the type's default scales and grids for
    ///                                       every axis immediately
    public Chart(int type, boolean installDefaultScaleAndGrid) {
        defaultUsingEventThread = isEventThreadUpdateEnabledByDefault();
        usingEventThread = defaultUsingEventThread;
        axisElements = new ArrayList(2);
        sharedAxisListener = null;
        projector = new CartesianProjector();
        savedStartingAngle = null;
        savedAngleRange = 360.0;
        renderers = new ArrayList();
        drawables = new ArrayList();
        stateFlags = new Flags(DEFAULT_STATE_FLAGS);
        baseTextDirection = 514;
        defaultRendererType = 5;
        interactorManager = null;
        floatingLegendLocation = null;
        legendPosition = DEFAULT_LEGEND_POSITION;
        chartArea = null;
        header = null;
        footer = null;
        legend = null;
        xScrollBar = null;
        resizingPolicy = null;
        dataRangePolicy = new DefaultDataRangePolicy();
        fontManagerReferenceSizeDirty = true;
        scalesUpToDate = false;
        pendingDataSetEventsByKey = new HashMap();
        setLocale(Locale.getDefault());
        updateUI();
        stateFlags.setFlag(FLAG_LAYOUT_LOCKED, true);
        setLayout(new ChartLayout());
        chartArea = new Chart.Area();
        super.add(chartArea, "Center");
        Axis xAxis = new Axis(1);
        Axis yAxis = new Axis(2);
        CoordinateSystem coordinateSystem = new CoordinateSystem(xAxis, yAxis);
        axisElements.add(new Chart.AxisElement(xAxis, coordinateSystem, -1));
        axisElements.add(new Chart.AxisElement(yAxis, coordinateSystem, 0));
        this.setTypeInternal(type, installDefaultScaleAndGrid);
        if (Beans.isDesignTime())
            setDataSource(new DefaultDataSource(createDesignTimeSampleDataSets()));
        ScalableFontManager fontManager = new ScalableFontManager(this, null, 0.0f, 0.0f);
        ScalableFontManager.setFontManager(getChartArea(), fontManager, null);
        super.addContainerListener(new ContainerAdapter() {
            @Override
            public void componentRemoved(ContainerEvent event) {
                ScalableFontManager.setFontManager(event.getChild(), null, null);
            }
        });
    }

    @Override
    public void setComponentOrientation(ComponentOrientation orientation) {
        ComponentOrientation previousOrientation = super.getComponentOrientation();
        super.setComponentOrientation(orientation);
        if (orientation != previousOrientation)
            componentOrientationChanged(previousOrientation, orientation);
    }

    /// Reacts to a component-orientation change after the Swing state has been updated.
    ///
    /// The default implementation propagates the new orientation to attached scales and
    /// decorations, and it recomputes label shaping when the chart's base-text direction follows
    /// component orientation.
    ///
    /// @param oldOrientation the orientation that was active before the change
    /// @param newOrientation the orientation now installed on the component
    protected void componentOrientationChanged(ComponentOrientation oldOrientation, ComponentOrientation newOrientation) {
        block:
        if (newOrientation.isLeftToRight() != oldOrientation.isLeftToRight()) {
            if (this.getConfiguredBaseTextDirection() != 514)
                if (getResolvedBaseTextDirection() != 527)
                    break block;
            baseTextDirectionChanged();
        } // end block

        int axisElementCount = axisElements.size();
        int axisElementIndex = 0;
        while (true) {
            if (axisElementIndex >= axisElementCount)
                break;
            Scale scale = this.getAxisElement(axisElementIndex).getScale();
            if (scale != null)
                scale.componentOrientationChanged(oldOrientation, newOrientation);
            axisElementIndex++;
        }
        if (decorations != null) {
            int decorationCount = decorations.size();
            int decorationIndex = 0;
            while (true) {
                if (decorationIndex >= decorationCount)
                    break;
                ChartDecoration decoration = decorations.get(decorationIndex);
                decoration.componentOrientationChanged(oldOrientation, newOrientation);
                decorationIndex++;
            }
        }
    }

    @Override
    public void updateUI() {
        super.updateUI();
        LookAndFeel.installColorsAndFont(this, "Panel.background", "Label.foreground", "Label.font");
    }

    @Override
    public void setBounds(int x, int y, int width, int height) {
        int previousWidth = super.getWidth();
        int previousHeight = super.getHeight();
        int sizeChanged;
        block:
        {
            if (previousWidth == width)
                if (previousHeight == height) {
                    sizeChanged = 0;
                    break block;
                }
            sizeChanged = 1;
        } // end block

        int shouldResizeLegend = sizeChanged;
        super.setBounds(x, y, width, height);
        if (shouldResizeLegend != 0)
            if (legend != null)
                if (legend.isFloating())
                    if (isUsingEventThread())
                        EventQueue
                                .invokeLater(() -> legend.setSize(legend.getPreferredSize()));
                    else
                        legend.setSize(legend.getPreferredSize());
    }

    /// Creates the placeholder datasets shown by GUI builders in design mode.
    private DataSet[] createDesignTimeSampleDataSets() {
        double[][] values = new double[2][];
        values[0] = new double[]{
                1.0, 10.0, 8.0, 13.0, 18.0, 23.0, 28.0, 25.0, 29.0, 31.0, 33.0, 37.0, 38.0,
                35.0, 31.0, 27.0, 31.0, 24.0, 21.0, 16.0, 12.0, 13.0, 16.0, 11.0, 13.0, 12.0
        };
        values[1] = new double[]{
                22.0, 18.0, 19.0, 21.0, 16.0, 13.0, 11.0, 15.0, 18.0, 13.0, 16.0, 18.0, 21.0,
                23.0, 24.0, 23.0, 26.0, 31.0, 32.0, 34.0, 29.0, 31.0, 27.0, 29.0, 28.0, 27.0
        };
        String[] names = {"DataSet 1", "DataSet 2"};
        return DefaultDataSet.create(values, -1, names, null);
    }

    /// Lazily creates the listener shared by every axis owned by this chart.
    private AxisListener getOrCreateSharedAxisListener() {
        if (sharedAxisListener == null) {
            synchronized (this) {
                if (sharedAxisListener == null)
                    sharedAxisListener = createSharedAxisListener();
            }
        }
        return sharedAxisListener;
    }

    /// Creates the listener that bridges owned-axis changes back into chart layout state.
    private AxisListener createSharedAxisListener() {
        return new ChartAxisListener();
    }

    public final boolean isAntiAliasing() {
        return stateFlags.getFlag(1);
    }

    /// Enables or disables geometric anti-aliasing for subsequent paint passes.
    ///
    /// @param antiAliasing `true` to render shapes with anti-aliasing enabled
    public void setAntiAliasing(boolean antiAliasing) {
        stateFlags.setFlag(1, antiAliasing);
    }

    public final boolean isAntiAliasingText() {
        return stateFlags.getFlag(2);
    }

    /// Enables or disables text anti-aliasing for labels and annotations.
    ///
    /// Text metrics affect scale layout, so changing this setting invalidates scale caches and the
    /// chart area's layout.
    ///
    /// @param antiAliasingText `true` to enable anti-aliased text rendering
    public void setAntiAliasingText(boolean antiAliasingText) {
        stateFlags.setFlag(2, antiAliasingText);
        invalidateScales();
        getChartArea().revalidateLayout();
    }

    public final int getBaseTextDirection() {
        return baseTextDirection;
    }

    /// Sets how chart-owned labels resolve bidirectional text.
    ///
    /// Accepted values are the direction constants already used by the surrounding Swing and
    /// charting code. When the effective direction changes, scales, decorations, legend text, and
    /// header/footer labels are invalidated and refreshed.
    ///
    /// @param baseTextDirection the requested base-direction constant
    /// @throws IllegalArgumentException if `baseTextDirection` is not one of the supported
    ///                                      direction constants
    public void setBaseTextDirection(int baseTextDirection) {
        if (baseTextDirection != 514)
            if (baseTextDirection != 527)
                if (baseTextDirection != 516)
                    if (baseTextDirection != 520)
                        if (baseTextDirection != 513)
                            throw new IllegalArgumentException();
        if (baseTextDirection != this.baseTextDirection) {
            int previousResolvedBaseTextDirection = getResolvedBaseTextDirection();
            this.baseTextDirection = baseTextDirection;
            if (getResolvedBaseTextDirection() != previousResolvedBaseTextDirection)
                baseTextDirectionChanged();
        }
    }

    int getConfiguredBaseTextDirection() {
        int baseTextDirection = getBaseTextDirection();
        if (baseTextDirection == 513)
            baseTextDirection = 514;
        return baseTextDirection;
    }

    /// Returns the effective base text direction currently used for chart-owned labels.
    ///
    /// When [#getBaseTextDirection()] is configured to follow component orientation, this resolves
    /// the current [ComponentOrientation] into a concrete left-to-right or right-to-left value.
    ///
    /// @return the resolved base-direction constant
    public int getResolvedBaseTextDirection() {
        int configuredBaseTextDirection = this.getConfiguredBaseTextDirection();
        if (configuredBaseTextDirection != 514)
            return configuredBaseTextDirection;
        return (!super.getComponentOrientation().isLeftToRight()) ? 520 : 516;
    }

    /// Invalidates layout and text after the effective base text direction changes.
    ///
    /// Subclasses overriding this hook should preserve the default propagation unless they replace
    /// the scale, decoration, and header/footer infrastructure entirely.
    protected void baseTextDirectionChanged() {
        int axisElementCount = axisElements.size();
        int axisElementIndex = 0;
        while (true) {
            if (axisElementIndex >= axisElementCount)
                break;
            Scale scale = this.getAxisElement(axisElementIndex).getScale();
            if (scale != null)
                scale.baseTextDirectionChanged();
            axisElementIndex++;
        }
        if (decorations != null) {
            int decorationCount = decorations.size();
            int decorationIndex = 0;
            while (true) {
                if (decorationIndex >= decorationCount)
                    break;
                ChartDecoration decoration = decorations.get(decorationIndex);
                decoration.baseTextDirectionChanged();
                decorationIndex++;
            }
        }
        invalidateScales();
        getChartArea().revalidateLayout();
        if (legend != null)
            if (legend.getBaseTextDirection() == 513)
                legend.baseTextDirectionChanged();
        boolean relayoutRequested = false;
        if (this.header instanceof JLabel) {
            String headerText = ((JLabel) header).getText();
            getOrCreateLabelTextChangeListener()
                    .propertyChange(new PropertyChangeEvent(header, TEXT_PROPERTY_NAME, headerText, headerText));
            relayoutRequested = true;
        }
        if (this.footer instanceof JLabel) {
            String footerText = ((JLabel) footer).getText();
            getOrCreateLabelTextChangeListener()
                    .propertyChange(new PropertyChangeEvent(footer, TEXT_PROPERTY_NAME, footerText, footerText));
            relayoutRequested = true;
        }
        if (relayoutRequested)
            revalidate();
        this.repaint();
    }

    /// Returns whether renderer, axis, and dataset mutations should trigger immediate refresh work.
    boolean areRefreshUpdatesEnabled() {
        return stateFlags.getFlag(FLAG_AXIS_CHANGE_UPDATES_ENABLED);
    }

    /// Temporarily suppresses or restores repaint and layout work during compound updates.
    void setRefreshUpdatesEnabled(boolean refreshUpdatesEnabled) {
        stateFlags.setFlag(FLAG_AXIS_CHANGE_UPDATES_ENABLED, refreshUpdatesEnabled);
    }

    @Override
    public void setCursor(Cursor cursor) {
        stateFlags.setFlag(FLAG_CURSOR_EXPLICITLY_SET, cursor != null);
        super.setCursor(cursor);
    }

    @Override
    public boolean isCursorSet() {
        return stateFlags.getFlag(FLAG_CURSOR_EXPLICITLY_SET);
    }

    public final boolean isOptimizedRepaint() {
        return stateFlags.getFlag(FLAG_OPTIMIZED_REPAINT);
    }

    public void setOptimizedRepaint(boolean optimizedRepaint) {
        stateFlags.setFlag(FLAG_OPTIMIZED_REPAINT, optimizedRepaint);
    }

    public boolean isShiftScroll() {
        return stateFlags.getFlag(FLAG_SHIFT_SCROLL_ENABLED);
    }

    public void setShiftScroll(boolean shiftScroll) {
        stateFlags.setFlag(FLAG_SHIFT_SCROLL_ENABLED, shiftScroll);
    }

    public double getScrollRatio() {
        return scrollRatio;
    }

    public void setScrollRatio(double scrollRatio) {
        this.scrollRatio = scrollRatio;
    }

    /// Returns the current chart type configuration.
    ///
    /// @return one of [#CARTESIAN], [#POLAR], [#PIE], [#RADAR], or [#TREEMAP]
    public int getType() {
        return chartConfig.getType();
    }

    /// Switches the chart to a different type configuration.
    ///
    /// The active [ChartConfig] is replaced, the projector is recreated, and the default scales and
    /// grids for the new type are installed on all existing axes.
    ///
    /// @param type the requested chart type constant
    public void setType(int type) {
        this.setTypeInternal(type, true);
    }

    private void setTypeInternal(int type, boolean installDefaultScaleAndGrid) {
        Object lock = getLock();
        synchronized (lock) {
            block:
            {
                if (chartConfig != null)
                    if (chartConfig.getType() == type)
                        break block;
                ChartConfig nextChartConfig = ChartConfig.forType(type);
                if (chartConfig != null)
                    chartConfig.applyToChart(null, installDefaultScaleAndGrid);
                chartConfig = nextChartConfig;
                if (chartConfig != null)
                    chartConfig.applyToChart(this, installDefaultScaleAndGrid);
                invalidateScales();
                getChartArea().revalidateLayout();
            } // end block

        }
    }

    /// Updates the stored polar-projector settings in one call.
    ///
    /// The values are always remembered, even while the chart is not currently polar. When the
    /// active projector is polar, the projector is updated immediately and scale layout is
    /// invalidated only if an effective value changes.
    ///
    /// @param startingAngle the angular zero direction in degrees
    /// @param angleRange    the angular span in degrees
    /// @param symmetric     whether the radial projector should force a symmetric origin
    public void setPolarParameters(double startingAngle, double angleRange, boolean symmetric) {
        savedStartingAngle = Double.valueOf(startingAngle);
        savedAngleRange = angleRange;
        block:
        if (getType() == 2) {
            PolarProjector projector = (PolarProjector) getProjector2D();
            if (startingAngle == projector.getStartingAngle())
                if (angleRange == projector.getRange())
                    if (symmetric == projector.isSymmetric())
                        break block;
            projector.setStartingAngle(startingAngle);
            projector.setRange(angleRange);
            projector.setSymmetric(symmetric);
            refreshScaleTitleRotations();
        } // end block

    }

    /// Returns the effective starting angle for polar-style charts.
    ///
    /// For active polar, pie, and radar charts this reads from the current projector. For other
    /// chart types it returns the last value supplied through [#setStartingAngle(double)] or
    /// [#setPolarParameters(double, double, boolean)].
    ///
    /// @return the starting angle in degrees
    public final double getStartingAngle() {
        if (getType() != 2)
            if (getType() != 4)
                if (getType() != 3) {
                    return (savedStartingAngle == null) ? 0.0 : savedStartingAngle.doubleValue();
                }
        return ((PolarProjector) getProjector2D()).getStartingAngle();
    }

    /// Updates the stored starting angle and, when applicable, the active projector.
    ///
    /// @param startingAngle the new starting angle in degrees
    public void setStartingAngle(double startingAngle) {
        savedStartingAngle = Double.valueOf(startingAngle);
        block:
        {
            if (getType() != 2)
                if (getType() != 4)
                    if (getType() != 3)
                        break block;
            PolarProjector projector = (PolarProjector) getProjector2D();
            if (startingAngle != projector.getStartingAngle()) {
                projector.setStartingAngle(startingAngle);
                refreshScaleTitleRotations();
            }
        } // end block

    }

    final double getStoredStartingAngleOrZero() {
        double startingAngle = (savedStartingAngle == null) ? 0.0 : savedStartingAngle.doubleValue();
        return startingAngle;
    }

    final double getStoredStartingAngleOrRightAngle() {
        double startingAngle = (savedStartingAngle == null) ? 90.0 : savedStartingAngle.doubleValue();
        return startingAngle;
    }

    /// Returns the effective angular span for polar-style charts.
    ///
    /// @return the angular range in degrees
    public final double getAngleRange() {
        if (getType() != 2)
            if (getType() != 3)
                return savedAngleRange;
        return ((PolarProjector) getProjector2D()).getRange();
    }

    /// Updates the stored angular span and, when applicable, the active projector.
    ///
    /// @param angleRange the new angular span in degrees
    public void setAngleRange(double angleRange) {
        savedAngleRange = angleRange;
        block:
        {
            if (getType() != 2)
                if (getType() != 3)
                    break block;
            PolarProjector projector = (PolarProjector) getProjector2D();
            if (angleRange != projector.getRange()) {
                projector.setRange(angleRange);
                refreshScaleTitleRotations();
            }
        } // end block

    }

    final double getStoredAngleRange() {
        return savedAngleRange;
    }

    /// Returns whether the active Cartesian projector paints in reversed orientation.
    ///
    /// @return `true` when the current Cartesian projector is reversed
    public boolean isProjectorReversed() {
        if (getType() != 1)
            return false;
        return ((AbstractProjector) getProjector2D()).isReversed();
    }

    /// Reverses or restores the active Cartesian projector orientation.
    ///
    /// This setting is ignored for non-Cartesian chart types.
    ///
    /// @param reversed `true` to reverse the Cartesian projector
    public void setProjectorReversed(boolean reversed) {
        if (getType() != 1)
            return;
        AbstractProjector projector = (AbstractProjector) getProjector2D();
        if (reversed != projector.isReversed()) {
            projector.setReversed(reversed);
            refreshScaleTitleRotations();
        }
    }

    /// Recomputes scale title geometry after projector-related settings change.
    private void refreshScaleTitleRotations() {
        int axisElementIndex = axisElements.size();
        while (true) {
            axisElementIndex--;
            if (axisElementIndex < 0)
                break;
            Scale scale = this.getAxisElement(axisElementIndex).getScale();
            if (scale != null) {
                scale.getSteps().invalidateValues();
                scale.updateAutoTitleRotation(isAutoScaleTitleRotation(), false);
            }
        }
        getChartArea().revalidateLayout();
    }

    final ChartConfig getChartConfig() {
        return chartConfig;
    }

    /// Returns a defensive copy of the default color palette used for fallback renderer styling.
    ///
    /// @return a copied palette, or `null` when no explicit fallback palette is configured
    public Color[] getDefaultColors() {
        if (fallbackRendererColors == null)
            return null;
        Color[] defaultColors = new Color[fallbackRendererColors.length];
        System.arraycopy(fallbackRendererColors, 0, defaultColors, 0, fallbackRendererColors.length);
        return defaultColors;
    }

    /// Replaces the default color palette used when renderers need fallback colors.
    ///
    /// The supplied array is copied before it becomes chart state.
    ///
    /// @param colors the new fallback palette, or `null` to clear the explicit palette
    public void setDefaultColors(Color[] colors) {
        Color[] previousColors = fallbackRendererColors;
        if (colors == null)
            fallbackRendererColors = null;
        else {
            fallbackRendererColors = new Color[colors.length];
            System.arraycopy(colors, 0, fallbackRendererColors, 0, colors.length);
        }
        super.firePropertyChange("defaultColors", previousColors, fallbackRendererColors);
    }

    /// Returns the scalable font manager currently attached to this chart.
    ///
    /// @return the chart's font manager, or `null` when scaling support is unavailable
    public final ScalableFontManager getFontManager() {
        return ScalableFontManager.getFontManager(this);
    }

    /// Returns whether chart-managed font scaling is currently enabled.
    public boolean isScalingFont() {
        ScalableFontManager fontManager = getFontManager();
        return fontManager != null && fontManager.isEnabled();
    }

    /// Enables or disables chart-driven font scaling.
    ///
    /// @param scalingFont `true` to let the chart's [ScalableFontManager] derive scaled fonts for
    ///                        managed children
    public void setScalingFont(boolean scalingFont) {
        ScalableFontManager fontManager = getFontManager();
        if (fontManager != null)
            fontManager.setEnabled(scalingFont);
    }

    final Chart.AxisElement getAxisElement(int axisElementIndex) {
        return axisElements.get(axisElementIndex);
    }

    final Chart.AxisElement getAxisElement(Axis axis) {
        int index = axisElements.size();
        while (true) {
            index--;
            if (index < 0)
                return null;
            if (this.getAxisElement(index).getAxis() == axis)
                break;
        }
        return this.getAxisElement(index);
    }

    /// Returns the scale currently attached to the given axis.
    ///
    /// @param axis the axis whose scale should be returned
    /// @return the attached scale, or `null` when that axis currently has no scale
    public final Scale getScale(Axis axis) {
        Chart.AxisElement axisElement = this.getAxisElement(axis);
        Object scale = (axisElement == null) ? null : axisElement.getScale();
        return (Scale) scale;
    }

    /// Returns whether attached scales currently choose their own preferred title rotation.
    public final boolean isAutoScaleTitleRotation() {
        return stateFlags.getFlag(FLAG_AUTO_SCALE_TITLE_ROTATION);
    }

    /// Enables or disables automatic title-rotation management for attached scales.
    ///
    /// When enabled, every currently attached scale is asked to recompute its preferred title
    /// rotation immediately.
    ///
    /// @param autoScaleTitleRotation `true` to let scales manage title rotation automatically
    public void setAutoScaleTitleRotation(boolean autoScaleTitleRotation) {
        if (autoScaleTitleRotation != isAutoScaleTitleRotation()) {
            stateFlags.setFlag(FLAG_AUTO_SCALE_TITLE_ROTATION, autoScaleTitleRotation);
            if (autoScaleTitleRotation)
                this.updateScaleTitleRotations(autoScaleTitleRotation, true);
        }
    }

    private void updateScaleTitleRotations(boolean autoScaleTitleRotation, boolean revalidateLayout) {
        int index = axisElements.size();
        while (true) {
            index--;
            if (index < 0)
                break;
            Scale scale = this.getAxisElement(index).getScale();
            if (scale != null)
                scale.updateAutoTitleRotation(autoScaleTitleRotation, false);
        }
        if (revalidateLayout)
            getChartArea().revalidateLayout();
    }

    public Axis getXAxis() {
        return this.getAxisElement(0).getAxis();
    }

    public final boolean isXAxisReversed() {
        return getXAxis().isReversed();
    }

    public void setXAxisReversed(boolean reversed) {
        getXAxis().setReversed(reversed);
    }

    public final Scale getXScale() {
        return this.getAxisElement(0).getScale();
    }

    /// Attaches the x-axis scale for this chart.
    ///
    /// The supplied scale must not already belong to another chart.
    ///
    /// @param scale the scale to attach, or `null` to remove the current x-axis scale
    /// @throws IllegalArgumentException if `scale` is already attached to another chart
    public void setXScale(Scale scale) {
        Object lock = getLock();
        synchronized (lock) {
            if (scale != null)
                if (scale.getAxisElement() != null)
                    throw new IllegalArgumentException("Scale already attached to a chart");
            this.getAxisElement(0).setScale(scale);
        }
    }

    public boolean isXScaleVisible() {
        return getXScale() != null && getXScale().isVisible();
    }

    public void setXScaleVisible(boolean visible) {
        if (getXScale() == null)
            return;
        getXScale().setVisible(visible);
    }

    public String getXScaleTitle() {
        return (getXScale() == null) ? null : getXScale().getTitle();
    }

    public void setXScaleTitle(String title) {
        if (getXScale() == null)
            return;
        getXScale().setTitle(title, getXScale().getTitleRenderer().getRotation());
    }

    public double getXScaleTitleRotation() {
        return (getXScale() == null) ? 0.0 : getXScale().getTitleRenderer().getRotation();
    }

    public void setXScaleTitleRotation(double rotation) {
        if (getXScale() == null)
            return;
        setAutoScaleTitleRotation(false);
        getXScale().getTitleRenderer().setRotation(rotation);
    }

    public int getYAxisCount() {
        return axisElements.size() - 1;
    }

    public Axis getYAxis(int yAxis) {
        return this.getAxisElement(yAxis + 1).getAxis();
    }

    public final boolean isYAxisReversed() {
        return getYAxis(0).isReversed();
    }

    public void setYAxisReversed(boolean reversed) {
        getYAxis(0).setReversed(reversed);
    }

    /// Adds a new y axis at the end of the current y-axis list.
    ///
    /// Existing renderers are informed about the new axis index so renderer-specific axis pickers
    /// can expose it immediately.
    ///
    /// @param createScale `true` to attach the type's default scale to the new axis immediately
    /// @param createGrid  `true` to attach the type's default grid to the new axis immediately
    /// @return the newly created y axis
    public Axis addYAxis(boolean createScale, boolean createGrid) {
        Axis axis = new Axis();
        this.addYAxisElement(axis);
        int yAxisIndex = getYAxisCount() - 1;
        if (createScale) {
            Scale scale = createScale(yAxisIndex);
            setYScale(yAxisIndex, scale);
            if (getType() == 1)
                scale.setCrossing(Axis.MAX_VALUE);
        }
        if (createGrid) {
            Grid grid = createGrid(yAxisIndex);
            setYGrid(yAxisIndex, grid);
        }
        int rendererIndex = 0;
        while (true) {
            if (rendererIndex >= getRendererCount())
                break;
            getRenderer(rendererIndex).handleYAxisElementAdded(
                    yAxisIndex, this.getAxisElement(yAxisIndex + 1));
            rendererIndex++;
        }
        invalidateScales();
        getChartArea().revalidateLayout();
        return axis;
    }

    private void addYAxisElement(Axis axis) {
        if (!Chart.bv)
            if (axis == null)
                throw new AssertionError();
        ChartUtil.checkNullParam("axis", axis);
        axis.setType(2);
        Chart.AxisElement axisElement = new Chart.AxisElement(axis, new CoordinateSystem(getXAxis(), axis), axisElements.size() - 1);
        axisElements.add(axisElement);
    }

    public final Scale getYScale(int yAxis) {
        this.validateYAxisIndex(yAxis);
        return this.getAxisElement(yAxis + 1).getScale();
    }

    /// Attaches the scale for the requested y axis.
    ///
    /// The supplied scale must not already belong to another chart.
    ///
    /// @param yAxis the zero-based y-axis index
    /// @param scale the scale to attach, or `null` to remove the current y-axis scale
    /// @throws IndexOutOfBoundsException if `yAxis` does not exist
    /// @throws IllegalArgumentException  if `scale` is already attached to another chart
    public void setYScale(int yAxis, Scale scale) {
        Object lock = getLock();
        synchronized (lock) {
            {
                this.validateYAxisIndex(yAxis);
                Scale currentScale = getYScale(yAxis);
                if (currentScale == scale)
                    return;
                if (scale != null)
                    if (scale.getAxisElement() != null)
                        throw new IllegalArgumentException("Scale already attached to a chart");
                this.getAxisElement(yAxis + 1).setScale(scale);
            } // end block
        }
    }

    public boolean isYScaleVisible() {
        return getYScale(0) != null && getYScale(0).isVisible();
    }

    public void setYScaleVisible(boolean visible) {
        if (getYScale(0) == null)
            return;
        getYScale(0).setVisible(visible);
    }

    public String getYScaleTitle() {
        return (getYScale(0) == null) ? null : getYScale(0).getTitle();
    }

    public void setYScaleTitle(String title) {
        if (getYScale(0) == null)
            return;
        getYScale(0).setTitle(title, getYScale(0).getTitleRenderer().getRotation());
    }

    public double getYScaleTitleRotation() {
        return (getYScale(0) == null) ? 0.0 : getYScale(0).getTitleRenderer().getRotation();
    }

    public void setYScaleTitleRotation(double rotation) {
        if (getYScale(0) == null)
            return;
        setAutoScaleTitleRotation(false);
        getYScale(0).getTitleRenderer().setRotation(rotation);
    }

    final void setScaleForAxis(int axisIndex, Scale scale) {
        if (axisIndex == -1)
            setXScale(scale);
        else
            setYScale(axisIndex, scale);
    }

    public final Grid getXGrid() {
        return this.getAxisElement(0).getGrid();
    }

    /// Attaches the grid for the x axis.
    ///
    /// The supplied grid must not already belong to another chart.
    ///
    /// @param grid the grid to attach, or `null` to remove the current x grid
    /// @throws IllegalArgumentException if `grid` is already attached to another chart
    public void setXGrid(Grid grid) {
        Object lock = getLock();
        synchronized (lock) {
            if (grid != null)
                if (grid.getChart() != null)
                    throw new IllegalArgumentException("Grid already attached to a chart");
            this.getAxisElement(0).setGrid(grid);
            getChartArea().repaint();
        }
    }

    public boolean isXGridVisible() {
        return getXGrid() != null && getXGrid().isMajorLineVisible();
    }

    public void setXGridVisible(boolean visible) {
        if (getXGrid() == null)
            return;
        getXGrid().setMajorLineVisible(visible);
    }

    public final Grid getYGrid(int yAxis) {
        this.validateYAxisIndex(yAxis);
        return this.getAxisElement(yAxis + 1).getGrid();
    }

    /// Attaches the grid for the requested y axis.
    ///
    /// @param yAxis the zero-based y-axis index
    /// @param grid  the grid to attach, or `null` to remove the current y-axis grid
    /// @throws IndexOutOfBoundsException if `yAxis` does not exist
    /// @throws IllegalArgumentException  if `grid` is already attached to another chart
    public void setYGrid(int yAxis, Grid grid) {
        Object lock = getLock();
        synchronized (lock) {
            if (grid != null)
                if (grid.getChart() != null)
                    throw new IllegalArgumentException("Grid already attached to a chart");
            this.validateYAxisIndex(yAxis);
            this.getAxisElement(yAxis + 1).setGrid(grid);
            getChartArea().repaint();
        }
    }

    public boolean isYGridVisible() {
        return getYGrid(0) != null && getYGrid(0).isMajorLineVisible();
    }

    public void setYGridVisible(boolean visible) {
        if (getYGrid(0) == null)
            return;
        getYGrid(0).setMajorLineVisible(visible);
    }

    final void setGridForAxis(int axisIndex, Grid grid) {
        if (axisIndex == -1)
            setXGrid(grid);
        else
            setYGrid(axisIndex, grid);
    }

    final CoordinateSystem getCoordinateSystem(Axis axis) {
        Chart.AxisElement axisElement = this.getAxisElement(axis);
        Object coordinateSystem = (axisElement == null) ? null : axisElement.getCoordinateSystem();
        return (CoordinateSystem) coordinateSystem;
    }

    /// Returns the coordinate system used by the requested y axis together with the shared x axis.
    ///
    /// @param yAxis the zero-based y-axis index
    /// @return the coordinate system pairing that y axis with the chart's x axis
    /// @throws IndexOutOfBoundsException if `yAxis` does not exist
    public final CoordinateSystem getCoordinateSystem(int yAxis) {
        return this.getAxisElement(yAxis + 1).getCoordinateSystem();
    }

    /// Creates the default scale for the requested axis index under the current [ChartConfig].
    ///
    /// `-1` addresses the x axis and non-negative values address y axes.
    ///
    /// @param axisIndex the chart-config axis index, where `-1` means the x axis
    /// @return a newly created scale suitable for that axis under the current chart type
    protected Scale createScale(int axisIndex) {
        return this.getChartConfig().createScale(axisIndex);
    }

    /// Creates the default grid for the requested y axis under the current [ChartConfig].
    ///
    /// @param yAxis the zero-based y-axis index
    /// @return a newly created grid suitable for that axis under the current chart type
    protected Grid createGrid(int yAxis) {
        return this.getChartConfig().createGrid(yAxis);
    }

    private void updateManualVisibleRangesAfterResize(Rectangle previousDrawRect, Rectangle drawRect) {
        if (getType() == 1)
            if (getResizingPolicy() != null) {
                int axisElementCount = axisElements.size();
                for (int axisElementIndex = 0; axisElementIndex < axisElementCount; axisElementIndex++) {
                    Chart.AxisElement axisElement = this.getAxisElement(axisElementIndex);
                    Axis axis = axisElement.getAxis();
                    if (!axis.hasDelegate())
                        if (!axis.isAutoVisibleRange()) {
                            DataInterval visibleRange = getResizingPolicy()
                                    .computeVisibleRange(this, axis, previousDrawRect, drawRect);
                            axis.setVisibleRange(visibleRange);
                        }
                }
            }
    }

    /// Formats an x-axis value using the current x-axis formatter.
    ///
    /// @param value the x value to format
    /// @return the formatted label text
    public String formatXValue(double value) {
        return this.getAxisElement(0).formatValue(value);
    }

    /// Formats a y-axis value using that axis's current formatter.
    ///
    /// @param yAxis the zero-based y-axis index
    /// @param value the y value to format
    /// @return the formatted label text
    /// @throws IndexOutOfBoundsException if `yAxis` does not exist
    public String formatYValue(int yAxis, double value) {
        this.validateYAxisIndex(yAxis);
        return this.getAxisElement(yAxis + 1).formatValue(value);
    }

    /// Installs the formatter used for x-axis labels.
    ///
    /// @param formatter the formatter to use, or `null` to fall back to the scale/default format
    public void setXValueFormat(ValueFormatter formatter) {
        this.getAxisElement(0).setValueFormatter(formatter);
    }

    /// Installs the formatter used for a y axis's labels.
    ///
    /// @param yAxis     the zero-based y-axis index
    /// @param formatter the formatter to use, or `null` to fall back to the scale/default format
    /// @throws IndexOutOfBoundsException if `yAxis` does not exist
    public void setYValueFormat(int yAxis, ValueFormatter formatter) {
        this.validateYAxisIndex(yAxis);
        this.getAxisElement(yAxis + 1).setValueFormatter(formatter);
    }

    /// Returns a snapshot of the decorations currently attached to this chart.
    ///
    /// The returned list is detached from the chart's internal storage, but the decorations
    /// themselves remain live chart-owned objects.
    ///
    /// @return a snapshot list in draw-order insertion order, or an empty list when none are
    ///     attached
    public List<ChartDecoration> getDecorations() {
        Object lock = getLock();
        synchronized (lock) {
            if (decorations == null)
                return Collections.emptyList();
            return new ArrayList<>(decorations);
        }
    }

    /// Attaches a decoration to this chart.
    ///
    /// Decorations are added to the drawable pipeline immediately and must not already belong to
    /// another chart.
    ///
    /// @param decoration the decoration to attach
    /// @throws IllegalArgumentException if `decoration` is already displayed by another chart
    public void addDecoration(ChartDecoration decoration) {
        Object lock = getLock();
        synchronized (lock) {
            ChartUtil.checkNullParam("decoration", decoration);
            List<ChartDecoration> previousDecorations = (decorations == null)
                    ? Collections.emptyList()
                    : new ArrayList<>(decorations);
            this.attachDecoration(decoration);
            List<ChartDecoration> currentDecorations = new ArrayList<>(decorations);
            super.firePropertyChange("decorations", previousDecorations, currentDecorations);
            getChartArea().repaint(decoration.getBounds(null).getBounds());
        }
    }

    private final void attachDecoration(ChartDecoration decoration) {
        if (decoration.getChart() != null)
            throw new IllegalArgumentException("Decoration already displayed by a chart");
        decoration.setChartInternal(this);
        if (decorations == null)
            decorations = new ArrayList();
        decorations.add(decoration);
        this.attachDrawable((ChartDrawable) decoration);
    }

    /// Detaches a decoration currently displayed by this chart.
    ///
    /// @param decoration the decoration to remove
    /// @throws IllegalArgumentException if `decoration` is not currently displayed by this chart
    public void removeDecoration(ChartDecoration decoration) {
        Object lock = getLock();
        synchronized (lock) {
            ChartUtil.checkNullParam("decoration", decoration);
            if (decoration.getChart() != this)
                throw new IllegalArgumentException("Decoration not displayed by this chart");
            if (!Chart.bv)
                if (decorations == null)
                    throw new AssertionError();
            Rectangle repaintBounds = decoration.getBounds(null).getBounds();
            decoration.setChartInternal(null);
            List<ChartDecoration> previousDecorations = new ArrayList<>(decorations);
            decorations.remove(decoration);
            if (decorations.isEmpty())
                decorations = null;
            this.detachDrawable(decoration);
            List<ChartDecoration> currentDecorations = (decorations == null)
                    ? Collections.emptyList()
                    : new ArrayList<>(decorations);
            super.firePropertyChange("decorations", previousDecorations, currentDecorations);
            getChartArea().repaint(repaintBounds);
        }
    }

    /// Replaces the full decoration list displayed by this chart.
    ///
    /// Existing decorations are detached before the new list is attached. `null` entries in the
    /// supplied list are ignored.
    ///
    /// @param newDecorations the replacement decorations, or `null` to clear them all
    public void setDecorations(List<ChartDecoration> newDecorations) {
        Object lock = getLock();
        synchronized (lock) {
            Rectangle repaintBounds = new Rectangle();
            List<ChartDecoration> previousDecorations = (decorations == null)
                    ? Collections.emptyList()
                    : new ArrayList<>(decorations);
            if (decorations != null) {
                for (int decorationIndex = decorations.size() - 1; decorationIndex >= 0; decorationIndex--) {
                    ChartDecoration decoration = decorations.remove(decorationIndex);
                    decoration.setChartInternal(null);
                    this.detachDrawable(decoration);
                    repaintBounds.add(decoration.getBounds(null).getBounds());
                }
                decorations = null;
            }
            if (newDecorations != null)
                for (ChartDecoration decoration : newDecorations)
                    if (decoration != null) {
                        this.attachDecoration(decoration);
                        repaintBounds.add(decoration.getBounds(null).getBounds());
                    }
            List<ChartDecoration> currentDecorations = (decorations == null)
                    ? Collections.emptyList()
                    : new ArrayList<>(decorations);
            boolean unchanged = previousDecorations.size() == currentDecorations.size();
            for (int decorationIndex = 0; unchanged && decorationIndex < previousDecorations.size(); decorationIndex++)
                unchanged = previousDecorations.get(decorationIndex) == currentDecorations.get(decorationIndex);
            if (!unchanged) {
                super.firePropertyChange("decorations", previousDecorations, currentDecorations);
                if (!repaintBounds.isEmpty())
                    getChartArea().repaint(repaintBounds);
            }
        }
    }

    private void attachDrawable(ChartDrawable drawable) {
        int insertionIndex = Collections.binarySearch(drawables, drawable, ChartDrawableComparator.getInstance());
        if (insertionIndex < 0)
            insertionIndex = -insertionIndex - 1;
        drawables.add(insertionIndex, drawable);
        if (drawable instanceof ChartOwnedDrawable ownedDrawable) {
            Chart previousChart = ownedDrawable.getChart();
            if (previousChart != this)
                ownedDrawable.chartConnected(previousChart, this);
        }
    }

    private void detachDrawable(ChartDrawable drawable) {
        drawables.remove(drawable);
        if (drawable instanceof ChartOwnedDrawable)
            ((ChartOwnedDrawable) drawable).chartConnected(((ChartOwnedDrawable) drawable).getChart(), null);
    }

    void handleDrawableDrawOrderChanged(ChartDrawable drawable, int previousDrawOrder, int newDrawOrder) {
        Object lock = getLock();
        synchronized (lock) {
            this.detachDrawable(drawable);
            this.attachDrawable(drawable);
        }
    }

    /// Returns an unmodifiable iterator over the current drawable pipeline.
    ///
    /// Drawables are ordered by [ChartDrawable#getDrawOrder()] and include attached decorations,
    /// scales, grids, and other chart-owned layers.
    ///
    /// @return an iterator over the current drawables in paint order
    public final Iterator<ChartDrawable> getDrawableIterator() {
        return Collections.unmodifiableList(drawables).iterator();
    }

    // TODO: TBR
    // public final Iterator<ChartDrawable> getReversedDrawableIterator() {
    // return
    // Collections.unmodifiableIterator(Collections.reversedIterator(this.drawables));
    // }

    /// Returns a snapshot of the chart's top-level renderers.
    ///
    /// The returned list is detached from the chart's internal storage, but the renderer instances
    /// remain live and chart-owned.
    ///
    /// @return a snapshot list of the current top-level renderers
    public List<ChartRenderer> getRenderers() {
        return (List) renderers.clone();
    }

    /// Returns an unmodifiable iterator over the current top-level renderers.
    ///
    /// @return an iterator that walks top-level renderers in paint order
    public Iterator<ChartRenderer> getRendererIterator() {
        return Collections.unmodifiableList(renderers).iterator();
    }

    Iterator<ChartRenderer> getReverseRendererIterator() {
        return reversedIterator(renderers.listIterator(renderers.size()));
    }

    private static <T> Iterator<T> reversedIterator(ListIterator<T> iter) {
        return new Iterator<T>() {
            @Override
            public boolean hasNext() {
                return iter.hasPrevious();
            }

            @Override
            public T next() {
                return iter.previous();
            }
        };
    }

    /// Returns a preorder iterator over every renderer reachable from this chart.
    ///
    /// Composite renderers contribute their descendants through
    /// [ChartRenderer#getChildIterator()].
    ///
    /// @return a preorder iterator starting with the chart's top-level renderers
    public Iterator<ChartRenderer> getAllRendererIterator() {
        // Bridge PreorderIterator from a synthetic chart root to the renderer tree. The iterator
        // starts at Chart.this only so getChildren(...) can hand the preorder skeleton the chart's
        // top-level renderer iterator. The enclosing method advances past that synthetic root
        // before returning the iterator to callers.
        class Itr extends PreorderIterator {

            Itr() {
                super(Chart.this);
            }

            @Override
            protected Iterator getChildren(Object node) {
                if (node instanceof Chart)
                    return ((Chart) node).getRendererIterator();
                return ((ChartRenderer) node).getChildIterator();
            }
        }

        Itr iterator = new Itr();
        iterator.next();
        return iterator;
    }

    public ChartRenderer getRenderer(int index) {
        return renderers.get(index);
    }

    /// Replaces the renderer at the requested index.
    ///
    /// The replacement renderer inherits the removed renderer's data source and y-axis assignment
    /// before the old renderer is detached.
    ///
    /// @param index    the renderer index to replace
    /// @param renderer the replacement renderer
    /// @throws IndexOutOfBoundsException if `index` does not exist
    /// @throws IllegalArgumentException  if `renderer` is already attached to another chart or is
    ///                                       incompatible with this chart type
    public void setRenderer(int index, ChartRenderer renderer) throws IllegalArgumentException {
        Object lock = getLock();
        synchronized (lock) {
            this.validateRendererConnection(renderer, true);
            ChartRenderer previousRenderer = getRenderer(index);
            renderer.setDataSource(previousRenderer.getDataSource());
            previousRenderer.setDataSource(new DefaultDataSource());
            renderer.copyPresentationSettingsFrom(previousRenderer);
            int yAxisIndex = previousRenderer.getAxisElement().getAxisIndex();
            renderers.remove(index);
            previousRenderer.setChartOwner(null, -1);
            this.addRendererInternal(index, renderer, yAxisIndex);
        }
    }

    /// Replaces the renderer at `index` with a fresh renderer of the requested type.
    ///
    /// @param index        the renderer index to replace
    /// @param rendererType the [ChartRenderer] type constant to instantiate
    /// @throws IllegalArgumentException  if the renderer type is invalid or incompatible with this
    ///                                       chart
    /// @throws IndexOutOfBoundsException if `index` does not exist
    public void setRendererType(int index, int rendererType) throws IllegalArgumentException {
        Object lock = getLock();
        synchronized (lock) {
            ChartRenderer renderer = ChartRenderer.createRenderer(rendererType);
            setRenderer(index, renderer);
        }
    }

    /// Detaches a renderer currently owned by this chart.
    ///
    /// @param renderer the renderer to remove
    /// @throws IllegalArgumentException if `renderer` is not currently connected to this chart
    public void removeRenderer(ChartRenderer renderer) throws IllegalArgumentException {
        Object lock = getLock();
        synchronized (lock) {
            this.removeRendererInternal(renderer);
        }
    }

    private void removeRendererInternal(ChartRenderer renderer) throws IllegalArgumentException {
        this.validateRendererConnection(renderer, false);
        block:
        if (renderers.remove(renderer)) {
            renderer.setChartOwner(null, -1);
            boolean dataRangeChanged = updateDataRange();
            if (this.areRefreshUpdatesEnabled()) {
                if (!dataRangeChanged)
                    if (!getChartArea().isPlotRectIncludingAnnotations()) {
                        getChartArea().repaint();
                        break block;
                    }
                getChartArea().revalidateLayout();
            }
        } // end block

    }

    public int getRendererCount() {
        return renderers.size();
    }

    /// Inserts a renderer at the requested top-level position and assigns it to a y axis.
    ///
    /// @param index    the desired insertion index; values beyond the current count append at the end
    /// @param renderer the renderer to attach
    /// @param yAxis    the zero-based y-axis index used by the renderer
    /// @throws IndexOutOfBoundsException if `yAxis` does not exist
    /// @throws IllegalArgumentException  if `renderer` is already attached to another chart or is
    ///                                       incompatible with this chart type
    public void addRenderer(int index, ChartRenderer renderer, int yAxis)
            throws IllegalArgumentException, IndexOutOfBoundsException {
        Object lock = getLock();
        synchronized (lock) {
            this.addRendererInternal(index, renderer, yAxis);
        }
    }

    /// Attaches a renderer backed by a single in-memory dataset.
    ///
    /// A temporary [DefaultDataSource] is created to expose the supplied dataset.
    ///
    /// @param renderer the renderer to attach
    /// @param dataSet  the sole dataset to expose through that renderer
    /// @throws IllegalArgumentException if `renderer` is already attached to another chart or is
    ///                                      incompatible with this chart type
    public final void addRenderer(ChartRenderer renderer, DataSet dataSet) throws IllegalArgumentException {
        DataSet[] dataSets = {dataSet};
        DefaultDataSource dataSource = new DefaultDataSource(dataSets);
        renderer.setDataSource(dataSource);
        Object lock = getLock();
        synchronized (lock) {
            this.addRendererInternal(getRendererCount(), renderer, 0);
        }
    }

    /// Appends a renderer on the primary y axis.
    ///
    /// @param renderer the renderer to attach
    /// @throws IllegalArgumentException if `renderer` is already attached to another chart or is
    ///                                      incompatible with this chart type
    public final void addRenderer(ChartRenderer renderer) throws IllegalArgumentException {
        Object lock = getLock();
        synchronized (lock) {
            this.addRendererInternal(getRendererCount(), renderer, 0);
        }
    }

    /// Appends a renderer on the requested y axis.
    ///
    /// @param renderer the renderer to attach
    /// @param yAxis    the zero-based y-axis index used by the renderer
    /// @throws IndexOutOfBoundsException if `yAxis` does not exist
    /// @throws IllegalArgumentException  if `renderer` is already attached to another chart or is
    ///                                       incompatible with this chart type
    public void addRenderer(ChartRenderer renderer, int yAxis) throws IllegalArgumentException {
        Object lock = getLock();
        synchronized (lock) {
            this.addRendererInternal(getRendererCount(), renderer, yAxis);
        }
    }

    private void addRendererInternal(int index, ChartRenderer renderer, int yAxis)
            throws IllegalArgumentException, IndexOutOfBoundsException {
        int targetIndex = index;
        this.validateYAxisIndex(yAxis);
        this.validateRendererConnection(renderer, true);
        if (targetIndex > renderers.size())
            targetIndex = renderers.size();
        renderers.add(targetIndex, renderer);
        renderer.setChartOwner(this, yAxis);
        boolean dataRangeChanged = updateDataRange();
        block:
        if (this.areRefreshUpdatesEnabled()) {
            if (!dataRangeChanged)
                if (!getChartArea().isPlotRectIncludingAnnotations()) {
                    getChartArea().repaint();
                    break block;
                }
            getChartArea().revalidateLayout();
        } // end block

    }

    /// Reassigns a connected renderer to a different y axis.
    ///
    /// @param renderer the renderer to move
    /// @param yAxis    the zero-based y-axis index to assign
    /// @throws IndexOutOfBoundsException if `yAxis` does not exist
    /// @throws IllegalArgumentException  if `renderer` is not connected to this chart
    public void setRendererAxis(ChartRenderer renderer, int yAxis) throws IllegalArgumentException {
        if (renderer.getChart() != this)
            throw new IllegalArgumentException("Renderer not handled by this chart");
        this.validateYAxisIndex(yAxis);
        renderer.setYAxisNumber(yAxis);
    }

    /// Creates a renderer of the requested type for `dataSource` and appends it to this chart.
    ///
    /// @param dataSource   the data source to expose through the new renderer
    /// @param rendererType the renderer type constant used to create the renderer
    /// @throws IllegalArgumentException if the renderer type is invalid or incompatible with this
    ///                                      chart
    public void addData(DataSource dataSource, int rendererType) throws IllegalArgumentException {
        ChartRenderer renderer = ChartRenderer.createRenderer(rendererType);
        if (renderer != null) {
            renderer.setDataSource(dataSource);
            this.addRenderer(renderer);
        }
    }

    public final int getRenderingType() {
        switch (getType()) {
            case 3:
                return 10;

            case 5:
                return 23;

            default:
                return defaultRendererType;

        }
    }

    public final int getUnconstrainedRenderingType() {
        return defaultRendererType;
    }

    /// Sets the default renderer type used by [#setDataSource(DataSource)] and future helper
    /// additions.
    ///
    /// When at least one renderer is already present, the first renderer is replaced immediately
    /// with the requested type.
    ///
    /// @param renderingType the renderer type constant to use by default
    public void setRenderingType(int renderingType) {
        if (renderingType != defaultRendererType) {
            ChartRenderer.validateRendererType(renderingType);
            if (getRendererCount() > 0)
                setRendererType(0, renderingType);
            defaultRendererType = renderingType;
        }
    }

    /// Returns an iterator over every dataset exposed by the chart's current renderers.
    ///
    /// Datasets are traversed renderer by renderer in top-level renderer order.
    ///
    /// @return an iterator over the current renderer datasets
    public Iterator<DataSet> getDataSetIterator() {
        return new Iterator<DataSet>() {
            final Iterator<ChartRenderer> rendererIterator = Chart.this.getRendererIterator();
            Iterator<DataSet> dataSetIterator;

            {
                this.advanceToNextRendererWithData();
            }

            private boolean advanceToNextRendererWithData() {
                while (true) {
                    if (!rendererIterator.hasNext())
                        break;
                    DataSource dataSource = rendererIterator.next().getDataSource();
                    dataSetIterator = dataSource.iterator();
                    if (dataSetIterator != null)
                        if (dataSetIterator.hasNext())
                            return true;
                }
                return false;
            }

            @Override
            public boolean hasNext() {
                return dataSetIterator != null && dataSetIterator.hasNext() || this.advanceToNextRendererWithData();
            }

            @Override
            public DataSet next() {
                if (dataSetIterator != null) {
                    if (!dataSetIterator.hasNext())
                        if (!this.advanceToNextRendererWithData())
                            throw new NoSuchElementException();
                    return dataSetIterator.next();
                }
                throw new NoSuchElementException();
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException();
            }
        };
    }

    /// Replaces the chart with a single renderer backed by `dataSource`.
    ///
    /// All existing renderers are removed first. When `dataSource` is non-null, a fresh renderer of
    /// the current [#getRenderingType()] is created and attached.
    ///
    /// @param dataSource the replacement data source, or `null` to clear all renderers
    /// @throws IllegalArgumentException if the default renderer type is incompatible with this chart
    public void setDataSource(DataSource dataSource) throws IllegalArgumentException {
        for (int rendererIndex = getRendererCount() - 1; rendererIndex >= 0; rendererIndex--)
            removeRenderer(getRenderer(rendererIndex));
        if (dataSource != null) {
            ChartRenderer renderer = ChartRenderer.createRenderer(getRenderingType());
            renderer.setDataSource(dataSource);
            this.addRenderer(renderer);
        }
    }

    /// Returns the data source owned by the first top-level renderer, if any.
    ///
    /// @return the first renderer's data source, or `null` when the chart has no renderers
    public DataSource getDataSource() {
        if (getRendererCount() <= 0)
            return null;
        return getRenderer(0).getDataSource();
    }

    /// Registers a listener for chart-area geometry changes.
    ///
    /// @param listener the listener to add
    public final void addChartListener(ChartListener listener) {
        if (chartAreaListeners == null)
            chartAreaListeners = new EventListenerList();
        chartAreaListeners.add(one.chartsy.charting.event.ChartListener.class, listener);
    }

    /// Unregisters a previously added [ChartListener].
    ///
    /// @param listener the listener to remove
    public final void removeChartListener(ChartListener listener) {
        if (chartAreaListeners == null)
            return;
        chartAreaListeners.remove(one.chartsy.charting.event.ChartListener.class, listener);
        if (chartAreaListeners.getListenerList().length == 0)
            chartAreaListeners = null;
    }

    void fireChartAreaChanged(Rectangle plotRect) {
        if (chartAreaListeners == null)
            return;
        ChartAreaEvent event = new ChartAreaEvent(this, plotRect);
        Object[] listeners = chartAreaListeners.getListenerList();
        for (int listenerIndex = listeners.length - 1; listenerIndex >= 0; listenerIndex -= 2)
            ((ChartListener) listeners[listenerIndex]).chartAreaChanged(event);
    }

    /// Returns the current plot rectangle used for coordinate transforms.
    ///
    /// @return the chart area's current plot rectangle
    public Rectangle getProjectorRect() {
        return getChartArea().getPlotRect();
    }

    /// Converts points from data space into display space for the requested y axis.
    ///
    /// The supplied [DoublePoints] batch is mutated in place.
    ///
    /// @param points the point batch to transform in place
    /// @param yAxis  the zero-based y-axis index whose coordinate system should be used
    public void toDisplay(DoublePoints points, int yAxis) {
        if (points != null)
            getProjector().toDisplay(points, getProjectorRect(), getCoordinateSystem(yAxis));
    }

    /// Converts points from data space into display space using the primary y axis.
    ///
    /// @param points the point batch to transform in place
    public final void toDisplay(DoublePoints points) {
        this.toDisplay(points, 0);
    }

    final void toDisplay2D(DoublePoints points, int yAxis) {
        if (points != null)
            getProjector2D().toDisplay(points, getProjectorRect(), getCoordinateSystem(yAxis));
    }

    /// Converts points from display space back into data space for the requested y axis.
    ///
    /// The supplied [DoublePoints] batch is mutated in place.
    ///
    /// @param points the point batch to transform in place
    /// @param yAxis  the zero-based y-axis index whose coordinate system should be used
    public void toData(DoublePoints points, int yAxis) {
        if (points != null)
            getProjector().toData(points, getProjectorRect(), getCoordinateSystem(yAxis));
    }

    /// Converts points from display space back into data space using the primary y axis.
    ///
    /// @param points the point batch to transform in place
    public final void toData(DoublePoints points) {
        this.toData(points, 0);
    }

    /// Returns the chart projector currently responsible for data/display transforms.
    ///
    /// @return the active projector
    public ChartProjector getProjector() {
        return projector;
    }

    /// Returns the projector currently used by the chart area's two-dimensional paint path.
    ///
    /// This is the same projector instance returned by [#getProjector()] and exists for older
    /// call sites that expect the historical `...2D` naming.
    public final ChartProjector getProjector2D() {
        return projector;
    }

    /// Returns a projector optimized for the supplied plot rectangle and coordinate system when the
    /// active projector supports that specialization.
    ///
    /// @param plotRect         the plot rectangle the local projector should target
    /// @param coordinateSystem the coordinate system the local projector should target
    /// @return either a specialized local projector or the active projector itself
    public final ChartProjector getLocalProjector2D(Rectangle plotRect, CoordinateSystem coordinateSystem) {
        ChartProjector currentProjector = getProjector2D();
        if (currentProjector instanceof AbstractProjector abstractProjector)
            return abstractProjector.getLocalProjector(plotRect, coordinateSystem);
        return currentProjector;
    }

    void setProjectorInternal(ChartProjector projector) {
        if (projector != this.projector) {
            this.projector = projector;
            invalidateScales();
        }
    }

    /// Returns the nearest logical point reported by the chart's renderers for the supplied picker.
    ///
    /// Renderers are consulted in reverse paint order so visually topmost content wins.
    ///
    /// @param picker the picking request in display space
    /// @return the nearest point, or `null` when no renderer reports one
    public DisplayPoint getNearestPoint(ChartDataPicker picker) {
        return ChartRenderer.getNearestPoint(this.getReverseRendererIterator(), picker);
    }

    /// Returns the nearest logical item reported by the chart's renderers for the supplied picker.
    ///
    /// @param picker         the picking request in display space
    /// @param distanceBuffer optional scratch storage used by renderer picking code
    /// @return the nearest item, or `null` when no renderer reports one
    public DisplayPoint getNearestItem(ChartDataPicker picker, double[] distanceBuffer) {
        return ChartRenderer.getNearestItem(this.getReverseRendererIterator(), picker, distanceBuffer);
    }

    /// Returns the display-space item currently selected by the supplied picker.
    ///
    /// @param picker the picking request in display space
    /// @return the selected display item, or `null` when no renderer reports one
    public DisplayPoint getDisplayItem(ChartDataPicker picker) {
        return ChartRenderer.getDisplayItem(this.getReverseRendererIterator(), picker);
    }

    /// Returns every display-space item reported by the chart's renderers for the supplied picker.
    ///
    /// @param picker the picking request in display space
    /// @return the collected display items, possibly empty
    public List<DisplayPoint> getDisplayItems(ChartDataPicker picker) {
        return ChartRenderer.getDisplayItems(this.getReverseRendererIterator(), picker);
    }

    /// Translates the visible ranges of the x axis and one y axis.
    ///
    /// @param xOffset the x-axis translation in data units
    /// @param yOffset the y-axis translation in data units
    /// @param yAxis   the zero-based y-axis index to translate
    public void scroll(double xOffset, double yOffset, int yAxis) {
        Object lock = getLock();
        synchronized (lock) {
            this.validateYAxisIndex(yAxis);
            this.setRefreshUpdatesEnabled(false);
            try {
                getXAxis().translateVisibleRange(xOffset);
                getYAxis(yAxis).translateVisibleRange(yOffset);
            } finally {
                this.setRefreshUpdatesEnabled(true);
            }
            chartArea.flushPendingRepaint();
        }
    }

    /// Applies the requested visible window to the x axis and one y axis.
    ///
    /// `NaN` lengths in either interval leave that axis unchanged.
    ///
    /// @param window the visible data window to apply
    /// @param yAxis  the zero-based y-axis index to update
    public void zoom(DataWindow window, int yAxis) {
        Object lock = getLock();
        synchronized (lock) {
            this.validateYAxisIndex(yAxis);
            if (window.isEmpty())
                return;
            this.setRefreshUpdatesEnabled(false);
            try {
                if (!Double.isNaN(window.xRange.getLength()))
                    getXAxis().setVisibleRange(window.xRange);
                if (!Double.isNaN(window.yRange.getLength()))
                    getYAxis(yAxis).setVisibleRange(window.yRange);
            } finally {
                this.setRefreshUpdatesEnabled(true);
            }
            chartArea.flushPendingRepaint();
        }
    }

    final void markScalesStale() {
        scalesUpToDate = false;
    }

    /// Marks all attached scales as stale.
    ///
    /// Callers typically use this after changing fonts, orientation, projector settings, or other
    /// state that affects tick generation or label metrics.
    public void invalidateScales() {
        this.markScalesStale();
    }

    /// Recomputes every attached scale immediately.
    ///
    /// This performs the full two-phase scale refresh used by the chart area: first rebuilding the
    /// scale model, then refreshing any cached layout derived from that model.
    public void updateScales() {
        int axisElementCount = axisElements.size();
        int axisElementIndex = 0;
        while (true) {
            if (axisElementIndex >= axisElementCount)
                break;
            Scale scale = this.getAxisElement(axisElementIndex).getScale();
            if (scale != null)
                scale.invalidatePreparedState();
            axisElementIndex++;
        }
        scalesUpToDate = true;
        axisElementIndex = 0;
        while (true) {
            if (axisElementIndex >= axisElementCount)
                break;
            Scale scale = this.getAxisElement(axisElementIndex).getScale();
            if (scale != null)
                scale.prepare();
            axisElementIndex++;
        }
    }

    /// Recomputes scales only when the chart knows one is stale.
    ///
    /// The method also honors per-scale invalidation flags, so callers may invoke it defensively
    /// before layout or export without forcing unnecessary work.
    public void updateScalesIfNeeded() {
        boolean needsUpdate = !scalesUpToDate;
        if (!needsUpdate) {
            int axisElementCount = axisElements.size();
            int axisElementIndex = 0;
            while (true) {
                if (axisElementIndex >= axisElementCount)
                    break;
                Scale scale = this.getAxisElement(axisElementIndex).getScale();
                if (scale != null)
                    if (!scale.isFullyPrepared()) {
                        needsUpdate = true;
                        break;
                    }
                axisElementIndex++;
            }
        }

        if (needsUpdate)
            updateScales();
    }

    void updateScaleModels(Rectangle plotRect) {
        int axisElementCount = axisElements.size();
        int axisElementIndex = 0;
        while (true) {
            if (axisElementIndex >= axisElementCount)
                break;
            Scale scale = this.getAxisElement(axisElementIndex).getScale();
            if (scale != null)
                scale.setPlotRect(plotRect, false);
            axisElementIndex++;
        }
    }

    void updateScales(Rectangle plotRect) {
        this.updateScaleModels(plotRect);
        scalesUpToDate = true;
        int axisElementCount = axisElements.size();
        int axisElementIndex = 0;
        while (true) {
            if (axisElementIndex >= axisElementCount)
                break;
            Scale scale = this.getAxisElement(axisElementIndex).getScale();
            if (scale != null)
                scale.prepare();
            axisElementIndex++;
        }
    }

    /// Applies a renderer-originated dataset change to chart layout and repaint state.
    ///
    /// Incremental insert, update, remove, and structure-change events are coalesced when the
    /// underlying dataset batches notifications. Depending on the event type, the chart either
    /// revalidates layout, updates the auto ranges, or repaints only the damaged renderer bounds.
    ///
    /// @param event    the dataset mutation reported by a renderer
    /// @param renderer the renderer that owns the affected dataset
    public void dataSetContentsChanged(DataSetContentsEvent event, ChartRenderer renderer) {
        Object lock = getLock();
        synchronized (lock) {
            boolean annotationsAffectLayout = false;
            if (getChartArea().isPlotRectIncludingAnnotations())
                if (event.getType() == 2) {
                    DataSet dataSet = event.getDataSet();
                    for (int index = event.getFirstIdx(); index <= event.getLastIdx(); index++) {
                        if (renderer.getAnnotation(dataSet, index) != null) {
                            annotationsAffectLayout = true;
                            break;
                        }
                    }
                }

            Rectangle2D dirtyBounds = null;
            if (event.getType() == 1)
                if (!getChartArea().isFullRepaintPending())
                    if (isOptimizedRepaint())
                        dirtyBounds = renderer.getBounds(event.getDataSet(), event.getFirstIdx(), event.getLastIdx(), null);

            if (isUsingEventThread())
                if (!SwingUtilities.isEventDispatchThread()) {
                    boolean finalAnnotationsAffectLayout = annotationsAffectLayout;
                    Rectangle2D finalDirtyBounds = dirtyBounds;
                    EventQueue.invokeLater(() -> {
                        synchronized (Chart.this.getLock()) {
                            this.handleDataSetContentsChange(event, renderer, finalAnnotationsAffectLayout, finalDirtyBounds);
                        }
                    });
                    return;
                }
            this.handleDataSetContentsChange(event, renderer, annotationsAffectLayout, dirtyBounds);
        }
    }

    private void handleDataSetContentsChange(DataSetContentsEvent event, ChartRenderer renderer,
                                             boolean annotationsAffectLayout, Rectangle2D dirtyBounds) {
        DataSet dataSet = event.getDataSet();
        DataSetEventAccumulator accumulator;
        switch (event.getType()) {
            case -1:
                accumulator = this.getOrCreatePendingDataSetEvent(renderer, dataSet, false);
                if (accumulator == null)
                    break;
                accumulator.flush();
                this.removePendingDataSetEvent(accumulator);
                break;

            case 1:
                if (getChartArea().isFullRepaintPending())
                    break;
                if (!isOptimizedRepaint())
                    break;
                getChartArea().addDirtyRegion(dirtyBounds);
                break;

            case 2:
                if (dataSet instanceof AbstractDataSet)
                    if (((AbstractDataSet) dataSet).isBatched()) {
                        accumulator = this.getOrCreatePendingDataSetEvent(renderer, dataSet, true);
                        accumulator.recordChangedRange(event.getFirstIdx(), event.getLastIdx(), annotationsAffectLayout);
                        break;
                    }
                this.handleDataChangedRange(renderer, dataSet, event.getFirstIdx(), event.getLastIdx(), annotationsAffectLayout);
                break;

            case 3:
                if (dataSet instanceof AbstractDataSet)
                    if (((AbstractDataSet) dataSet).isBatched()) {
                        accumulator = this.getOrCreatePendingDataSetEvent(renderer, dataSet, true);
                        accumulator.recordDataChanged();
                        break;
                    }
                this.handleDataChanged(dataSet);
                break;

            case 4:
                if (dataSet instanceof AbstractDataSet)
                    if (((AbstractDataSet) dataSet).isBatched()) {
                        accumulator = this.getOrCreatePendingDataSetEvent(renderer, dataSet, true);
                        accumulator.recordAddedRange(event.getFirstIdx(), event.getLastIdx());
                        break;
                    }
                this.handleDataAddedRange(renderer, dataSet, event.getFirstIdx(), event.getLastIdx());
                break;

            case 6:
                if (dataSet instanceof AbstractDataSet)
                    if (((AbstractDataSet) dataSet).isBatched()) {
                        accumulator = this.getOrCreatePendingDataSetEvent(renderer, dataSet, true);
                        accumulator.markFullUpdate();
                        break;
                    }
                this.handleFullDataUpdate(dataSet);
                break;

            default:
                break;

        }
    }

    DataSetEventAccumulator getOrCreatePendingDataSetEvent(ChartRenderer renderer, DataSet dataSet, boolean create) {
        RendererDataSetKey key = new RendererDataSetKey(renderer, dataSet);
        DataSetEventAccumulator accumulator = pendingDataSetEventsByKey.get(key);
        if (accumulator == null)
            if (create) {
                accumulator = new DataSetEventAccumulator(renderer, dataSet);
                pendingDataSetEventsByKey.put(accumulator, accumulator);
            }
        return accumulator;
    }

    void removePendingDataSetEvent(DataSetEventAccumulator accumulator) {
        pendingDataSetEventsByKey.remove(accumulator);
    }

    void removePendingDataSetEventsForRenderer(ChartRenderer renderer) {
        Object lock = getLock();
        synchronized (lock) {
            Iterator<Entry<RendererDataSetKey, DataSetEventAccumulator>> entryIterator = pendingDataSetEventsByKey.entrySet().iterator();
            while (true) {
                if (!entryIterator.hasNext())
                    break;
                Entry<RendererDataSetKey, DataSetEventAccumulator> entry = entryIterator.next();
                if (entry.getKey().renderer == renderer)
                    entryIterator.remove();
            }
        }
    }

    /// Flushes every batched dataset event that is still pending on this chart.
    void flushPendingDataSetEvents() {
        Iterator<Entry<RendererDataSetKey, DataSetEventAccumulator>> entryIterator = pendingDataSetEventsByKey.entrySet().iterator();
        while (true) {
            if (!entryIterator.hasNext())
                break;
            Entry<RendererDataSetKey, DataSetEventAccumulator> entry = entryIterator.next();
            entry.getValue().flush();
            entryIterator.remove();
        }
    }

    private void handleDataChangedRange(ChartRenderer renderer, DataSet dataSet, int firstIndex, int lastIndex,
                                        boolean layoutRequired) {
        boolean requiresLayout = layoutRequired;
        double visibleMax = getXAxis().getVisibleMax();
        double dataMax = dataSet.getXRange(null).getMax();
        boolean dataRangeChanged = updateDataRange();
        if (isShiftScroll())
            if (dataMax > visibleMax) {
                this.shiftVisibleXRange(getXAxis().getVisibleMin(), visibleMax, dataMax);
                dataRangeChanged = true;
            }
        if (dataRangeChanged)
            requiresLayout = true;
        block:
        if (requiresLayout)
            getChartArea().revalidateLayout();
        else {
            if (!getChartArea().isFullRepaintPending())
                if (isOptimizedRepaint()) {
                    Rectangle2D dirtyBounds = renderer.getBounds(dataSet, firstIndex, lastIndex, null);
                    getChartArea().addDirtyRegion(dirtyBounds);
                    getChartArea().repaintDirtyRegion();
                    break block;
                }
            getChartArea().repaint();
        } // end block

    }

    private void handleDataChanged(DataSet dataSet) {
        double visibleMax = getXAxis().getVisibleMax();
        double dataMax = dataSet.getXRange(null).getMax();
        updateDataRange();
        if (isShiftScroll())
            if (dataMax > visibleMax)
                this.shiftVisibleXRange(getXAxis().getVisibleMin(), visibleMax, dataMax);
        getChartArea().revalidateLayout();
    }

    private void handleDataAddedRange(ChartRenderer renderer, DataSet dataSet, int firstIndex, int lastIndex) {
        int addedRangeStart = firstIndex;
        int addedRangeEnd = lastIndex;
        double visibleMax = getXAxis().getVisibleMax();
        double lastAddedX = dataSet.getXData(addedRangeEnd);
        int repaintAllowed = getChartArea().isFullRepaintPending() ? 0 : 1;
        int visibleAddition = repaintAllowed;
        block:
        {
            if (!updateDataRange())
                if (!getChartArea().isFullRepaintPending())
                    if (isOptimizedRepaint()) {
                        repaintAllowed = 0;
                        break block;
                    }
            repaintAllowed = 1;
        } // end block

        int requiresLayout = repaintAllowed;
        block_2:
        {
            if (lastAddedX > visibleMax)
                if (isShiftScroll()) {
                    this.shiftVisibleXRange(getXAxis().getVisibleMin(), visibleMax, lastAddedX);
                    requiresLayout = 1;
                    break block_2;
                }
            double visibleMin = getXAxis().getVisibleMin();
            int intersectsVisibleWindow = 0;
            if (lastAddedX >= visibleMin)
                if (lastAddedX <= visibleMax)
                    intersectsVisibleWindow = 1;
            int index = addedRangeStart;
            while (true) {
                if (index >= addedRangeEnd)
                    break;
                if (intersectsVisibleWindow != 0)
                    break;
                lastAddedX = dataSet.getXData(index);
                if (lastAddedX >= visibleMin)
                    if (lastAddedX <= visibleMax)
                        intersectsVisibleWindow = 1;
                index++;
            }
            if (intersectsVisibleWindow == 0)
                visibleAddition = 0;
        } // end block_2

        if (visibleAddition != 0)
            if (requiresLayout == 0)
                getChartArea().addDirtyRegion(renderer.getBounds(dataSet, addedRangeStart, addedRangeEnd, null));
        if (visibleAddition != 0)
            getChartArea().flushPendingRepaint();
    }

    private void handleFullDataUpdate(DataSet dataSet) {
        double visibleMax = getXAxis().getVisibleMax();
        double dataMax = dataSet.getXRange(null).getMax();
        updateDataRange();
        if (isShiftScroll())
            if (dataMax > visibleMax)
                this.shiftVisibleXRange(getXAxis().getVisibleMin(), visibleMax, dataMax);
        getChartArea().revalidateLayout();
    }

    private void shiftVisibleXRange(double visibleMin, double visibleMax, double dataMax) {
        double translation = 0.0;
        if (getScrollRatio() == 0.0)
            translation = dataMax - visibleMax;
        else {
            double scrollStep = (visibleMax - visibleMin) * getScrollRatio();
            translation = Math.ceil((dataMax - visibleMax) / scrollStep) * scrollStep;
        }
        getXAxis().setVisibleRange(visibleMin + translation, visibleMax + translation);
    }

    void configureDataRange(Axis axis, DataInterval dataRange) {
        if (chartConfig != null)
            chartConfig.adjustDataRange(dataRange, axis);
    }

    /// Recomputes every auto-ranging axis from the current renderers and datasets.
    ///
    /// Only axes without delegates participate. The shared [DataRangePolicy] is asked for a range
    /// in axis order so it can reuse intermediate state across axes when appropriate.
    ///
    /// @return `true` if any axis changed its visible auto range
    public boolean updateDataRange() {
        Object lock = getLock();
        synchronized (lock) {
            boolean anyAxisChanged = false;
            this.setRefreshUpdatesEnabled(false);
            try {
                DataInterval dataRange = null;
                int axisElementCount = axisElements.size();
                int axisElementIndex = 0;
                while (true) {
                    if (axisElementIndex >= axisElementCount)
                        break;
                    Axis axis = this.getAxisElement(axisElementIndex).getAxis();
                    if (!axis.hasDelegate())
                        if (axis.isAutoDataRange()) {
                            dataRange = dataRangePolicy.computeDataRange(this, axis, dataRange);
                            if (!dataRange.isEmpty())
                                anyAxisChanged = axis.updateAutoDataRange(dataRange) || anyAxisChanged;
                        }
                    axisElementIndex++;
                }
            } finally {
                this.setRefreshUpdatesEnabled(true);
            }
            return anyAxisChanged;
        }
    }

    final void updateAutoDataRange(Axis axis) {
        Object lock = getLock();
        synchronized (lock) {
            Chart.AxisElement axisElement = this.getAxisElement(axis);
            if (axisElement != null)
                if (!axis.hasDelegate()) {
                    DataInterval dataRange = dataRangePolicy.computeDataRange(this, axis, null);
                    if (!dataRange.isEmpty())
                        axis.updateAutoDataRange(dataRange);
                }
        }
    }

    /// Recomputes auto ranges and refreshes the chart area accordingly.
    ///
    /// A pure value-range change forces layout when annotations contribute to plot bounds;
    /// otherwise the existing layout can be repainted in place.
    public void updateDataRangeAndRepaint() {
        boolean dataRangeChanged = updateDataRange();
        block:
        {
            if (!dataRangeChanged)
                if (!getChartArea().isPlotRectIncludingAnnotations()) {
                    getChartArea().repaint();
                    break block;
                }
            getChartArea().revalidateLayout();
        } // end block

    }

    /// Returns the policy that computes auto-ranging data intervals for this chart.
    public DataRangePolicy getDataRangePolicy() {
        return dataRangePolicy;
    }

    /// Replaces the auto-range policy used for all non-delegated axes.
    ///
    /// The new policy is applied immediately so attached axes reflect its rules before the next
    /// paint pass.
    ///
    /// @param policy the non-null range policy to use
    /// @throws IllegalArgumentException if `policy` is `null`
    public void setDataRangePolicy(DataRangePolicy policy) {
        ChartUtil.checkNullParam("policy", policy);
        if (policy != dataRangePolicy) {
            dataRangePolicy = policy;
            updateDataRange();
        }
    }

    /// Returns the optional policy that constrains visible ranges during resize operations.
    public final ChartResizingPolicy getResizingPolicy() {
        return resizingPolicy;
    }

    /// Sets the policy used to preserve or recompute visible ranges while the chart is resized.
    ///
    /// @param resizingPolicy the resize policy to use, or `null` to fall back to direct axis scaling
    public void setResizingPolicy(ChartResizingPolicy resizingPolicy) {
        this.resizingPolicy = resizingPolicy;
    }

    /// Returns whether chart-owned drawables may restyle themselves dynamically while painting.
    public final boolean isDynamicStyling() {
        return stateFlags.getFlag(FLAG_DYNAMIC_STYLING);
    }

    final ChartInteractorManager getInteractorManager() {
        return interactorManager;
    }

    /// Returns the interactors currently installed on this chart.
    ///
    /// The returned array is a snapshot of the manager state. Modifying it does not change the
    /// chart until [#setInteractors(ChartInteractor[])] is called.
    public ChartInteractor[] getInteractors() {
        if (interactorManager == null)
            return Chart.EMPTY_INTERACTORS;
        return interactorManager.getInteractors();
    }

    /// Replaces the chart's interactor set in one operation.
    ///
    /// Existing interactors are detached before the new array is added, preserving the manager's
    /// ordering and property-change behavior.
    ///
    /// @param interactors the interactors to install, or `null` for an empty set
    /// @throws IllegalArgumentException if an interactor is already connected to another chart
    public void setInteractors(ChartInteractor[] interactors) {
        ChartInteractor[] nextInteractors = (interactors == null) ? Chart.EMPTY_INTERACTORS : interactors;
        ChartInteractor[] previousInteractors = getInteractors();
        if (interactorManager == null)
            interactorManager = new ChartInteractorManager(this);
        boolean changed = interactorManager.setInteractors(nextInteractors);
        if (changed)
            super.firePropertyChange("interactors", previousInteractors, getInteractors());
    }

    /// Adds a single interactor instance to this chart.
    ///
    /// @param interactor the interactor to attach
    /// @throws IllegalArgumentException if `interactor` is `null`, already attached, or connected to
    ///                                      another chart
    public void addInteractor(ChartInteractor interactor) {
        ChartInteractor[] previousInteractors = getInteractors();
        if (interactorManager == null)
            interactorManager = new ChartInteractorManager(this);
        interactorManager.addInteractor(interactor);
        super.firePropertyChange("interactors", previousInteractors, getInteractors());
    }

    /// Creates and adds an interactor from its registered name.
    ///
    /// @param interactorName the interactor type name understood by `ChartInteractor.create(String)`
    /// @throws IllegalArgumentException if the name cannot be resolved or the created interactor
    ///                                      cannot be attached
    public void addInteractor(String interactorName) {
        ChartInteractor[] previousInteractors = getInteractors();
        if (interactorManager == null)
            interactorManager = new ChartInteractorManager(this);
        interactorManager.addInteractor(interactorName);
        super.firePropertyChange("interactors", previousInteractors, getInteractors());
    }

    /// Removes a previously attached interactor.
    ///
    /// @param interactor the interactor to detach
    /// @throws IllegalArgumentException if `interactor` is `null` or not currently attached to this
    ///                                      chart
    public void removeInteractor(ChartInteractor interactor) {
        if (interactorManager != null)
            if (interactor != null)
                if (interactor.getChart() == this) {
                    ChartInteractor[] previousInteractors = getInteractors();
                    boolean changed = interactorManager.removeInteractor(interactor);
                    if (changed)
                        super.firePropertyChange("interactors", previousInteractors, getInteractors());
                    return;
                }
        throw new IllegalArgumentException("Interactor not connected to this chart");
    }

    /// Returns the primary chart area component.
    ///
    /// The returned [Area] owns scales, grids, renderers, and the plot rectangle used by most
    /// coordinate transforms and export paths.
    public Chart.Area getChartArea() {
        return chartArea;
    }

    /// Returns the border currently painted around the chart area.
    public Border getChartAreaBorder() {
        return getChartArea().getBorder();
    }

    /// Sets the border painted around the chart area.
    ///
    /// @param border the border to install, or `null` for no chart-area border
    public void setChartAreaBorder(Border border) {
        getChartArea().setBorder(border);
    }

    /// Returns the fill color of the plot area style.
    public Color getPlotAreaBackground() {
        return getChartArea().getPlotStyle().getFillColor();
    }

    /// Replaces the plot area's fill paint with a solid color.
    ///
    /// @param color the plot fill color, or `null` to clear the explicit fill paint
    public void setPlotAreaBackground(Color color) {
        getChartArea().setPlotStyle(getChartArea().getPlotStyle().setFillPaint(color));
    }

    @Override
    public Color getBackground() {
        if (backgroundOverride != null)
            return backgroundOverride;
        return super.getBackground();
    }

    /// Returns the paint used when the chart fills its own background.
    ///
    /// When no explicit paint is configured, this falls back to [#getBackground()].
    public final Paint getBackgroundPaint() {
        return (backgroundPaint == null) ? getBackground() : backgroundPaint;
    }

    /// Sets the paint used when the chart clears its background.
    ///
    /// @param backgroundPaint the explicit background paint, or `null` to use [#getBackground()]
    public void setBackgroundPaint(Paint backgroundPaint) {
        this.backgroundPaint = backgroundPaint;
    }

    @Override
    public void setBackground(Color background) {
        getBackground();
        super.setBackground(background);
    }

    @Override
    public void setForeground(Color foreground) {
        super.getForeground();
        super.setForeground(foreground);
    }

    /// Installs the layout manager used by the chart container.
    ///
    /// Once construction finishes, a chart only supports [ChartLayout] instances because the rest
    /// of the component tree assumes that layout contract.
    ///
    /// @param layout the layout manager to install
    /// @throws IllegalArgumentException if the chart is initialized and `layout` is not a
    ///                                      [ChartLayout]
    @Override
    public void setLayout(LayoutManager layout) {
        if (stateFlags != null)
            if (stateFlags.getFlag(FLAG_LAYOUT_LOCKED))
                if (!(layout instanceof ChartLayout))
                    throw new IllegalArgumentException("You cannot change the layout of a Chart.");
        super.setLayout(layout);
    }

    /// Adds only chart-managed child components to this container.
    ///
    /// Accepted children are components previously marked with the chart client property or
    /// components inserted on a non-default Swing layer, which is how floating legends are hosted.
    @Override
    protected void addImpl(Component component, Object constraints, int index) {
        block:
        {
            if (component instanceof JComponent)
                if (((JComponent) component).getClientProperty(CHART_COMPONENT_CLIENT_PROPERTY) != null)
                    break block;
            if (constraints instanceof Integer)
                if (((Integer) constraints).compareTo(JLayeredPane.DEFAULT_LAYER) != 0)
                    break block;
            throw new IllegalArgumentException("cannot add the specified component : not a Chart-enabled component");
        } // end block

        ScalableFontManager fontManager = getFontManager();
        if (fontManager != null)
            ScalableFontManager.setFontManager(component, fontManager, component.getFont());
        super.addImpl(component, constraints, index);
    }

    /// Returns the optional header component displayed above the chart area.
    public final JComponent getHeader() {
        return header;
    }

    /// Replaces the header component displayed above the chart area.
    ///
    /// Plain text labels are normalized for the chart's bidirectional text settings and registered
    /// for live text updates.
    ///
    /// @param headerComponent the new header component, or `null` to remove the header
    public void setHeader(JComponent headerComponent) {
        if (headerComponent != header) {
            if (header != null) {
                if (this.header instanceof JLabel)
                    header.removePropertyChangeListener(TEXT_PROPERTY_NAME, getOrCreateLabelTextChangeListener());
                super.remove(header);
            }
            header = headerComponent;
            if (header != null) {
                if (this.header instanceof JLabel) {
                    JLabel headerLabel = (JLabel) header;
                    boolean htmlText = false;
                    String text = headerLabel.getText();
                    if (text != null)
                        if (text.length() > 6)
                            htmlText = text.regionMatches(true, 0, "<html>", 0, 6);
                    if (!htmlText) {
                        headerLabel.setText(BidiUtil.getCombinedString(headerLabel.getText(), getResolvedBaseTextDirection(),
                                super.getComponentOrientation(), true));
                        header.addPropertyChangeListener(TEXT_PROPERTY_NAME, getOrCreateLabelTextChangeListener());
                    }
                }
                header.putClientProperty(CHART_COMPONENT_CLIENT_PROPERTY, Boolean.TRUE);
                super.add(header, "North_Top");
            }
            if (isUsingEventThread())
                revalidate();
        }
    }

    /// Returns the header text when the current header is a [JLabel].
    public String getHeaderText() {
        if (header != null)
            if (this.header instanceof JLabel)
                return ((JLabel) header).getText();
        return null;
    }

    /// Sets the header text, creating a default label header when needed.
    ///
    /// @param text the header text, or `null` to clear the text on the existing/default label
    public void setHeaderText(String text) {
        JComponent headerComponent = getHeader();
        block:
        {
            if (headerComponent != null)
                if (headerComponent instanceof JLabel) {
                    ((JLabel) headerComponent).setText(text);
                    break block;
                }
            headerComponent = createJLabel(text);
            setHeader(headerComponent);
        } // end block

    }

    /// Returns the optional footer component displayed below the chart area.
    public final JComponent getFooter() {
        return footer;
    }

    /// Replaces the footer component displayed below the chart area.
    ///
    /// Plain text labels are normalized for the chart's bidirectional text settings and registered
    /// for live text updates.
    ///
    /// @param footerComponent the new footer component, or `null` to remove the footer
    public void setFooter(JComponent footerComponent) {
        if (footerComponent != footer) {
            if (footer != null) {
                if (this.footer instanceof JLabel)
                    footer.removePropertyChangeListener(TEXT_PROPERTY_NAME, getOrCreateLabelTextChangeListener());
                super.remove(footer);
            }
            footer = footerComponent;
            if (footer != null) {
                if (this.footer instanceof JLabel) {
                    JLabel footerLabel = (JLabel) footer;
                    footerLabel.setText(BidiUtil.getCombinedString(footerLabel.getText(), getResolvedBaseTextDirection(),
                            super.getComponentOrientation(), true));
                    footer.addPropertyChangeListener(TEXT_PROPERTY_NAME, getOrCreateLabelTextChangeListener());
                }
                footer.putClientProperty(CHART_COMPONENT_CLIENT_PROPERTY, Boolean.TRUE);
                super.add(footer, "South_Bottom");
            }
            if (isUsingEventThread())
                revalidate();
        }
    }

    /// Returns the footer text when the current footer is a [JLabel].
    public String getFooterText() {
        if (footer != null)
            if (this.footer instanceof JLabel)
                return ((JLabel) footer).getText();
        return null;
    }

    /// Sets the footer text, creating a default label footer when needed.
    ///
    /// @param text the footer text, or `null` to clear the text on the existing/default label
    public void setFooterText(String text) {
        JComponent footerComponent = getFooter();
        block:
        {
            if (footerComponent != null)
                if (footerComponent instanceof JLabel) {
                    ((JLabel) footerComponent).setText(text);
                    break block;
                }
            footerComponent = createJLabel(text);
            setFooter(footerComponent);
        } // end block

    }

    private Chart.JLabelTextChangeListener getOrCreateLabelTextChangeListener() {
        if (labelTextChangeListener == null)
            labelTextChangeListener = new Chart.JLabelTextChangeListener();
        return labelTextChangeListener;
    }

    /// Creates the default label implementation used by [#setHeaderText(String)] and
    /// [#setFooterText(String)].
    ///
    /// Subclasses may override this to supply a custom label subclass while preserving the chart's
    /// header/footer lifecycle.
    ///
    /// @param text the initial label text
    /// @return the label instance to install
    protected JLabel createJLabel(String text) {
        return new JLabelExt(text, 0);
    }

    /// Returns the legend currently associated with this chart.
    public final Legend getLegend() {
        return legend;
    }

    /// Associates a legend instance with this chart without docking it into the component tree.
    ///
    /// Most callers want [#addLegend(Legend, String)] so the legend is both associated and placed.
    ///
    /// @param legend the legend to associate, or `null` to clear the current legend
    public void setLegend(Legend legend) {
        this.setLegendInternal(legend);
    }

    private void setLegendInternal(Legend newLegend) {
        Object lock = getLock();
        synchronized (lock) {
            if (legend == newLegend)
                return;
            if (legend != null)
                legend.setChart(null);
            legend = newLegend;
            if (newLegend != null)
                newLegend.setChart(this);
        }
    }

    /// Adds and docks the legend at the requested position using the current floating-bounds policy.
    ///
    /// @param legend   the legend to dock
    /// @param position the requested docking position, or `null` for the floating `"Absolute"` mode
    public void addLegend(Legend legend, String position) {
        this.addLegend(legend, position, true);
    }

    /// Adds and docks the legend at the requested position.
    ///
    /// Docked positions are normalized to the chart's layout regions. The special `"Absolute"`
    /// position hosts the legend on the palette layer and optionally restores its previous floating
    /// bounds.
    ///
    /// @param legend                the legend to dock
    /// @param position              the requested docking position, or `null` for floating mode
    /// @param restoreFloatingBounds whether floating legends should reuse the stored floating bounds
    public void addLegend(Legend legend, String position, boolean restoreFloatingBounds) {
        if (legend == null)
            return;
        String normalizedPosition = (position == null) ? "Absolute" : this.normalizeLegendPosition(position);
        if (legend != null)
            if (legend.getParent() == this)
                super.remove(legend);
        this.setLegendInternal(legend);
        if (!normalizedPosition.equals("Absolute")) {
            legend.setFloating(false);
            super.add(legend, normalizedPosition);
        } else {
            legend.setFloating(true);
            super.add(legend, JLayeredPane.PALETTE_LAYER);
            Dimension preferredSize = legend.getPreferredSize();
            block:
            {
                if (floatingLegendLocation != null)
                    if (restoreFloatingBounds)
                        break block;
                floatingLegendLocation = legend.getLocation();
            } // end block

            if (restoreFloatingBounds)
                legend.setBounds(floatingLegendLocation.x, floatingLegendLocation.y, preferredSize.width,
                        preferredSize.height);
        }
        if (isUsingEventThread())
            revalidate();
        String previousPosition = legendPosition;
        legendPosition = normalizedPosition;
        if (!Objects.equals(previousPosition, legendPosition))
            legend.fireLegendDockingEvent(new LegendDockingEvent(legend, previousPosition));
    }

    final void setFloatingLegendLocation(Point location) {
        floatingLegendLocation = location;
    }

    private String normalizeLegendPosition(String position) {
        if (position.equalsIgnoreCase("North_Top") || position.equalsIgnoreCase("NORTH"))
            return DEFAULT_LEGEND_POSITION;
        if (position.equalsIgnoreCase("South_Bottom") || position.equalsIgnoreCase("SOUTH"))
            return "South_Top";
        if (position.equalsIgnoreCase("Absolute"))
            return "Absolute";
        if (position.equalsIgnoreCase("West"))
            return "West";
        if (position.equalsIgnoreCase("East"))
            return "East";
        return position;
    }

    /// Returns whether a legend is currently installed and visible.
    public boolean isLegendVisible() {
        return getLegend() != null && getLegend().isVisible();
    }

    /// Shows or hides the legend managed by this chart.
    ///
    /// Enabling visibility lazily creates a default legend if none is present. Disabling visibility
    /// removes the current legend from both the component tree and chart association.
    ///
    /// @param visible `true` to ensure a legend is present, `false` to remove it
    public void setLegendVisible(boolean visible) {
        block:
        {
            if (legend == null)
                if (visible) {
                    this.addLegend(createLegend(), getLegendPosition(), true);
                    break block;
                }
            if (legend != null)
                if (!visible) {
                    super.remove(legend);
                    setLegend(null);
                    if (isUsingEventThread())
                        revalidate();
                }
        } // end block

    }

    /// Creates the default legend used when visibility is enabled without an existing legend.
    protected Legend createLegend() {
        return new Legend();
    }

    /// Returns the normalized docking position remembered for the legend.
    public String getLegendPosition() {
        return legendPosition;
    }

    /// Changes the remembered or active legend docking position.
    ///
    /// When a legend is already attached, the legend is re-added immediately so layout and floating
    /// behavior stay consistent with the new position.
    ///
    /// @param position the requested docking position
    public void setLegendPosition(String position) {
        try {
            if (legend == null)
                legendPosition = this.normalizeLegendPosition(position);
            else
                this.addLegend(legend, position, true);
        } catch (Exception e1) {
            e1.printStackTrace();
        }
    }

    void fireRendererChanged(ChartRendererEvent event) {
        if (rendererChangeListeners == null)
            return;
        Object[] listeners = rendererChangeListeners.getListenerList();
        for (int listenerIndex = listeners.length - 1; listenerIndex >= 0; listenerIndex -= 2)
            ((ChartRendererListener) listeners[listenerIndex]).rendererChanged(event);
    }

    private void setRendererChangeBatchActive(boolean active) {
        if (active) {
            rendererChangeDepth++;
        } else {
            rendererChangeDepth--;
            if (rendererChangeDepth == 0)
                if (rendererLayoutPending) {
                    rendererLayoutPending = false;
                    getChartArea().revalidateLayout();
                }
        }
        if (rendererChangeListeners == null)
            return;
        Object[] listeners = rendererChangeListeners.getListenerList();
        for (int listenerIndex = listeners.length - 1; listenerIndex >= 0; listenerIndex -= 2) {
            ChartRendererListener listener = (ChartRendererListener) listeners[listenerIndex];
            if (listener instanceof ChartRendererListener2 batchListener)
                if (!active)
                    batchListener.endRendererChanges();
                else
                    batchListener.startRendererChanges();
        }
    }

    /// Registers a listener for renderer change events raised by this chart.
    ///
    /// Listeners implementing [ChartRendererListener2] immediately receive synthetic
    /// `startRendererChanges()` callbacks for any renderer-change batch already in progress.
    ///
    /// @param listener the listener to add
    public final void addChartRendererListener(ChartRendererListener listener) {
        if (rendererChangeListeners == null)
            rendererChangeListeners = new EventListenerList();
        if (listener instanceof ChartRendererListener2) {
            int remainingDepth = rendererChangeDepth;
            while (true) {
                if (remainingDepth <= 0)
                    break;
                ((ChartRendererListener2) listener).startRendererChanges();
                remainingDepth--;
            }
        }
        rendererChangeListeners.add(one.chartsy.charting.event.ChartRendererListener.class, listener);
    }

    /// Removes a renderer listener previously added with
    /// [#addChartRendererListener(ChartRendererListener)].
    ///
    /// [ChartRendererListener2] implementations receive matching synthetic
    /// `endRendererChanges()` callbacks for any active batch depth being unwound on removal.
    ///
    /// @param listener the listener to remove
    public final void removeChartRendererListener(ChartRendererListener listener) {
        if (rendererChangeListeners == null)
            return;
        EventListenerList listeners = rendererChangeListeners;
        block:
        synchronized (listeners) {
            Object[] listenerList = rendererChangeListeners.getListenerList();
            boolean registered = false;
            for (int listenerIndex = listenerList.length - 1; listenerIndex >= 0; listenerIndex -= 2)
                if (listenerList[listenerIndex] == listener) {
                    registered = true;
                    break;
                }
            if (!registered)
                return;
            rendererChangeListeners.remove(ChartRendererListener.class, listener);
        } // end block

        if (listener instanceof ChartRendererListener2) {
            int remainingDepth = rendererChangeDepth;
            while (true) {
                if (remainingDepth <= 0)
                    break;
                ((ChartRendererListener2) listener).endRendererChanges();
                remainingDepth--;
            }
        }
        if (rendererChangeListeners.getListenerList().length == 0)
            rendererChangeListeners = null;
    }

    void handleRendererChanged(ChartRenderer renderer, int changeType) {
        switch (changeType) {
            case 1:
            case 2:
            case 3:
            case 4:
            case 5:
            case 7:
                updateDataRangeAndRepaint();
                break;

            case 8:
                if (!getChartArea().isPlotRectIncludingAnnotations()) {
                    getChartArea().repaint();
                    break;
                }
                getChartArea().revalidateLayout();
                break;

            default:
                break;

        }
        this.fireRendererChanged(new ChartRendererEvent(this, renderer, changeType));
    }

    /// Opens a renderer-change batch.
    ///
    /// Batches suppress repeated layout work until [#endRendererChanges()] balances the nesting
    /// depth back to zero.
    public void startRendererChanges() {
        this.setRendererChangeBatchActive(true);
    }

    /// Closes a renderer-change batch started by [#startRendererChanges()].
    public void endRendererChanges() {
        this.setRendererChangeBatchActive(false);
    }

    void fireBeforeDraw(ChartDrawEvent event) {
        if (drawListeners == null)
            return;
        Object[] listeners = drawListeners.getListenerList();
        for (int listenerIndex = listeners.length - 1; listenerIndex >= 0; listenerIndex -= 2)
            ((ChartDrawListener) listeners[listenerIndex]).beforeDraw(event);
    }

    void fireAfterDraw(ChartDrawEvent event) {
        if (drawListeners == null)
            return;
        Object[] listeners = drawListeners.getListenerList();
        for (int listenerIndex = listeners.length - 1; listenerIndex >= 0; listenerIndex -= 2)
            ((ChartDrawListener) listeners[listenerIndex]).afterDraw(event);
    }

    /// Registers a listener invoked before and after chart drawing.
    ///
    /// @param listener the listener to add
    public final void addChartDrawListener(ChartDrawListener listener) {
        if (drawListeners == null)
            drawListeners = new EventListenerList();
        drawListeners.add(one.chartsy.charting.event.ChartDrawListener.class, listener);
    }

    /// Removes a chart draw listener previously added with [#addChartDrawListener(ChartDrawListener)].
    ///
    /// @param listener the listener to remove
    public final void removeChartDrawListener(ChartDrawListener listener) {
        if (drawListeners == null)
            return;
        drawListeners.remove(one.chartsy.charting.event.ChartDrawListener.class, listener);
        if (drawListeners.getListenerList().length == 0)
            drawListeners = null;
    }

    /// Delegates one axis to another chart and optionally synchronizes the plot area geometry.
    ///
    /// Axis synchronization links this chart's x axis when `axis == Axis.X_AXIS`; any other value
    /// currently targets the primary y axis. When `synchronizePlotArea` is enabled, both charts must
    /// use Cartesian projectors so their plot bounds can be kept aligned.
    ///
    /// @param chart               the chart whose axis should become the delegate target
    /// @param axis                the axis selector, typically `Axis.X_AXIS` or `Axis.Y_AXIS`
    /// @param synchronizePlotArea whether plot rectangles should be synchronized as well
    /// @throws UnsupportedOperationException if plot-area synchronization is requested for a
    ///                                           non-Cartesian chart pair
    public void synchronizeAxis(Chart chart, int axis, boolean synchronizePlotArea) {
        Object lock = getLock();
        synchronized (lock) {
            if (synchronizePlotArea)
                if (!(chart.getProjector() instanceof CartesianProjector)
                        || !(this.getProjector() instanceof CartesianProjector))
                    throw new UnsupportedOperationException(
                            "Both charts must be Cartesian to synchronize their plot areas");
            if (axis == 1)
                getXAxis().setDelegate(chart.getXAxis());
            else
                getYAxis(0).setDelegate(chart.getYAxis(0));
            if (synchronizePlotArea) {
                boolean reversePlotArea = (axis != 1) ? !isProjectorReversed() : isProjectorReversed();
                MultiChartSync synchronizer = new MultiChartSync(reversePlotArea ? 1 : 0);
                ChartAreaSynchronizer.synchronize(this, chart.getChartArea(), synchronizer);
            }
        }
    }

    /// Removes axis and plot-area synchronization previously installed by [#synchronizeAxis(Chart, int, boolean)].
    ///
    /// @param axis the axis selector originally passed to synchronize
    public void unSynchronizeAxis(int axis) {
        Object lock = getLock();
        synchronized (lock) {
            if (axis == 1)
                getXAxis().setDelegate((Axis) null);
            else
                getYAxis(0).setDelegate((Axis) null);
            ChartAreaSynchronizer.unSynchronize(this);
        }
    }

    /// Returns the x-axis scroll bar currently bound to this chart, if any.
    public JScrollBar getXScrollBar() {
        return xScrollBar;
    }

    /// Attaches a Swing scroll bar to the x axis.
    ///
    /// Any previously attached scroll bar is disconnected before the new one is bound.
    ///
    /// @param scrollBar the scroll bar to bind, or `null` to remove the existing binding
    public void setXScrollBar(JScrollBar scrollBar) {
        if (xScrollBar != null)
            detachBoundedModel(xScrollBar.getModel());
        xScrollBar = scrollBar;
        if (scrollBar != null)
            this.attachBoundedModel(scrollBar.getModel(), -1);
    }

    /// Binds a bounded range model to one chart axis.
    ///
    /// The model stays synchronized with the axis visible range through a
    /// [BoundedRangeModelConnector]. Passing `-1` selects the x axis; non-negative values select y
    /// axes.
    ///
    /// @param model    the model to bind
    /// @param axis     the axis selector, where `-1` means x and non-negative values mean y-axis indices
    /// @param reversed whether the model direction should be reversed relative to the axis
    public void attachBoundedModel(BoundedRangeModel model, int axis, boolean reversed) {
        if (model == null)
            return;
        Axis chartAxis;
        if (axis == -1)
            chartAxis = getXAxis();
        else {
            this.validateYAxisIndex(axis);
            chartAxis = getYAxis(axis);
        }
        if (chartAxis == null)
            return;
        Chart.connectBoundedRangeModel(model, chartAxis, reversed);
    }

    /// Binds a bounded range model to one chart axis without reversing the direction.
    ///
    /// @param model the model to bind
    /// @param axis  the axis selector, where `-1` means x and non-negative values mean y-axis indices
    public void attachBoundedModel(BoundedRangeModel model, int axis) {
        this.attachBoundedModel(model, axis, false);
    }

    /// Removes any chart binding for the given bounded range model.
    ///
    /// @param model the model to disconnect
    public void detachBoundedModel(BoundedRangeModel model) {
        if (model == null)
            return;
        Chart.disconnectBoundedRangeModel(model);
    }

    private static synchronized void connectBoundedRangeModel(BoundedRangeModel model, Axis axis, boolean reversed) {
        BoundedRangeModelConnector connector = new BoundedRangeModelConnector(model, axis, reversed);
        if (Chart.boundedRangeModelConnectors == null)
            Chart.boundedRangeModelConnectors = new HashMap(2);
        Chart.boundedRangeModelConnectors.put(model, connector);
    }

    private static synchronized void disconnectBoundedRangeModel(BoundedRangeModel model) {
        if (Chart.boundedRangeModelConnectors == null)
            return;
        BoundedRangeModelConnector connector = Chart.boundedRangeModelConnectors.remove(model);
        if (connector == null)
            return;
        connector.disconnect();
    }

    private final void validateYAxisIndex(int yAxis) {
        if (yAxis < 0)
            throw new IndexOutOfBoundsException("A y axis is required here, not an x axis");
        if (yAxis < getYAxisCount())
            return;
        throw new IndexOutOfBoundsException("No such axis:" + yAxis);
    }

    private final void validateRendererConnection(ChartRenderer renderer, boolean connecting) {
        ChartUtil.checkNullParam("renderer", renderer);
        if (connecting) {
            if (renderer.getChart() != null)
                throw new IllegalArgumentException("Renderer already connected to another chart.");
            if (!chartConfig.supportsRenderer(renderer))
                throw new IllegalArgumentException("Renderer not supported by this chart.");
        } else if (!renderers.contains(renderer))
            throw new IllegalArgumentException("Renderer not connected to this chart.");
    }

    @Override
    public void paint(Graphics g) {
        if (fontManagerReferenceSizeDirty) {
            ScalableFontManager fontManager = getFontManager();
            if (fontManager != null)
                fontManager.setRefSize(super.getSize());
            fontManagerReferenceSizeDirty = false;
        }
        if (super.isOpaque())
            if (g.getClipBounds() == null)
                g.setClip(new Rectangle(0, 0, super.getWidth(), super.getHeight()));
        super.paint(g);
    }

    @Override
    protected void paintComponent(Graphics g) {
        if (super.isOpaque()) {
            Graphics2D g2 = (Graphics2D) g;
            Paint originalPaint = g2.getPaint();
            Paint fillPaint = getBackgroundPaint();
            if (fillPaint == null)
                fillPaint = Color.lightGray;
            g2.setPaint(fillPaint);
            Rectangle clipBounds = g.getClipBounds();
            block:
            {
                if (clipBounds != null)
                    if (backgroundPaint == null) {
                        G2D.fillRect(g2, clipBounds.x, clipBounds.y, clipBounds.width, clipBounds.height);
                        break block;
                    }
                G2D.fillRect(g2, 0, 0, super.getWidth(), super.getHeight());
            } // end block

            g2.setPaint(originalPaint);
        }
    }

    private static Color findOpaqueBackground(Component component) {
        Component current = component;
        while (current != null)
            if (current.isOpaque())
                break;
            else
                current = current.getParent();
        return (current == null) ? Color.white : current.getBackground();
    }

    @Override
    public void revalidate() {
        Object lock = getLock();
        synchronized (lock) {
            if (!isUsingEventThread())
                return;
            super.revalidate();
        }
    }

    @Override
    public void repaint() {
        if (!isUsingEventThread())
            return;
        super.repaint();
    }

    private static void writePng(BufferedImage image, OutputStream out, Color transparentColor) throws IOException {
        try (ImageOutputStream imageOutput = ImageIO.createImageOutputStream(out)) {
            ImageTypeSpecifier imageType = ImageTypeSpecifier.createFromRenderedImage(image);
            Iterator<ImageWriter> writers = ImageIO.getImageWriters(imageType, "png");
            if (!writers.hasNext())
                throw new IOException("no PNG format writer found");
            ImageWriter writer = writers.next();
            try {
                writer.setOutput(imageOutput);
                if (transparentColor == null)
                    writer.write(image);
                else {
                    IIOMetadata metadata = writer.getDefaultImageMetadata(imageType, null);
                    if (metadata != null) {
                        IIOMetadataNode metadataRoot = new IIOMetadataNode(metadata.getNativeMetadataFormatName());
                        IIOMetadataNode transparencyNode = new IIOMetadataNode("tRNS");
                        IIOMetadataNode transparentColorNode = new IIOMetadataNode("tRNS_RGB");
                        transparentColorNode.setAttribute("red", Integer.toString(transparentColor.getRed()));
                        transparentColorNode.setAttribute("green", Integer.toString(transparentColor.getGreen()));
                        transparentColorNode.setAttribute("blue", Integer.toString(transparentColor.getBlue()));
                        transparencyNode.appendChild(transparentColorNode);
                        metadataRoot.appendChild(transparencyNode);
                        metadata.mergeTree(metadata.getNativeMetadataFormatName(), metadataRoot);
                    }
                    writer.write(metadata, new IIOImage(image, null, metadata), null);
                }
                imageOutput.flush();
            } finally {
                writer.dispose();
            }
        }
    }

    static BufferedImage paintToImage(JComponent component, PaintAction paintAction, BufferedImage image, boolean clearBackground) {
        AtomicReference<BufferedImage> imageReference = new AtomicReference<>();
        Runnable paintRunnable = () -> {
            paintAction.checkHierarchy(component);
            Dimension size = component.getSize();
            imageReference.set(image);
            boolean shouldClearBackground = clearBackground;
            if (imageReference.get() != null) {
                size.width = image.getWidth();
                size.height = image.getHeight();
            } else {
                if (size.width == 0)
                    size.width = DEFAULT_EXPORT_WIDTH;
                if (size.height == 0)
                    size.height = DEFAULT_EXPORT_HEIGHT;
                imageReference.set(new BufferedImage(size.width, size.height, BufferedImage.TYPE_INT_RGB));
                shouldClearBackground = true;
            }
            paintAction.prepareComponent(component, size);
            Graphics2D g2 = (Graphics2D) imageReference.get().getGraphics();
            if (shouldClearBackground) {
                Color opaqueBackground = Chart.findOpaqueBackground(component);
                g2.setColor(opaqueBackground);
                g2.fillRect(0, 0, size.width, size.height);
            }
            component.paint(g2);
            g2.dispose();
            paintAction.disposeComponent(component);
        };
        try {
            SwingUtilities.invokeAndWait(paintRunnable);
        } catch (InvocationTargetException | InterruptedException e) {
            throw new RuntimeException(e);
        }
        return imageReference.get();
    }

    /// Renders this chart into a buffered image using the current component size.
    ///
    /// When `clearBackground` is enabled and no target image is supplied, the export path first
    /// fills a new image with the nearest opaque ancestor background color.
    ///
    /// @param image           the image to reuse, or `null` to allocate one sized from the current component
    /// @param clearBackground whether the export should clear the image before painting
    /// @return the rendered image
    public BufferedImage toImage(BufferedImage image, boolean clearBackground) {
        return Chart.paintToImage(this, new Chart.ChartPaintAction(/* hidden print window */null), image, clearBackground);
    }

    /// Writes this chart as PNG using the current component size.
    ///
    /// @param out                   the destination stream
    /// @param transparentBackground `true` to encode the nearest opaque ancestor background as
    ///                                  transparent PNG metadata
    /// @throws IOException if the PNG writer cannot be created or the stream write fails
    public void toPNG(OutputStream out, boolean transparentBackground) throws IOException {
        BufferedImage image = this.toImage(null, true);
        Color transparentColor = (!transparentBackground) ? null : Chart.findOpaqueBackground((Component) this);
        Chart.writePng(image, out, transparentColor);
    }

    /// Writes this chart as PNG using the current component size and an opaque background.
    ///
    /// @param out the destination stream
    /// @throws IOException if the PNG write fails
    public void toPNG(OutputStream out) throws IOException {
        this.toPNG(out, false);
    }

    /// Returns the paint context active for the current export or off-screen paint session.
    ///
    /// The value is `null` during normal on-screen painting.
    public final Chart.ChartPaintContext getPaintContext() {
        return activePaintContext;
    }

    /// Starts an off-screen paint session with the supplied context.
    ///
    /// Session state drives image exports and makes layout information available to nested chart
    /// components while the export is in progress.
    ///
    /// @param paintContext the session context to install
    public final void startSession(Chart.ChartPaintContext paintContext) {
        Object lock = getLock();
        synchronized (lock) {
            this.activePaintContext = paintContext;
            if (paintContext.rootComponent != this)
                paintContext.drawRect = paintContext.sessionBounds;
            else {
                if (legend != null)
                    if (legend.getParent() == this)
                        if (!legend.isPreferredSizeSet())
                            legend.setPreferredSize(null);
                paintContext.componentBounds = ((ChartLayout) super.getLayout()).computeBounds(this, paintContext.sessionBounds);
                if (legend != null)
                    if (legend.getParent() == this)
                        if (paintContext.componentBounds.get(legend) == null) {
                            legend.setSize(legend.getPreferredSize());
                            paintContext.componentBounds.put(legend, legend.getBounds());
                        }
                paintContext.drawRect = paintContext.componentBounds.get(chartArea);
            }
            paintContext.plotRect = null;
            usingEventThread = false;
        }
    }

    /// Ends the current off-screen paint session and clears temporary layout state.
    ///
    /// @param paintContext the session being ended; present for API symmetry with
    ///                         [#startSession(ChartPaintContext)]
    public final void endSession(Chart.ChartPaintContext paintContext) {
        assert paintContext != null;
        Object lock = getLock();
        synchronized (lock) {
            usingEventThread = defaultUsingEventThread;
            this.activePaintContext = null;
            getChartArea().clearLayoutCachesAfterPaint();
        }
    }

    /// Returns whether the chart is currently rendering through an off-screen paint session.
    public final boolean isPaintingImage() {
        return activePaintContext != null;
    }

    /// Paints the chart immediately using the active off-screen [ChartPaintContext].
    ///
    /// The caller is responsible for providing a graphics context compatible with the current
    /// session bounds established through [#startSession(ChartPaintContext)].
    ///
    /// @param g          the graphics context to paint into
    /// @param background the background override to use for this paint, or `null` to resolve the
    ///                       usual chart background paint
    public void paintCurrentThread(Graphics2D g, Color background) {
        Object lock = getLock();
        synchronized (lock) {
            this.updateScales(getChartArea().getPlotRect());
            Paint resolvedBackground = background;
            if (resolvedBackground == null) {
                resolvedBackground = getBackgroundPaint();
                if (resolvedBackground == null)
                    resolvedBackground = Chart.findOpaqueBackground((Component) this);
            }
            if (resolvedBackground != null) {
                g.setPaint(resolvedBackground);
                G2D.fillRect(g, activePaintContext.sessionBounds.x, activePaintContext.sessionBounds.y, activePaintContext.sessionBounds.width, activePaintContext.sessionBounds.height);
            }
            if (super.getBorder() != null)
                super.getBorder().paintBorder(this, g, 0, 0, activePaintContext.sessionBounds.width, activePaintContext.sessionBounds.height);
            Rectangle chartAreaBounds = activePaintContext.componentBounds.get(chartArea);
            g.translate(chartAreaBounds.x, chartAreaBounds.y);
            chartArea.paintComponent(g);
            g.translate(-chartAreaBounds.x, -chartAreaBounds.y);
            if (chartArea.getBorder() != null)
                chartArea.getBorder().paintBorder(chartArea, g, activePaintContext.drawRect.x, activePaintContext.drawRect.y, activePaintContext.drawRect.width, activePaintContext.drawRect.height);
            if (header != null)
                if (this.header instanceof JLabel)
                    Chart.paintLabelForExport((JLabel) header, g, activePaintContext.componentBounds.get(header));
            if (footer != null)
                if (this.footer instanceof JLabel)
                    Chart.paintLabelForExport((JLabel) footer, g, activePaintContext.componentBounds.get(footer));
            Legend legend = getLegend();
            if (legend != null)
                if (legend.getParent() == this) {
                    Rectangle legendBounds = activePaintContext.componentBounds.get(legend);
                    if (legendBounds == null)
                        legendBounds = legend.getBounds();
                    if (legendBounds != null)
                        if (legendBounds.width > 0)
                            if (legendBounds.height > 0) {
                                g.translate(legendBounds.x, legendBounds.y);
                                legend.toImage(g, legendBounds.width, legendBounds.height, background);
                                g.translate(-legendBounds.x, -legendBounds.y);
                            }
                }
            invalidateScales();
        }
    }

    static void paintLabelForExport(JLabel label, Graphics g, Rectangle bounds) {
        if (label.isOpaque()) {
            Color labelBackground = label.getBackground();
            if (labelBackground != null) {
                g.setColor(labelBackground);
                g.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
            }
        }
        Font labelFont = label.getFont();
        if (labelFont != null)
            g.setFont(labelFont);
        if (!(label instanceof JLabelExt))
            GraphicUtil.paintJLabel(g, label, bounds);
        else
            ((JLabelExt) label).paintForegroundContent(g, bounds);
        if (label.getBorder() != null)
            label.getBorder().paintBorder(label, g, bounds.x, bounds.y, bounds.width, bounds.height);
    }

    void paintCurrentThread(Graphics2D g) {
        paintCurrentThread(g, null);
    }

    private BufferedImage paintToImage(int width, int height, Color background, JComponent rootComponent) {
        Object lock = getLock();
        synchronized (lock) {
            boolean startOwnSession = activePaintContext == null;
            if (startOwnSession)
                startSession(new Chart.ChartImagePaintContext(width, height, rootComponent));
            BufferedImage image;
            try {
                Graphics2D g2 = (Graphics2D) ((Chart.ChartImagePaintContext) activePaintContext).getGraphics();
                if (rootComponent == this)
                    paintCurrentThread(g2, background);
                else
                    chartArea.paintCurrentThread(g2, background);
                g2.dispose();
                image = ((Chart.ChartImagePaintContext) activePaintContext).getImage();
            } finally {
                if (startOwnSession)
                    endSession(activePaintContext);
            }
            return image;
        }
    }

    /// Renders this chart into a new image of the requested size.
    ///
    /// @param width      the export width in pixels
    /// @param height     the export height in pixels
    /// @param background the background override to use, or `null` to resolve the usual chart
    ///                       background
    /// @return the rendered image
    public BufferedImage toImage(int width, int height, Color background) {
        return this.paintToImage(width, height, background, this);
    }

    /// Writes this chart as PNG at the requested export size.
    ///
    /// @param out                   the destination stream
    /// @param width                 the export width in pixels
    /// @param height                the export height in pixels
    /// @param transparentBackground `true` to encode the nearest opaque ancestor background as
    ///                                  transparent PNG metadata
    /// @throws IOException if the PNG write fails
    public void toPNG(OutputStream out, int width, int height, boolean transparentBackground) throws IOException {
        BufferedImage image = this.toImage(width, height, null);
        Color transparentColor = (!transparentBackground) ? null : Chart.findOpaqueBackground((Component) this);
        Chart.writePng(image, out, transparentColor);
    }

    /// Writes this chart as PNG at the requested export size with an opaque background.
    ///
    /// @param out    the destination stream
    /// @param width  the export width in pixels
    /// @param height the export height in pixels
    /// @throws IOException if the PNG write fails
    public void toPNG(OutputStream out, int width, int height) throws IOException {
        this.toPNG(out, width, height, false);
    }

    /// Releases chart-owned helper objects and disconnects external bindings.
    ///
    /// After disposal the chart area reference is cleared, so the instance should be treated as no
    /// longer usable for normal rendering or export.
    public void dispose() {
        if (chartArea != null) {
            if (decorations != null) {
                Iterator<ChartDecoration> decorationIterator = decorations.iterator();
                while (decorationIterator.hasNext()) {
                    ChartDecoration decoration = decorationIterator.next();
                    decoration.setChartInternal(null);
                }
                decorations = null;
            }
            setXScrollBar(null);
            ScalableFontManager fontManager = getFontManager();
            if (fontManager != null)
                fontManager.dispose();
            for (int axisElementIndex = 0; axisElementIndex < axisElements.size(); axisElementIndex++) {
                Scale scale = axisElements.get(axisElementIndex).getScale();
                if (scale != null)
                    scale.disposeAnnotations();
            }
            chartArea = null;
        }
    }

    static {
        bv = !one.chartsy.charting.Chart.class.desiredAssertionStatus();
    }

    private static int ARRAYLENGTH(Object array) {
        return Array.getLength(array);
    }
}
