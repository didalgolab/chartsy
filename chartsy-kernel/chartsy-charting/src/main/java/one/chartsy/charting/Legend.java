package one.chartsy.charting;

import java.awt.Color;
import java.awt.Component;
import java.awt.ComponentOrientation;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLayeredPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.border.TitledBorder;
import javax.swing.event.EventListenerList;

import one.chartsy.charting.event.ChartRendererEvent;
import one.chartsy.charting.event.ChartRendererListener;
import one.chartsy.charting.event.ChartRendererListener2;
import one.chartsy.charting.event.LegendDockingEvent;
import one.chartsy.charting.event.LegendDockingListener;
import one.chartsy.charting.event.LegendEvent;
import one.chartsy.charting.event.LegendListener;
import one.chartsy.charting.internal.AbstractPaintAction;
import one.chartsy.charting.util.ColorUtil;
import one.chartsy.charting.util.Flags;
import one.chartsy.charting.util.GraphicUtil;
import one.chartsy.charting.util.swing.SwingUtil;
import one.chartsy.charting.util.text.BidiUtil;

/// Displays a Swing legend composed of [LegendEntry] rows for a [Chart] or for standalone overlay
/// panels.
///
/// A legend can be connected to one chart at a time. When attached, it derives its default title
/// from renderer metadata, rebuilds renderer-owned legend rows whenever renderer configuration
/// changes, and can either stay docked in a [ChartLayout] slot or float above the chart. Desktop
/// code in this repository also reuses the component outside a chart and manages its bounds and
/// visibility manually while still using the same legend-entry rendering logic.
///
/// Instances are mutable UI components and are not independently thread-safe. Internal
/// synchronization through [#getLock()] coordinates export-time layout and renderer-driven rebuild
/// work, but callers should still treat the API as Swing-oriented and mutate it from the same UI
/// thread as the owning chart.
public class Legend extends JComponent {
    
    private static final String DRAG_POSITION_CLIENT_PROPERTY = "_DragPosition_Key__";
    
    /// Off-screen legend paint context backed by a dedicated [BufferedImage].
    ///
    /// [Legend#toImage(Graphics2D, int, int, Color)] installs this variant when the legend owns
    /// the export raster. Keeping the image together with the inherited draw rectangle lets
    /// `installPaintContext(...)` compute server-side child bounds for the requested export size
    /// before the legend paints, instead of relying on a live Swing hierarchy update.
    private static class LegendImagePaintContext extends LegendPaintContext {
        /// RGB image that receives the exported legend paint pass.
        BufferedImage image;
        
        /// Creates a new image-backed legend export context.
        ///
        /// @param width the destination image width in pixels
        /// @param height the destination image height in pixels
        public LegendImagePaintContext(int width, int height) {
            super(width, height);
            image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        }
    }
    
    /// Shared mouse handler installed on every [LegendEntry] owned by this legend.
    ///
    /// [Legend#addLegendItem(LegendEntry)] attaches one instance of this interactor to each entry.
    /// A left-button press that [Legend#itemClickMayMoveLegend(LegendEntry)] rejects arms an
    /// entry-activation gesture. That gesture suppresses the drag pathway until the matching click
    /// callback delivers [Legend#itemClicked(LegendEntry)]. All other gestures are delegated to
    /// [MoveLegendInteractor] whenever built-in legend dragging is enabled.
    private final class LegendItemInteractor extends MouseAdapter implements MouseMotionListener, Serializable {
        /// Whether the current pointer sequence should end as an item click instead of a drag.
        private boolean itemClickArmed;
        
        private LegendItemInteractor() {
        }
        
        /// Completes an armed entry click or forwards the click to the legend drag interactor.
        @Override
        public void mouseClicked(MouseEvent e) {
            if (!itemClickArmed) {
                if (moveInteractionEnabled)
                    moveLegendInteractor.mouseClicked(e);
                return;
            }
            if (SwingUtilities.isLeftMouseButton(e)) {
                LegendEntry item = (LegendEntry) e.getSource();
                itemClicked(item);
            }
        }
        
        /// Forwards drag updates only for gestures that were not reserved for entry activation.
        @Override
        public void mouseDragged(MouseEvent e) {
            if (itemClickArmed)
                return;
            if (moveInteractionEnabled)
                moveLegendInteractor.mouseDragged(e);
        }
        
        /// Clears stale click state when the pointer enters an entry and refreshes drag hover state
        /// for passive pointer movement.
        @Override
        public void mouseEntered(MouseEvent e) {
            itemClickArmed = false;
            if (e.getButton() == MouseEvent.NOBUTTON)
                mouseMoved(e);
        }
        
        /// Keeps drag hover handling in sync while the pointer moves across legend entries.
        @Override
        public void mouseMoved(MouseEvent e) {
            if (moveInteractionEnabled)
                moveLegendInteractor.mouseMoved(e);
        }
        
        /// Arms a click-only gesture for entries that should not start legend dragging.
        @Override
        public void mousePressed(MouseEvent e) {
            if (SwingUtilities.isLeftMouseButton(e)) {
                LegendEntry item = (LegendEntry) e.getSource();
                if (!itemClickMayMoveLegend(item)) {
                    itemClickArmed = true;
                    return;
                }
            }
            itemClickArmed = false;
            if (moveInteractionEnabled)
                moveLegendInteractor.mousePressed(e);
        }
        
        /// Finishes only delegated drag gestures. Armed entry clicks wait for `mouseClicked(...)`.
        @Override
        public void mouseReleased(MouseEvent e) {
            if (itemClickArmed)
                return;
            if (moveInteractionEnabled)
                moveLegendInteractor.mouseReleased(e);
        }
    }
    
    /// Paint action used by [Legend#toImage(BufferedImage, boolean)].
    ///
    /// Docked legends borrow layout constraints from their owning [Chart]. This helper therefore
    /// selects the chart as the export root for docked chart-owned legends so the export pipeline
    /// can preserve chart-managed legend sizing. Floating and standalone legends remain
    /// self-contained and therefore export against the legend component itself.
    ///
    /// The action never asks the chart to render into the image. The chart is used only as
    /// hierarchy and sizing context for the legend paint pass.
    private final class LegendPaintAction extends AbstractPaintAction {
        
        /// Creates the helper for one legend export pass.
        ///
        /// @param temporaryParent transient parent used when the selected export root is detached
        public LegendPaintAction(Container temporaryParent) {
            super(temporaryParent);
        }
        
        /// Uses the owning chart as the temporary export root only for docked chart-owned legends.
        @Override
        protected JComponent getRootComponent(JComponent c) {
            if (c.getParent() == getChart())
                if (!isFloating())
                    return getChart();
            return c;
        }
        
        /// Resizes the legend and refreshes docked chart layout from the chart's preferred size.
        ///
        /// Recomputing the chart size keeps the legend's chart-layout slot aligned with the export
        /// dimensions instead of leaving the legend at an arbitrary standalone size.
        @Override
        protected void resizeComponent(JComponent c, Dimension size) {
            c.setSize(size);
            if (c.getParent() == getChart())
                if (!isFloating()) {
                    Chart chart = getChart();
                    chart.setPreferredSize(null);
                    chart.setSize(chart.getPreferredSize());
                }
        }
    }
    
    /// Mutable layout state for one off-screen legend paint pass.
    ///
    /// [Legend#installPaintContext(LegendPaintContext)] installs this helper before
    /// [Legend#paintLegend(Graphics2D, Color)] renders without a live Swing layout pass. The
    /// context carries the requested legend drawing rectangle together with optional per-component
    /// bounds predicted by the active [ServerSideLayout], allowing [LegendEntry] rows and other
    /// child components to paint against export-time geometry instead of their current on-screen
    /// bounds.
    private static class LegendPaintContext {
        /// Drawing rectangle assigned to the legend for the current export pass.
        ///
        /// [Legend#paintLegend(Graphics2D, Color)] uses this rectangle's width and height when it
        /// fills the background, clips child painting, and paints the legend border.
        public Rectangle drawRect;
        
        /// Component bounds computed for the active export pass.
        ///
        /// The map reference is retained directly and may stay `null` until
        /// [Legend#installPaintContext(LegendPaintContext)] asks the active [ServerSideLayout] to
        /// predict child locations. [Legend#paintLegend(Graphics2D, Color)] then looks up each
        /// component by identity while translating painting into export coordinates.
        Map<Component, Rectangle> componentBounds;
        
        /// Creates a legend paint context for the requested export size.
        ///
        /// @param width the legend width in pixels
        /// @param height the legend height in pixels
        public LegendPaintContext(int width, int height) {
            drawRect = new Rectangle(0, 0, width, height);
        }
    }
    
    /// Handles drag gestures that move a chart-owned [Legend] between docked [ChartLayout]
    /// positions and floating mode.
    ///
    /// The interactor is installed by [Legend#setMovable(boolean)] and operates only while the
    /// legend remains attached directly to its owning [Chart]. During a drag it keeps
    /// `DRAG_POSITION_CLIENT_PROPERTY` updated so [LegendLayout] and [LegendSeparator] can preview
    /// the candidate docking geometry before release.
    ///
    /// Releasing over a docked region delegates the final reattachment to
    /// [Chart#addLegend(Legend, String)]. Releasing over the plot area keeps the legend floating,
    /// preserves the new floating location on the chart, and notifies docking listeners through
    /// the legend's normal event path.
    static final class MoveLegendInteractor extends MouseAdapter implements MouseMotionListener {
        private boolean dragging;
        private boolean detachedFromOriginalPosition;
        private String originalPosition;
        private String currentPosition;
        private String previousPosition;
        private final Legend legend;
        private Point dragStart;
        private Point currentPoint;
        private Point previousPoint;
        private Rectangle originalBounds;
        private Rectangle chartAreaBounds;
        
        /// Creates the drag helper for one legend instance.
        ///
        /// @param legend the legend whose drag state is tracked for the lifetime of this interactor
        public MoveLegendInteractor(Legend legend) {
            dragging = false;
            detachedFromOriginalPosition = false;
            originalPosition = null;
            currentPosition = null;
            previousPosition = null;
            dragStart = new Point();
            currentPoint = new Point();
            chartAreaBounds = new Rectangle();
            this.legend = legend;
        }
        
        /// Resolves a pointer location in chart coordinates to the docking slot currently being
        /// previewed.
        ///
        /// Pointer locations inside the chart area map to [ChartLayout#ABSOLUTE], which keeps the
        /// legend floating. Locations to the left or right of the chart area map to west or east
        /// docking slots, split into upper, middle, and lower regions by chart height so the
        /// legend can dock beside the chart header or footer bands.
        ///
        /// @param x the horizontal pointer coordinate in the owning chart
        /// @param y the vertical pointer coordinate in the owning chart
        /// @return the candidate [ChartLayout] position for the current drag location
        private String resolvePosition(int x, int y) {
            int chartHeight = legend.getChart().getHeight();
            chartAreaBounds = legend.getChart().getChartArea().getBounds(chartAreaBounds);
            if (x <= chartAreaBounds.x) {
                if (y <= chartHeight / 3)
                    return ChartLayout.NORTH_WEST;
                if (y <= 2 * chartHeight / 3)
                    return ChartLayout.WEST;
                return ChartLayout.SOUTH_WEST;
            }
            if (x < chartAreaBounds.x + chartAreaBounds.width) {
                if (y <= chartAreaBounds.y)
                    return ChartLayout.NORTH_BOTTOM;
                if (y >= chartAreaBounds.y + chartAreaBounds.height)
                    return ChartLayout.SOUTH_TOP;
                return ChartLayout.ABSOLUTE;
            }
            if (y <= chartHeight / 3)
                return ChartLayout.NORTH_EAST;
            if (y <= 2 * chartHeight / 3)
                return ChartLayout.EAST;
            return ChartLayout.SOUTH_EAST;
        }
        
        /// Updates the live docking preview for the current drag location.
        ///
        /// The first drag step promotes a docked legend to the chart's palette layer so it can be
        /// moved independently of [ChartLayout]. While the pointer stays inside the plot area the
        /// legend behaves as floating and follows the drag delta. When the pointer moves into a
        /// docking zone, the legend snaps to the bounds associated with that slot. Returning to
        /// the original slot after leaving it restores the originally docked bounds instead of
        /// continuing the free-form preview.
        @Override
        public void mouseDragged(MouseEvent e) {
            block: if (dragging) {
                Chart chart = legend.getChart();
                Chart.Area chartArea = chart.getChartArea();
                previousPoint = currentPoint;
                currentPoint = SwingUtil.convertPoint((JComponent) e.getSource(), e.getPoint(), chart);
                if (!legend.isFloating()) {
                    legend.setFloating(true);
                    chart.setLayer(legend, JLayeredPane.PALETTE_LAYER.intValue());
                    chart.getLayout().removeLayoutComponent(legend);
                    legend.setLocation(currentPoint.x, currentPoint.y);
                    legend.setSize(legend.getPreferredSize());
                    legend.validate();
                }
                previousPosition = currentPosition;
                currentPosition = resolvePosition(currentPoint.x, currentPoint.y);
                legend.putClientProperty(DRAG_POSITION_CLIENT_PROPERTY, currentPosition);
                block_2: if (!ChartLayout.ABSOLUTE.equals(currentPosition)) {
                    if (!Objects.equals(currentPosition, previousPosition))
                        if (ChartLayout.ABSOLUTE.equals(previousPosition))
                            break block_2;
                    if (!Objects.equals(currentPosition, originalPosition))
                        break block;
                    if (detachedFromOriginalPosition)
                        break block;
                    legend.setLocation(currentPoint.x, currentPoint.y);
                    break block;
                } // end block_2
                
                detachedFromOriginalPosition = true;
                int dx = currentPoint.x - previousPoint.x;
                int dy = currentPoint.y - previousPoint.y;
                legend.invalidate();
                Dimension preferredSize = legend.getPreferredSize();
                if (ChartLayout.ABSOLUTE.equals(currentPosition)) {
                    legend.setSize(preferredSize);
                    if (!Objects.equals(previousPosition, currentPosition))
                        legend.setLocation(currentPoint.x, currentPoint.y);
                    else
                        legend.setLocation(legend.getX() + dx, legend.getY() + dy);
                } else if (Objects.equals(currentPosition, originalPosition))
                    legend.setBounds(originalBounds);
                else if (ChartLayout.NORTH_BOTTOM.equals(currentPosition))
                    legend.setBounds(0, chartArea.getY(), chart.getWidth(), preferredSize.height);
                else if (ChartLayout.NORTH_WEST.equals(currentPosition))
                    legend.setBounds(0, 0, preferredSize.width, chartArea.getY() + chartArea.getHeight());
                else if (ChartLayout.WEST.equals(currentPosition))
                    legend.setBounds(0, chartArea.getY(), preferredSize.width, chartArea.getHeight());
                else if (ChartLayout.SOUTH_WEST.equals(currentPosition))
                    legend.setBounds(0, chartArea.getY(), preferredSize.width, chart.getHeight() - chartArea.getY());
                else if (ChartLayout.SOUTH_TOP.equals(currentPosition))
                    legend.setBounds(0, chartArea.getY() + chartArea.getHeight() - preferredSize.height, chart.getWidth(), preferredSize.height);
                else if (ChartLayout.SOUTH_EAST.equals(currentPosition))
                    legend.setBounds(chartArea.getX() + chartArea.getWidth() - preferredSize.width, chartArea.getY(), preferredSize.width, chart.getHeight() - chartArea.getY());
                else if (ChartLayout.EAST.equals(currentPosition))
                    legend.setBounds(chartArea.getX() + chartArea.getWidth() - preferredSize.width, chartArea.getY(), preferredSize.width, chartArea.getHeight());
                else if (ChartLayout.NORTH_EAST.equals(currentPosition))
                    legend.setBounds(chartArea.getX() + chartArea.getWidth() - preferredSize.width, 0, preferredSize.width, chartArea.getY() + chartArea.getHeight());
                legend.validate();
            } // end block
        
        }
        
        /// Ignores passive pointer movement. Preview updates happen only during active drags.
        @Override
        public void mouseMoved(MouseEvent event) {
        }
        
        /// Starts a drag sequence only for legends currently hosted by their owning chart.
        ///
        /// The method snapshots the current docking position and bounds so subsequent drag updates
        /// can preview a return to the original slot and the release step can report the old
        /// position if the drag changes docking mode.
        @Override
        public void mousePressed(MouseEvent e) {
            if (legend.getParent() != legend.getChart())
                return;
            dragging = true;
            dragStart = SwingUtil.convertPoint((JComponent) e.getSource(), e.getPoint(), legend.getChart());
            currentPoint = dragStart;
            originalPosition = legend.getPosition();
            currentPosition = originalPosition;
            originalBounds = legend.getBounds();
        }
        
        /// Commits the last previewed position and clears drag-preview state.
        ///
        /// Docked targets are committed through [Chart#addLegend(Legend, String)], letting the
        /// chart own the final layout and layer updates. The floating target keeps the legend on
        /// the palette layer, resizes it to its preferred size, stores the new floating location
        /// on the chart, and emits a [LegendDockingEvent] that reports the position captured when
        /// the drag started.
        @Override
        public void mouseReleased(MouseEvent e) {
            if (dragging) {
                dragging = false;
                detachedFromOriginalPosition = false;
                legend.putClientProperty(DRAG_POSITION_CLIENT_PROPERTY, null);
                if (currentPosition != null) {
                    if (!ChartLayout.ABSOLUTE.equals(currentPosition))
                        legend.getChart().addLegend(legend, currentPosition);
                    else {
                        legend.setFloating(true);
                        legend.invalidate();
                        legend.setSize(legend.getPreferredSize().width, legend.getPreferredSize().height);
                        legend.validate();
                        if (!Objects.equals(currentPosition, originalPosition)) {
                            legend.getChart().invalidate();
                            legend.getChart().validate();
                        }
                        legend.getChart().setFloatingLegendLocation(legend.getLocation());
                        legend.fireLegendDockingEvent(new LegendDockingEvent(legend, originalPosition));
                    }
                    originalPosition = currentPosition;
                }
            }
        }
    }
    
    /// Chart-owned listener that keeps renderer-derived legend content synchronized with renderer
    /// change notifications.
    ///
    /// [Legend#chartConnected(Chart, Chart)] attaches this listener while the legend is owned by a
    /// chart. Because it implements [ChartRendererListener2], the listener mirrors the chart's
    /// nested renderer-change batches and coalesces repeated refresh-worthy events into at most one
    /// [Legend#refreshFromRendererChanges()] call per outermost batch.
    ///
    /// Renderer events that only affect data ranges or annotation layout are ignored because the
    /// legend rebuild path depends on renderer metadata and [LegendEntry] providers rather than the
    /// current plotted bounds.
    private final class RendererRefreshListener implements ChartRendererListener2, Serializable {
        /// Outstanding renderer-change batch depth observed from the owning [Chart].
        ///
        /// [Chart#addChartRendererListener(ChartRendererListener)] replays already-open batches to
        /// newly attached [ChartRendererListener2] instances, so this counter must track both live
        /// and synthetic callbacks.
        private int batchDepth;

        /// Whether a refresh-worthy renderer event arrived before the current batch closed.
        private boolean refreshPending;
        
        /// Creates the listener with no active batch and no deferred refresh.
        private RendererRefreshListener() {
            batchDepth = 0;
            refreshPending = false;
        }
        
        /// Closes one observed renderer-change batch and flushes any deferred legend refresh when
        /// the outermost batch ends.
        @Override
        public void endRendererChanges() {
            if (batchDepth > 0) {
                batchDepth--;
                if (batchDepth == 0 && refreshPending) {
                    refreshPending = false;
                    refreshFromRendererChanges();
                }
            }
        }
        
        /// Refreshes legend content after renderer changes that can alter legend-derived UI state.
        ///
        /// `ChartRendererEvent.DATARANGE_CHANGED` and `ChartRendererEvent.ANNOTATION_CHANGED` are
        /// ignored because [Legend#refreshFromChart()] only rebuilds the title and legend entries,
        /// not plot geometry.
        @Override
        public void rendererChanged(ChartRendererEvent event) {
            switch (event.getType()) {
            case 5:
            case 8:
                return;
                
            default:
                if (batchDepth <= 0)
                    refreshFromRendererChanges();
                else
                    refreshPending = true;

            }
        }
        
        /// Opens one nested renderer-change batch observed from the owning chart.
        @Override
        public void startRendererChanges() {
            batchDepth++;
        }
    }
    
    /// Chart-owned resize listener that keeps a floating legend aligned with its stored chart-edge
    /// offsets.
    ///
    /// [Legend#chartConnected(Chart, Chart)] and [Legend#setFollowChartResize(boolean)] attach this
    /// listener only while automatic floating-legend relocation is enabled. The callback does not
    /// inspect the incoming event payload because all required geometry comes from the owning
    /// legend and its current chart.
    private final class ChartResizeListener extends ComponentAdapter implements Serializable {
        
        private ChartResizeListener() {
        }
        
        /// Recomputes the floating legend location after the owning chart changes size.
        ///
        /// Docked legends are ignored because [#updateFloatingLocation()] returns immediately unless
        /// the legend is currently floating.
        @Override
        public void componentResized(ComponentEvent event) {
            updateFloatingLocation();
        }
    }
    
    static void assertNotOnEventDispatchThreadForDetachedLegend() {
        Chart.assertNotOnEventDispatchThreadForDetachedChart();
    }
    
    private static boolean sameColor(Color first, Color second) {
        if (first == null)
            return second == null;
        return second != null && second.getClass() == first.getClass() && second.equals(first);
    }
    
    private final Object lock;
    private final Flags stateFlags;
    private Dimension markerSize;
    private int cellSpacing;
    private Chart chart;
    private EventListenerList legendListeners;
    private EventListenerList legendDockingListeners;
    private int transparency;
    private String title;
    /// Listener reused across chart attachment changes to keep renderer-driven legend state current.
    private final ChartRendererListener rendererRefreshListener;
    /// Lazily created chart listener used by [#setFollowChartResize(boolean)].
    private ComponentListener chartResizeListener;
    private int floatingOffsetX;
    private int floatingOffsetY;
    private int baseTextDirection;
    private transient Legend.MoveLegendInteractor moveLegendInteractor;
    private transient boolean moveInteractionEnabled;
    
    private final Legend.LegendItemInteractor legendItemInteractor;
    
    private transient Color cachedTranslucentBackground;
    
    private transient Color cachedBackgroundColor;
    
    private transient Dimension dynamicSize;
    
    private Legend.LegendPaintContext paintContext;
    
    /// Creates an empty legend with the default [LegendLayout], translucent background settings,
    /// and drag support enabled.
    ///
    /// The default state matches the chart-owned legends created by [Chart#createLegend()]: title
    /// text is initially resolved lazily from attached renderers, background painting is disabled
    /// until callers opt in, and entry clicks can later be turned into [LegendEvent] dispatch by
    /// enabling interactivity and registering listeners.
    public Legend() {
        lock = new Serializable() {
        };
        stateFlags = new Flags(5);
        markerSize = new Dimension(21, 15);
        cellSpacing = 4;
        chart = null;
        legendListeners = null;
        legendDockingListeners = null;
        transparency = 185;
        title = null;
        rendererRefreshListener = new RendererRefreshListener();
        baseTextDirection = 513;
        legendItemInteractor = new Legend.LegendItemInteractor();
        super.setLocale(Locale.getDefault());
        initializeTransientState();
        super.putClientProperty("__Chart_Component__", Boolean.TRUE);
        super.enableEvents(48L);
        super.setLayout(createLegendLayout());
        super.setOpaque(false);
        updateUI();
        TitledBorder titledBorder = BorderFactory.createTitledBorder("");
        super.setBorder(titledBorder);
        setMovable(true);
    }
    
    private void setMoveInteractionEnabled(boolean moveInteractionEnabled) {
        if (moveInteractionEnabled != this.moveInteractionEnabled) {
            this.moveInteractionEnabled = moveInteractionEnabled;
            if (moveInteractionEnabled) {
                if (moveLegendInteractor == null)
                    moveLegendInteractor = new Legend.MoveLegendInteractor(this);
                super.addMouseListener(moveLegendInteractor);
                super.addMouseMotionListener(moveLegendInteractor);
            } else if (moveLegendInteractor != null) {
                super.removeMouseListener(moveLegendInteractor);
                super.removeMouseMotionListener(moveLegendInteractor);
            }
        }
    }
    
    void setChart(Chart chart) {
        Chart previousChart = this.chart;
        if (chart != previousChart) {
            if (chart == null)
                setFloating(false);
            this.chart = chart;
            chartConnected(previousChart, chart);
        }
    }
    
    void paintLegend(Graphics2D g, Color background) {
        Color resolvedBackground = background;
        Object lock = getLock();
        synchronized (lock) {
            int width = paintContext.drawRect.width;
            int height = paintContext.drawRect.height;
            Map<Component, Rectangle> componentBounds = paintContext.componentBounds;
            if (resolvedBackground == null)
                resolvedBackground = computeBackgroundColor();
            if (resolvedBackground == null)
                resolvedBackground = (Color) UIManager.get("Panel.background");
            if (isPaintingBackground()) {
                g.setColor(resolvedBackground);
                g.fillRect(0, 0, width, height);
            }
            int antiAliasingStarted;
            block: {
                if (isAntiAliasing())
                    if (!GraphicUtil.startAntiAliasing(g)) {
                        antiAliasingStarted = 1;
                        break block;
                    }
                antiAliasingStarted = 0;
            } // end block
            
            int stopAntiAliasing = antiAliasingStarted;
            block_2: {
                if (isAntiAliasingText())
                    if (!GraphicUtil.startTextAntiAliasing(g)) {
                        antiAliasingStarted = 1;
                        break block_2;
                    }
                antiAliasingStarted = 0;
            } // end block_2
            
            int stopTextAntiAliasing = antiAliasingStarted;
            int index = 0;
            while (true) {
                if (index >= super.getComponentCount())
                    break;
                Component component = super.getComponent(index);
                Rectangle bounds = null;
                if (componentBounds != null)
                    bounds = componentBounds.get(component);
                if (bounds == null)
                    bounds = component.getBounds();
                g.translate(bounds.x, bounds.y);
                if (component instanceof LegendEntry entry) {
                    if (entry.isOpaque()) {
                        resolvedBackground = entry.getBackground();
                        if (resolvedBackground != null) {
                            g.setColor(resolvedBackground);
                            g.fillRect(0, 0, bounds.width, bounds.height);
                        }
                    }
                    entry.setDynamicBounds(bounds);
                    try {
                        entry.paintEntry(g);
                        if (entry.getBorder() != null)
                            entry.getBorder().paintBorder(entry, g, 0, 0, bounds.width, bounds.height);
                    } finally {
                        entry.setDynamicBounds((Rectangle) null);
                    }
                }
                g.translate(-bounds.x, -bounds.y);
                index++;
            }
            if (super.getBorder() != null) {
                if (g.getClipBounds() == null)
                    g.setClip(0, 0, width, height);
                super.getBorder().paintBorder(this, g, 0, 0, width, height);
            }
            if (stopAntiAliasing != 0)
                GraphicUtil.stopAntiAliasing(g);
            if (stopTextAntiAliasing != 0)
                GraphicUtil.stopTextAntiAliasing(g);
        }
    }
    
    private void installPaintContext(LegendPaintContext paintContext) {
        Object lock = getLock();
        synchronized (lock) {
            this.paintContext = paintContext;
            this.paintContext.componentBounds = ((ServerSideLayout) super.getLayout()).computeBounds(this, paintContext.drawRect);
        }
    }
    
    /// Registers a docking listener that is notified when this legend changes chart slot or enters
    /// floating mode while interactive.
    public void addLegendDockingListener(LegendDockingListener listener) {
        if (legendDockingListeners == null)
            legendDockingListeners = new EventListenerList();
        legendDockingListeners.add(one.chartsy.charting.event.LegendDockingListener.class, listener);
    }
    
    /// Adds one legend row or auxiliary legend component.
    ///
    /// [LegendEntry] instances are attached to this legend, inherit its foreground color, and gain
    /// the built-in click or drag interactor. Non-entry components are simply added to the Swing
    /// container and preserved across renderer-driven legend rebuilds.
    public void addLegendItem(LegendEntry item) {
        if (item != null) {
            super.add(item);
            item.setLegend(this);
            item.addMouseListener(legendItemInteractor);
            item.addMouseMotionListener(legendItemInteractor);
            item.setForeground(super.getForeground());
        }
    }
    
    /// Registers an item-click listener.
    ///
    /// Entry click events are delivered only while [#isInteractive()] is `true`.
    public void addLegendListener(LegendListener listener) {
        if (legendListeners == null)
            legendListeners = new EventListenerList();
        legendListeners.add(one.chartsy.charting.event.LegendListener.class, listener);
    }
    
    void assertNotOnEventDispatchThread() {
        if (chart == null)
            Legend.assertNotOnEventDispatchThreadForDetachedLegend();
        else
            chart.assertNotOnEventDispatchThread();
    }
    
    private void clearPaintContext() {
        paintContext = null;
    }
    
    /// Invalidates cached title text after the effective base text direction changes.
    ///
    /// Subclasses overriding this hook should preserve the default title refresh unless they fully
    /// replace title rendering.
    protected void baseTextDirectionChanged() {
        refreshTitle();
    }
    
    /// Resolves and applies the titled-border caption for the current chart and bidi state.
    ///
    /// An explicit [#getTitle()] wins. When no explicit title is set, the legend asks attached
    /// renderers for the first non-`null` default legend title and applies bidi wrapping just
    /// before the border text is updated.
    void refreshTitle() {
        Border border = super.getBorder();
        block: if (border instanceof TitledBorder titledBorder) {
            String resolvedTitle = title;
            if (resolvedTitle == null) {
                Chart owningChart = getChart();
                if (owningChart != null) {
                    for (ChartRenderer renderer : owningChart.getRenderers()) {
                        resolvedTitle = renderer.getDefaultLegendTitle();
                        if (resolvedTitle != null)
                            break;
                    }
                }
                if (resolvedTitle == null)
                    resolvedTitle = "";
            }
            if (!resolvedTitle.equals(titledBorder.getTitle())) {
                resolvedTitle = BidiUtil.getCombinedString(resolvedTitle, getResolvedBaseTextDirection(),
                        super.getComponentOrientation(), false);
                titledBorder.setTitle(resolvedTitle);
                if (chart == null) {
                    if (Chart.getNoEventThreadUpdate())
                        break block;
                } else if (!chart.isUsingEventThread())
                    break block;
                revalidate();
                repaint();
            }
        } // end block
        
    }
    
    /// Handles attachment to a new chart.
    ///
    /// The default implementation moves the renderer-change listener, updates follow-resize state,
    /// propagates inherited base text direction, and refreshes both the title and renderer-owned
    /// legend rows.
    protected void chartConnected(Chart oldChart, Chart newChart) {
        if (oldChart != null) {
            oldChart.removeChartRendererListener(rendererRefreshListener);
            if (isFollowChartResize())
                oldChart.removeComponentListener(chartResizeListener);
        }
        if (newChart != null) {
            newChart.addChartRendererListener(rendererRefreshListener);
            if (isFollowChartResize()) {
                if (chartResizeListener == null)
                    chartResizeListener = new ChartResizeListener();
                newChart.addComponentListener(chartResizeListener);
                captureFloatingOffsets();
            }
        }
        if (getBaseTextDirection() == 513)
            propagateBaseTextDirectionChange();
        refreshFromChart();
    }
    
    /// Removes every current child component from the legend.
    ///
    /// [LegendEntry] instances are detached cleanly through [#removeLegendItem(LegendEntry)];
    /// other auxiliary components are removed directly.
    public void clear() {
        for (int index = super.getComponentCount() - 1; index >= 0; index--) {
            Component component = super.getComponent(index);
            if (!(component instanceof LegendEntry))
                super.remove(component);
            else
                removeLegendItem((LegendEntry) component);
        }
    }
    
    /// Reacts to a Swing component-orientation change.
    ///
    /// When the configured base text direction follows component orientation, title and entry text
    /// are invalidated so bidi formatting can be recomputed.
    protected void componentOrientationChanged(ComponentOrientation oldOrientation, ComponentOrientation newOrientation) {
        block: if (newOrientation.isLeftToRight() != oldOrientation.isLeftToRight()) {
            if (getConfiguredBaseTextDirection() != 514)
                if (getResolvedBaseTextDirection() != 527)
                    break block;
            baseTextDirectionChanged();
        } // end block
    
    block_2: {
            if (chart == null) {
                if (Chart.getNoEventThreadUpdate())
                    break block_2;
            } else if (!chart.isUsingEventThread())
                break block_2;
            revalidate();
        } // end block_2
        
    }
    
    /// Resolves the background color that should be painted for the current state.
    ///
    /// Floating legends attached directly to their chart use a translucent variant derived from
    /// [#getBackground()] and [#getTransparency()]. Other cases use the plain component
    /// background.
    protected Color computeBackgroundColor() {
        if (super.getParent() == getChart())
            if (isFloating())
                return getTranslucentFloatingBackground();
        return super.getBackground();
    }
    
    /// Creates the layout manager used for docked, floating, and export-time legend layout.
    protected LayoutManager createLegendLayout() {
        return new LegendLayout(this);
    }
    
    final Dimension getDynamicSizeHint() {
        return dynamicSize;
    }
    
    Map<Component, Rectangle> getPaintContextComponentBounds() {
        if (paintContext == null)
            return null;
        return paintContext.componentBounds;
    }
    
    final Rectangle getExportBounds() {
        Chart owningChart = getChart();
        if (owningChart != null && owningChart.isPaintingImage())
            return owningChart.getPaintContext().componentBounds.get(this);
        return null;
    }
    
    /// Dispatches a docking event to registered listeners.
    ///
    /// No event is delivered when the legend is non-interactive or when no docking listeners are
    /// installed.
    protected final void fireLegendDockingEvent(LegendDockingEvent event) {
        if (legendDockingListeners != null)
            if (legendDockingListeners.getListenerCount() != 0)
                if (isInteractive()) {
                    Object[] listeners = legendDockingListeners.getListenerList();
                    for (int index = listeners.length - 1; index >= 0; index -= 2)
                        ((LegendDockingListener) listeners[index]).dockingChanged(event);
                }
    }
    
    /// Dispatches an item-click event to registered listeners.
    ///
    /// No event is delivered when the legend is non-interactive or when no item listeners are
    /// installed.
    protected final void fireLegendEvent(LegendEvent event) {
        if (legendListeners != null)
            if (legendListeners.getListenerCount() != 0)
                if (isInteractive()) {
                    Object[] listeners = legendListeners.getListenerList();
                    for (int index = listeners.length - 1; index >= 0; index -= 2)
                        ((LegendListener) listeners[index]).itemClicked(event);
                }
    }
    
    /// Refreshes renderer-derived legend state, marshalling onto the event thread when configured.
    ///
    /// [RendererRefreshListener] uses this instead of calling [#refreshFromChart()] directly so
    /// batched renderer notifications still respect detached-legend update rules and
    /// [Chart#isUsingEventThread()].
    void refreshFromRendererChanges() {
        block: {
        block_2: {
        if (chart == null) {
            if (Chart.getNoEventThreadUpdate())
                break block_2;
        } else if (!chart.isUsingEventThread())
            break block_2;
        if (!SwingUtilities.isEventDispatchThread()) {
            EventQueue.invokeLater(this::refreshFromChart);
            break block;
        }
    } // end block_2
    
    refreshFromChart();
    } // end block
    
    }
    
    /// Returns the configured base text-direction mode for title, tooltip, and entry text.
    public final int getBaseTextDirection() {
        return baseTextDirection;
    }
    
    /// Returns the chart currently associated with this legend, or `null` while detached.
    public final Chart getChart() {
        return chart;
    }
    
    /// Returns the floating-layout direction forwarded to the default [LegendLayout].
    ///
    /// Returns `-1` when this legend is currently using a custom layout manager.
    public final int getFloatingLayoutDirection() {
        if (!(super.getLayout() instanceof LegendLayout))
            return -1;
        return ((LegendLayout) super.getLayout()).getFloatingLayoutDirection();
    }
    
    /// Returns the horizontal orientation forwarded to the default [LegendLayout].
    ///
    /// Returns `-1` when this legend is currently using a custom layout manager.
    public final int getHorizontalOrientation() {
        if (!(super.getLayout() instanceof LegendLayout))
            return -1;
        return ((LegendLayout) super.getLayout()).getHorizontalOrientation();
    }
    
    /// Returns the monitor used by export-time layout and renderer-driven legend rebuilds.
    ///
    /// This does not make the broader API thread-safe; it only exposes the internal lock used by
    /// existing charting code when it coordinates temporary legend state.
    public Object getLock() {
        return lock;
    }
    
    /// Returns the marker box size reserved for each [LegendEntry] row.
    public final Dimension getMarkerSize() {
        return markerSize;
    }
    
    /// Returns the legend's current chart-layout position.
    ///
    /// A floating legend reports `ChartLayout.ABSOLUTE`. A legend hosted outside its associated
    /// chart returns `null`.
    public String getPosition() {
        if (!isFloating())
            if (super.getParent() != null) {
                if (super.getParent() != getChart())
                    return null;
                return ((ChartLayout) getChart().getLayout()).getConstraint(this);
            }
        return ChartLayout.ABSOLUTE;
    }
    
    /// Returns the preferred size including any titled-border chrome.
    ///
    /// Swing's default preferred size is widened or heightened as needed so the current title fits
    /// inside the border.
    @Override
    public Dimension getPreferredSize() {
        Dimension preferredSize = super.getPreferredSize();
        Border border = super.getBorder();
        if (border instanceof TitledBorder titledBorder)
            if (titledBorder.getTitle() != null) {
                Dimension titleSize = titledBorder.getMinimumSize(this);
                int minimumWidth = 5 + titleSize.width + 5;
                int minimumHeight = titleSize.height;
                preferredSize.width = Math.max(preferredSize.width, minimumWidth);
                preferredSize.height = Math.max(preferredSize.height, minimumHeight);
                }
        return preferredSize;
    }
    
    /// Returns the concrete base text direction currently used for title, tooltip, and entry text.
    ///
    /// When [#getBaseTextDirection()] follows component orientation, this resolves the current
    /// [ComponentOrientation] into a left-to-right or right-to-left value.
    public int getResolvedBaseTextDirection() {
        int configuredDirection = getConfiguredBaseTextDirection();
        if (configuredDirection != 514)
            return configuredDirection;
        return (!super.getComponentOrientation().isLeftToRight()) ? 520 : 516;
    }
    
    /// Returns the horizontal gap between each marker box and its label text.
    public final int getSymbolTextSpacing() {
        return cellSpacing;
    }
    
    /// Returns the explicit title, or `null` when the title should be derived from renderers.
    public final String getTitle() {
        return title;
    }
    
    /// Returns the tooltip text with bidirectional formatting resolved for the current legend
    /// direction.
    @Override
    public String getToolTipText(MouseEvent event) {
        return BidiUtil.getCombinedString(super.getToolTipText(event), getResolvedBaseTextDirection(),
                super.getComponentOrientation(), false);
    }
    
    /// Returns the alpha component used when a floating legend derives a translucent background
    /// from [#getBackground()].
    public final int getTransparency() {
        return transparency;
    }
    
    /// Returns the vertical orientation forwarded to the default [LegendLayout].
    ///
    /// Returns `-1` when this legend is currently using a custom layout manager.
    public final int getVerticalOrientation() {
        if (!(super.getLayout() instanceof LegendLayout))
            return -1;
        return ((LegendLayout) super.getLayout()).getVerticalOrientation();
    }
    
    /// Rebuilds title and renderer-owned legend entries from the currently attached chart.
    void refreshFromChart() {
        refreshTitle();
        rebuildLegendItems();
    }
    
    /// Recreates chart-owned legend entries while preserving auxiliary child components.
    ///
    /// Non-[ChartRendererLegendItem] components are retained across the rebuild, renderer-derived
    /// [LegendEntry] rows are regenerated from each legended renderer's
    /// [LegendEntryProvider], and a floating legend resizes itself to the new preferred size.
    void rebuildLegendItems() {
        Object lock = getLock();
        synchronized (lock) {
            Chart owningChart = getChart();
            if (owningChart == null)
                return;
            ArrayList<Component> preservedComponents = new ArrayList<>();
            for (int index = super.getComponentCount() - 1; index >= 0; index--) {
                Component component = super.getComponent(index);
                if (!(component instanceof ChartRendererLegendItem))
                    preservedComponents.add(component);
                if (!(component instanceof LegendEntry))
                    super.remove(component);
                else
                    removeLegendItem((LegendEntry) component);
            }
            for (int rendererIndex = 0; rendererIndex < owningChart.getRendererCount(); rendererIndex++) {
                ChartRenderer renderer = owningChart.getRenderer(rendererIndex);
                if (renderer.isLegended())
                    renderer.getLegendEntryProvider().createLegendEntries().forEach(this::addLegendItem);
            }
            for (int index = preservedComponents.size() - 1; index >= 0; index--) {
                Component component = preservedComponents.get(index);
                if (!(component instanceof LegendEntry))
                    super.add(component);
                else
                    addLegendItem((LegendEntry) component);
            }
            if (isFloating())
                super.setSize(getPreferredSize());
            block:
            {
                if (chart == null) {
                    if (Chart.getNoEventThreadUpdate())
                        break block;
                } else if (!chart.isUsingEventThread())
                    break block;
                revalidate();
                repaint();
            } // end block
        }
    }
    
    /// Returns whether shape antialiasing is enabled for legend painting and export.
    public final boolean isAntiAliasing() {
        return stateFlags.getFlag(16);
    }
    
    /// Returns whether text antialiasing is enabled for legend painting and export.
    public final boolean isAntiAliasingText() {
        return stateFlags.getFlag(32);
    }
    
    /// Returns whether this legend is currently treated as floating instead of docked.
    public final boolean isFloating() {
        return stateFlags.getFlag(2);
    }
    
    /// Returns whether floating legend bounds should be refreshed when the owning chart resizes.
    public final boolean isFollowChartResize() {
        return stateFlags.getFlag(64);
    }
    
    /// Returns whether legend entry clicks and docking notifications are currently emitted.
    public final boolean isInteractive() {
        return stateFlags.getFlag(4);
    }
    
    /// Returns whether the built-in drag interactor is enabled.
    public final boolean isMovable() {
        return stateFlags.getFlag(8);
    }
    
    /// Returns whether [#paintComponent(Graphics)] fills the legend background.
    public final boolean isPaintingBackground() {
        return stateFlags.getFlag(1);
    }
    
    /// Handles a legend-entry click recognized by the built-in interactor.
    ///
    /// The default implementation wraps the clicked item in a [LegendEvent] and forwards it to
    /// [#fireLegendEvent(LegendEvent)].
    protected void itemClicked(LegendEntry item) {
        fireLegendEvent(new LegendEvent(this, item));
    }
    
    /// Decides whether pressing an entry should begin moving the legend instead of arming an item
    /// click.
    ///
    /// The default policy keeps item clicks for listeners whenever legend interactivity is enabled.
    protected boolean itemClickMayMoveLegend(LegendEntry item) {
        if (legendListeners != null)
            if (legendListeners.getListenerCount() != 0)
                return !isInteractive();
        return true;
    }
    
    private void initializeTransientState() {
        moveLegendInteractor = null;
        moveInteractionEnabled = false;
        cachedTranslucentBackground = null;
    }
    
    /// Resolves the legend's base-text-direction mode after considering chart inheritance.
    private int getConfiguredBaseTextDirection() {
        int configuredDirection = getBaseTextDirection();
        if (configuredDirection == 513)
            if (chart == null)
                configuredDirection = 514;
            else
                return chart.getResolvedBaseTextDirection();
        return configuredDirection;
    }
    
    private void propagateBaseTextDirectionChange() {
        baseTextDirectionChanged();
        Object lock = getLock();
        synchronized (lock) {
            for (int index = super.getComponentCount() - 1; index >= 0; index--) {
                Component component = super.getComponent(index);
                if (component instanceof LegendEntry)
                    ((LegendEntry) component).baseTextDirectionChanged();
            }
            block: {
                if (chart == null) {
                    if (Chart.getNoEventThreadUpdate())
                        break block;
                } else if (!chart.isUsingEventThread())
                    break block;
                revalidate();
                repaint();
            } // end block
        }
    }
    
    private Color computeTranslucentBackground() {
        Color background = super.getBackground();
        if (background == null)
            return null;
        return ColorUtil.setAlpha(background, getTransparency() / 255.0f);
    }
    
    private void refreshCachedBackground() {
        cachedBackgroundColor = super.getBackground();
        cachedTranslucentBackground = computeTranslucentBackground();
    }
    
    private Color getTranslucentFloatingBackground() {
        block: {
            if (cachedTranslucentBackground != null)
                if (sameColor(cachedBackgroundColor, super.getBackground()))
                    break block;
            refreshCachedBackground();
        } // end block
        
        return cachedTranslucentBackground;
    }
    
    /// Paints child components inside the legend insets while honoring the antialiasing flags.
    @Override
    protected void paintChildren(Graphics g) {
        int stopAntiAliasing;
        block: {
            if (isAntiAliasing())
                if (!GraphicUtil.startAntiAliasing(g)) {
                    stopAntiAliasing = 1;
                    break block;
                }
            stopAntiAliasing = 0;
        } // end block
        
        int stopTextAntiAliasing;
        block_2: {
            if (isAntiAliasingText())
                if (!GraphicUtil.startTextAntiAliasing(g)) {
                    stopTextAntiAliasing = 1;
                    break block_2;
                }
            stopTextAntiAliasing = 0;
        } // end block_2
        
        Insets insets = super.getInsets();
        int clipX = insets.left;
        int clipY = insets.top;
        int clipWidth = super.getWidth() - insets.left - insets.right;
        int clipHeight = super.getHeight() - insets.top - insets.bottom;
        Shape originalClip = g.getClip();
        g.clipRect(clipX, clipY, clipWidth, clipHeight);
        super.paintChildren(g);
        g.setClip(originalClip);
        if (stopAntiAliasing != 0)
            GraphicUtil.stopAntiAliasing(g);
        if (stopTextAntiAliasing != 0)
            GraphicUtil.stopTextAntiAliasing(g);
    }
    
    /// Paints the legend background before delegating to Swing's normal component painting.
    @Override
    protected void paintComponent(Graphics g) {
        if (isPaintingBackground()) {
            Color background = computeBackgroundColor();
            g.setColor(background);
            g.fillRect(0, 0, super.getWidth(), super.getHeight());
            g.setColor(super.getForeground());
        }
        super.paintComponent(g);
    }
    
    private void captureFloatingOffsets() {
        if (!stateFlags.getFlag(128))
            if (getChart() != null) {
                floatingOffsetX = getChart().getWidth() - super.getX() - super.getWidth();
                floatingOffsetY = getChart().getHeight() - super.getY() - super.getHeight();
                boolean preserveBottomRightOffsets = floatingOffsetX > 0 && floatingOffsetY > 0;
                stateFlags.setFlag(256, preserveBottomRightOffsets);
            }
    }
    
    private void updateFloatingLocation() {
        if (!isFloating())
            return;
        if (!stateFlags.getFlag(256))
            captureFloatingOffsets();
        int relocatedX = Math.max(0, getChart().getWidth() - super.getWidth() - floatingOffsetX);
        int relocatedY = Math.max(0, getChart().getHeight() - super.getHeight() - floatingOffsetY);
        stateFlags.setFlag(128, true);
        super.setLocation(relocatedX, relocatedY);
        stateFlags.setFlag(128, false);
    }
    
    @Serial
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        initializeTransientState();
        if (isMovable())
            setMoveInteractionEnabled(true);
    }
    
    /// Unregisters a previously added docking listener.
    public void removeLegendDockingListener(LegendDockingListener listener) {
        if (legendDockingListeners == null)
            return;
        legendDockingListeners.remove(one.chartsy.charting.event.LegendDockingListener.class, listener);
    }
    
    /// Removes a previously added legend entry and detaches its legend-specific listeners.
    public void removeLegendItem(LegendEntry item) {
        if (item != null) {
            super.remove(item);
            item.setLegend(null);
            item.removeMouseListener(legendItemInteractor);
            item.removeMouseMotionListener(legendItemInteractor);
        }
    }
    
    /// Unregisters a previously added item-click listener.
    public void removeLegendListener(LegendListener listener) {
        if (legendListeners == null)
            return;
        legendListeners.remove(one.chartsy.charting.event.LegendListener.class, listener);
    }
    
    /// Forwards repaint requests only when the legend or owning chart is configured to update
    /// through Swing's event thread.
    @Override
    public void repaint() {
        block: {
        if (chart == null) {
            if (Chart.getNoEventThreadUpdate())
                break block;
        } else if (!chart.isUsingEventThread())
            break block;
        super.repaint();
    } // end block
    
    }
    
    /// Forwards revalidation only when the legend or owning chart is configured to update through
    /// Swing's event thread.
    @Override
    public void revalidate() {
        Object lock = getLock();
        synchronized (lock) {
            block: {
            if (chart == null) {
                if (Chart.getNoEventThreadUpdate())
                    break block;
            } else if (!chart.isUsingEventThread())
                break block;
            super.revalidate();
        } // end block
        
        }
    }
    
    /// Enables or disables shape antialiasing for on-screen and export painting.
    public void setAntiAliasing(boolean antiAliasing) {
        stateFlags.setFlag(16, antiAliasing);
    }
    
    /// Enables or disables text antialiasing for on-screen and export painting.
    public void setAntiAliasingText(boolean antiAliasingText) {
        stateFlags.setFlag(32, antiAliasingText);
    }
    
    /// Updates the base background color used by [#computeBackgroundColor()].
    ///
    /// Floating legends cache a translucent derivative of this color, so changing the background
    /// also refreshes that cached value.
    @Override
    public void setBackground(Color background) {
        Color previousBackground = super.getBackground();
        super.setBackground(background);
        block: {
            if (background == null) {
                if (background == previousBackground)
                    break block;
            } else if (background.equals(previousBackground))
                break block;
            refreshCachedBackground();
        } // end block
        
    }
    
    /// Sets how title, tooltip, and entry text resolve bidirectional content.
    ///
    /// When the effective direction changes, cached title and entry text are invalidated and
    /// rebuilt.
    ///
    /// @throws IllegalArgumentException if `baseTextDirection` is not one of the supported
    ///     direction constants
    public void setBaseTextDirection(int baseTextDirection) {
        if (baseTextDirection != 514)
            if (baseTextDirection != 527)
                if (baseTextDirection != 516)
                    if (baseTextDirection != 520)
                        if (baseTextDirection != 513)
                            throw new IllegalArgumentException();
        if (baseTextDirection != this.baseTextDirection) {
            int previousResolvedDirection = getResolvedBaseTextDirection();
            this.baseTextDirection = baseTextDirection;
            if (getResolvedBaseTextDirection() != previousResolvedDirection)
                propagateBaseTextDirectionChange();
        }
    }
    
    /// Updates the component bounds and refreshes stored floating offsets when the location
    /// changes.
    @Override
    public void setBounds(int x, int y, int width, int height) {
        int previousX = super.getX();
        int previousY = super.getY();
        super.setBounds(x, y, width, height);
        block: {
            if (previousX == x)
                if (previousY == y)
                    break block;
            captureFloatingOffsets();
        } // end block
        
    }
    
    /// Updates Swing component orientation and refreshes bidi-sensitive legend text when needed.
    @Override
    public void setComponentOrientation(ComponentOrientation orientation) {
        ComponentOrientation previousOrientation = super.getComponentOrientation();
        super.setComponentOrientation(orientation);
        if (orientation != previousOrientation)
            componentOrientationChanged(previousOrientation, orientation);
    }
    
    /// Stores a temporary dynamic-size override.
    ///
    /// The override is not reentrant: installing a second non-`null` size before clearing the
    /// first one fails.
    public final void setDynamicSize(Dimension size) {
        if (size != null)
            if (dynamicSize != null)
                throw new UnsupportedOperationException("setDynamicSize is not reentrant");
        dynamicSize = size;
    }
    
    /// Marks this legend as floating or docked.
    ///
    /// [Chart#addLegend(Legend, String, boolean)] normally manages this flag for chart-owned
    /// legends.
    public final void setFloating(boolean floating) {
        stateFlags.setFlag(2, floating);
    }
    
    /// Changes how floating legends lay out their rows when the default [LegendLayout] is in use.
    public void setFloatingLayoutDirection(int direction) {
        block: if (direction != getFloatingLayoutDirection())
            if (super.getLayout() instanceof LegendLayout) {
                ((LegendLayout) super.getLayout()).setFloatingLayoutDirection(direction);
                if (chart == null) {
                    if (Chart.getNoEventThreadUpdate())
                        break block;
                } else if (!chart.isUsingEventThread())
                    break block;
                revalidate();
            }
    
    }
    
    /// Enables or disables automatic relocation of floating legends after chart resize.
    ///
    /// When enabled, the legend preserves the previously captured right and bottom offsets instead
    /// of keeping a fixed absolute location.
    public void setFollowChartResize(boolean followChartResize) {
        if (stateFlags.getFlag(64) == followChartResize)
            return;
        stateFlags.setFlag(64, followChartResize);
        if (getChart() != null)
            if (!followChartResize)
                getChart().removeComponentListener(chartResizeListener);
            else {
                if (chartResizeListener == null)
                    chartResizeListener = new ChartResizeListener();
                getChart().addComponentListener(chartResizeListener);
                captureFloatingOffsets();
            }
    }
    
    /// Sets the font consulted by legend-entry label renderers that inherit from this component.
    @Override
    public void setFont(Font font) {
        super.setFont(font);
    }
    
    /// Updates the legend foreground and applies it immediately to current child components.
    @Override
    public void setForeground(Color foreground) {
        super.setForeground(foreground);
        for (int index = 0, componentCount = super.getComponentCount(); index < componentCount; index++)
            super.getComponent(index).setForeground(foreground);
    }
    
    /// Changes the horizontal orientation used by the default [LegendLayout].
    public void setHorizontalOrientation(int orientation) {
        block: if (orientation != getHorizontalOrientation())
            if (super.getLayout() instanceof LegendLayout) {
                ((LegendLayout) super.getLayout()).setHorizontalOrientation(orientation);
                if (chart == null) {
                    if (Chart.getNoEventThreadUpdate())
                        break block;
                } else if (!chart.isUsingEventThread())
                    break block;
                revalidate();
            }
    
    }
    
    /// Enables or disables user-facing legend events.
    ///
    /// When disabled, item clicks and docking notifications are suppressed even if listeners are
    /// registered.
    public final void setInteractive(boolean interactive) {
        stateFlags.setFlag(4, interactive);
    }
    
    /// Moves the legend to a new location.
    ///
    /// Floating legends and overlay containers use this directly; docked legends are usually
    /// positioned by their parent layout manager.
    @Override
    public void setLocation(Point location) {
        super.setLocation(location);
    }
    
    /// Changes the marker box size reserved for each legend row.
    public void setMarkerSize(Dimension markerSize) {
        Dimension previousMarkerSize = this.markerSize;
        this.markerSize = markerSize;
        block: {
            if (markerSize == null) {
                if (markerSize == previousMarkerSize)
                    break block;
            } else if (markerSize.equals(previousMarkerSize))
                break block;
            if (chart == null) {
                if (Chart.getNoEventThreadUpdate())
                    break block;
            } else if (!chart.isUsingEventThread())
                break block;
            revalidate();
        } // end block
        
    }
    
    /// Enables or disables the built-in drag interactor that repositions the legend.
    public void setMovable(boolean movable) {
        if (stateFlags.getFlag(8) == movable)
            return;
        stateFlags.setFlag(8, movable);
        setMoveInteractionEnabled(movable);
    }
    
    /// Enables or disables explicit background filling in [#paintComponent(Graphics)].
    public final void setPaintingBackground(boolean paintingBackground) {
        stateFlags.setFlag(1, paintingBackground);
    }
    
    /// Changes the gap between each legend marker and its label text.
    public void setSymbolTextSpacing(int symbolTextSpacing) {
        block: if (symbolTextSpacing != cellSpacing) {
            cellSpacing = symbolTextSpacing;
            if (chart == null) {
                if (Chart.getNoEventThreadUpdate())
                    break block;
            } else if (!chart.isUsingEventThread())
                break block;
            revalidate();
        } // end block
    
    }
    
    /// Sets the explicit legend title.
    ///
    /// Passing `null` restores the default behavior of deriving the title from attached renderers.
    public void setTitle(String title) {
        block: {
        if (title == null) {
            if (title == this.title)
                break block;
        } else if (title.equals(this.title))
            break block;
        this.title = title;
        refreshTitle();
    } // end block
    
    }
    
    /// Sets the alpha component used for floating legend backgrounds derived from
    /// [#getBackground()].
    public void setTransparency(int transparency) {
        if (transparency != this.transparency) {
            this.transparency = transparency;
            refreshCachedBackground();
        }
    }
    
    /// Changes the vertical orientation used by the default [LegendLayout].
    public void setVerticalOrientation(int orientation) {
        block: if (orientation != getVerticalOrientation())
            if (super.getLayout() instanceof LegendLayout) {
                ((LegendLayout) super.getLayout()).setVerticalOrientation(orientation);
                if (chart == null) {
                    if (Chart.getNoEventThreadUpdate())
                        break block;
                } else if (!chart.isUsingEventThread())
                    break block;
                revalidate();
            }
    
    }
    
    /// Renders this legend into `image`, creating or resizing a target image when needed.
    ///
    /// The export path uses the current layout and child components just as normal on-screen
    /// painting does.
    public BufferedImage toImage(BufferedImage image, boolean clearBackground) {
        return Chart.paintToImage(this, new Legend.LegendPaintAction(null), image, clearBackground);
    }
    
    /// Renders this legend into an off-screen image of the requested size.
    ///
    /// When `g` is `null`, the method creates a temporary graphics context from the returned image.
    /// The optional `background` overrides the color that would otherwise be resolved through
    /// [#computeBackgroundColor()] during the export pass.
    public BufferedImage toImage(Graphics2D g, int width, int height, Color background) {
        Graphics2D graphics = g;
        Object lock = getLock();
        synchronized (lock) {
            installPaintContext(new Legend.LegendImagePaintContext(width, height));
            BufferedImage image;
            try {
                image = ((Legend.LegendImagePaintContext) paintContext).image;
                if (graphics == null)
                    graphics = image.createGraphics();
                paintLegend(graphics, background);
            } finally {
                clearPaintContext();
            }
            return image;
        }
    }
    
    @Override
    public void updateUI() {
        super.updateUI();
    }
}
