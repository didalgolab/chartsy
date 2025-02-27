/*
 * Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.ui.chart.components;

import java.awt.AWTEvent;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.text.DecimalFormat;
import java.util.*;

import javax.swing.Action;
import javax.swing.JPanel;
import javax.swing.ToolTipManager;

import one.chartsy.*;
import one.chartsy.core.commons.AbstractHandleableCloseable;
import one.chartsy.core.commons.HandleableCloseable;
import one.chartsy.core.Range;
import one.chartsy.data.CandleSeries;
import one.chartsy.ui.chart.*;
import one.chartsy.ui.chart.action.ChartActions;
import one.chartsy.ui.chart.annotation.ChartAnnotator;
import one.chartsy.ui.chart.annotation.GraphicLayer;
import one.chartsy.ui.chart.annotation.GraphicModel;
import one.chartsy.ui.chart.annotation.GraphicModelListener;
import one.chartsy.ui.chart.data.VisibleCandles;
import one.chartsy.ui.chart.graphic.GraphicInteractor;
import one.chartsy.ui.chart.hover.HoverEvent;
import one.chartsy.ui.chart.hover.QuoteHoverListener;
import one.chartsy.ui.chart.internal.Graphics2DHelper;
import org.openide.util.Lookup;

public class AnnotationPanel extends JPanel implements OrganizedViewInteractorContext, GraphicModelListener, MouseListener, MouseMotionListener, KeyListener {
    
    private final ChartContext chartFrame;
    //	private final List<Annotation> annotations = new ArrayList<>();
    /** Holds annotation graphics associated with the currently opened chart. */
    private final GraphicModel model = new GraphicModel();
    
    private GraphicLayer interactiveGraphicLayer;
    //	private final OrganizedModel model = new OrganizedModel();
    private final ToolTipManager toolTipManager;
    
    
    public AnnotationPanel(ChartContext frame) {
        super(null);
        chartFrame = frame;
        toolTipManager = ToolTipManager.sharedInstance();
        toolTipManager.setLightWeightPopupEnabled(true);
        toolTipManager.registerComponent(this);
        
        setOpaque(false);
        
        addMouseListener(this);
        addMouseMotionListener(this);
        addKeyListener(this);
        
        chartFrame.addChartFrameListener(new ChartFrameListener() {
            @Override
            public void symbolChanged(SymbolIdentity newSymbol) {
                changeGraphicLayer(loadPersistentGraphicLayer());
            }
        });
    }
    
    /**
     * Loads the annotation graphics stored in the system repository for the
     * currently displayed chart symbol. The collected graphics are returned in the
     * single {@code GraphicLayer} object. If the repository does not contain any
     * graphics yet, an empty persistent graphic layer is created and returned.
     * 
     * @return the persisted graphic layer for the current symbol
     */
    protected final GraphicLayer loadPersistentGraphicLayer() {
        SymbolIdentity symbol = chartFrame.getChartData().getSymbol();
        
        String namespace = null;
        // TODO
        //if (symbol.getProvider() != null)
        //    namespace = symbol.getProvider().getNamespace();
        
        AnnotationRepository repo = Lookup.getDefault().lookup(AnnotationRepository.class);
        if (repo == null)
            return new GraphicLayer();
        else
            return repo.getGraphicModel(namespace, symbol, getGraphicModelUUID());
    }
    
    /**
     * Determines the UUID of the parent panel to which this annotation panel
     * is attached.
     * 
     * @return the parent panel identifier
     */
    private UUID getGraphicModelUUID() {
        UUID uuid;
        if (getParent() instanceof IndicatorPanel)
            uuid = ((IndicatorPanel) getParent()).getId();
        else
            uuid = ChartPanel.UUID;
        return uuid;
    }
    
    /**
     * Sets the given persistent graphic layer for this annotation panel and registers the
     * panel as a view in the model.
     * 
     * @param layer
     *            the new graphic layer to set
     * @throws IllegalArgumentException
     *             if the {@code model} is {@code null}
     */
    protected void changeGraphicLayer(GraphicLayer layer) {
        deselectAll();
        model.removeAllLayers();
        graphicLayers.clear();
        model.addLayer(interactiveGraphicLayer = layer);
    }
    
    @Override
    public void removeNotify() {
        super.removeNotify();
        deselectAll();
        model.removeGraphicModelListener(this);
    }
    
    @Override
    public void addNotify() {
        super.addNotify();
        
        // try to remove first, to prevent adding a duplicate
        model.removeGraphicModelListener(this);
        model.addGraphicModelListener(this);
        changeGraphicLayer(loadPersistentGraphicLayer());
    }
    
    /**
     * Returns the graphic model used to manage all annotation graphics created at
     * this annotation panel.
     * 
     * @return the graphic model
     */
    public final GraphicModel getModel() {
        return model;
    }
    
    /**
     * Returns the graphic layer used to manage the annotation graphics created
     * interactively (manually) by the user. The interactive graphic layer is always
     * persistent and stores created graphics in the special repository on the hard
     * drive.
     * 
     * @return the graphic layer which manages all persistent, interactively created
     *         annotation graphics
     */
    public final GraphicLayer getInteractiveGraphicLayer() {
        return interactiveGraphicLayer;
    }
    
    private final Map<HandleableCloseable<?>, GraphicLayer> graphicLayers = new IdentityHashMap<>(1);
    
    private static class GraphicLayerOwner extends AbstractHandleableCloseable<GraphicLayerOwner> {
        private static final GraphicLayerOwner DEFAULT = new GraphicLayerOwner();
    }
    
    public GraphicLayer getGraphicLayer() {
        return getGraphicLayer(GraphicLayerOwner.DEFAULT);
    }
    
    public GraphicLayer getGraphicLayer(ChartPlugin<?> owner) {
        return getGraphicLayer((HandleableCloseable<?>) owner);
    }
    
    public GraphicLayer getGraphicLayer(HandleableCloseable<?> owner) {
        synchronized (graphicLayers) {
            GraphicLayer layer = graphicLayers.get(owner);
            if (layer == null) {
                GraphicLayer newLayer = layer = new GraphicLayer();
                owner.addCloseHandler(__ -> newLayer.removeAll());
                model.addLayer(newLayer);
                graphicLayers.put(owner, newLayer);
            }
            return layer;
        }
    }
    
    public void removeGraphicLayer(GraphicLayer layer) {
        synchronized (graphicLayers) {
            int index = layer.getIndex();
            if (index >= 0 && layer.getModel() == model)
                model.removeLayer(index);

            graphicLayers.entrySet().removeIf(e -> e.getValue() == layer);
        }
    }
    
    public ChartContext getChartFrame() {
        return chartFrame;
    }
    
    @Override
    public void paint(Graphics g) {
        Graphics2D g2 = Graphics2DHelper.prepareGraphics2D(g);
        // paint all graphic objects
        if (model != null)
            model.paint(g2, this, getWidth(), getHeight());
        
        // paint all selections 
        if (!selections.isEmpty())
            for (Selection selection : selections.values())
                selection.paint(g2, coordinateSystem, getWidth(), getHeight());
        
        // Paint children
        super.paint(g2);
    }
    
    public Range getRange() {
        Container parent = getParent();
        if (parent instanceof ChartPanel) {
            return ((ChartPanel) parent).getRange();
        } else {
            return ((IndicatorPanel) parent).getIndicator()
                    .getRange(chartFrame).range();
        }
    }
    
    public void setAnnotations(List<Annotation> list) {
        for (Annotation annotation : list)
            addAnnotation(annotation);
        
        repaint();
    }
    
    /**
     * Adds the specified annotation graphic to the interactive layer.
     * 
     * @param graphic the annotation graphic to add
     */
    public void addAnnotation(Annotation graphic) {
        interactiveGraphicLayer.addAnnotation(graphic);
    }
    
    public void removeAllAnnotations() {
        try {
            deselectAll();
            model.removeAll();
            validate();
            repaint();
        } catch (Exception ex) {
            chartFrame.log().fatal(ex);
        }
    }
    
    /**
     * Removes the specified annotation graphic from this drawing layer.
     * <p>
     * By default the method calls
     * {@link GraphicLayer#removeAnnotation(Annotation)}. Removing from selection
     * and repainting is handled in via a listener callback from the model.
     * 
     * @param annotation
     *            the annotation graphic to remove
     */
    public void removeAnnotation(Annotation annotation) {
        model.removeAnnotation(annotation);
    }
    
    @Override
    public void mouseClicked(MouseEvent e) {
        if (e.getButton() == MouseEvent.BUTTON1 && e.getClickCount() == 2) {
            Annotation graphic = model.getGraphic(e.getPoint(), coordinateSystem);
            if (graphic != null) {
                Action action = ChartActions.annotationProperties(chartFrame, graphic);
                action.actionPerformed(new ActionEvent(e.getSource(), ActionEvent.ACTION_PERFORMED, graphic.getName()));
            }
        }
    }
    
    @Override
    public void mousePressed(MouseEvent e) {
        requestFocusInWindow();
        
        if (e.isConsumed())
            return;
        
        if (e.getButton() == MouseEvent.BUTTON1 && e.getClickCount() == 1) {
            if (getCursor().getType() == Cursor.N_RESIZE_CURSOR)
                getParent().requestFocusInWindow();
            
            if (!ChartAnnotator.getGlobal().hasDrawing()) {
                //				chartFrame.deselectAll();
                Annotation graphic = model.getGraphic(e.getPoint(), coordinateSystem);
                if (graphic != null) {
                    //					setSelected(graphic, true);
                    if (!getCursor().equals(
                            Cursor.getPredefinedCursor(Cursor.N_RESIZE_CURSOR))) {
                        if (chartFrame.getChartProperties().getMarkerVisibility()) {
                            Rectangle rect = getBounds();
                            rect.translate(2, 0);
                            
                            int index = chartFrame.getChartData().getIndex(e.getX(), e.getY(), rect);
                            if (index != -1) {
                                chartFrame.getMainPanel().getStackPanel().setMarkerIndex(index);
                                chartFrame.getMainPanel().getStackPanel().labelText();
                                chartFrame.getMainPanel().getStackPanel().repaint();
                            }
                        } else {
                            chartFrame.getMainPanel().getStackPanel().setMarkerIndex(-1);
                        }
                    }
                }
            }
        }
        if (e.getButton() == MouseEvent.BUTTON3)
            chartFrame.getMenu().show(this, e.getX(), e.getY());
    }
    
    @Override
    public void mouseReleased(MouseEvent e) {
    }
    
    @Override
    public void mouseEntered(MouseEvent e) {
        setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
        // tooltipHandler(e);
    }
    
    @Override
    public void mouseExited(MouseEvent e) {
        QuoteHoverListener.Broadcaster.mouseExited(new HoverEvent(chartFrame, null));
    }
    
    private final OrganizedView view = new OrganizedView() {
        
        @Override
        public CoordinateSystem getCoordinateSystem() {
            return coordinateSystem;
        }
    };
    
    private MouseEvent startEvent;
    
    @Override
    protected void processMouseEvent(MouseEvent e) {
        super.processMouseEvent(e);
        
        switch (e.getID()) {
        case MouseEvent.MOUSE_PRESSED:
            startEvent = e;
            interactiveGraphicLayer.startBulkUpdateContext(this);
            if (e.isConsumed())
                return;
            
            if (e.getButton() == MouseEvent.BUTTON1 && e.getClickCount() == 1) {
                if (getCursor().getType() == Cursor.N_RESIZE_CURSOR)
                    getParent().requestFocusInWindow();
                
                if (ChartAnnotator.getGlobal().hasDrawing()) {
                    chartFrame.getMainPanel().deselectAll();
                    Annotation ann = ChartAnnotator.getGlobal().getCurrentDrawing(this);
                    // add newly created annotation to the model
                    addAnnotation(ann);
                    // and make it immediately selected
                    setSelected(ann, true);
                    
                    view.pushInteractor(ann.getDrawingInteractor(this));
                    //setSelectedItem(a);
                    OrganizedViewInteractor interactor = view.getInteractor();
                    if (interactor != null)
                        interactor.processMouseEvent(e);
                } else {
                    // Find a graphic under the mouse pointer
                    Annotation graphic = null;
                    Selection selection = getSelection(e.getPoint(), coordinateSystem);
                    if (selection != null)
                        graphic = selection.getGraphic();
                    else
                        graphic = model.getGraphic(e.getPoint(), coordinateSystem);
                    
                    // Deselect previous selections if not in multiselect mode
                    boolean multiselect = e.isShiftDown();
                    if (!multiselect)
                        deselectAll();
                    
                    // Select the graphic or toggle selection if in multiselect mode
                    if (graphic != null)
                        setSelected(graphic, !multiselect || !isSelected(graphic));
                    
                    // Dispatch the event to the current selection
                    dispatchEventToSelection(e);
                }
            }
            break;
        case MouseEvent.MOUSE_RELEASED:
            // Forward AWT event to the interactor
            OrganizedViewInteractor interactor = view.getInteractor();
            if (interactor != null)
                interactor.processMouseEvent(e);
            
            // Dispatch the event to the current selection
            if (!e.isConsumed())
                dispatchEventToSelection(e);
            
            // push model changes to the store
            interactiveGraphicLayer.endBulkUpdateContext();
            break;
        case MouseEvent.MOUSE_CLICKED:
            break;
        case MouseEvent.MOUSE_EXITED:
            break;
        case MouseEvent.MOUSE_ENTERED:
            break;
        }
    }
    
    //	private List<Pair<Annotation, Rectangle2D>> selectedBounds;
    //
    //	private final Rectangle2D tmpRect = new Rectangle2D.Double();
    
    private final SelectionMoveInteractor moveInteractor = new SelectionMoveInteractor(this);
    
    @Override
    protected void processMouseMotionEvent(MouseEvent e) {
        super.processMouseMotionEvent(e);
        
        OrganizedViewInteractor interactor;
        switch(e.getID()) {
        case MouseEvent.MOUSE_MOVED:
            interactor = view.getInteractor();
            if (interactor != null)
                interactor.processMouseMotionEvent(e);
            break;
        case MouseEvent.MOUSE_DRAGGED:
            interactor = view.getInteractor();
            if (interactor != null)
                interactor.processMouseMotionEvent(e);
            else if (getSelectionCount() > 0 && !dispatchEventToSelection(e)) {
                view.pushInteractor(moveInteractor, startEvent);
                view.getInteractor().processMouseMotionEvent(e);
            }
            break;
        }
    }
    
    /**
     * Dispatches the event to the selection object.
     * <p>
     * The event is forwarded only if there is exactly one currently selected
     * graphic. Otherwise the method returns {@code false} without dispatching
     * the event.
     * 
     * @param e
     *            the event to be processed
     * @return {@code true} if the event was successfully handled by the
     *         selection interactor, {@code false} otherwise
     */
    public boolean dispatchEventToSelection(AWTEvent e) {
        if (selections.size() == 1) {
            Selection selection = selections.values().iterator().next();
            GraphicInteractor interactor = selection.getGraphicInteractor();
            if (interactor != null && interactor.processEvent(e, selection, this))
                return true;
        }
        return false;
    }
    
    @Override
    public void paintImmediately(Rectangle2D rect) {
        Rectangle bounds = getBounds();
        if (!bounds.contains(rect))
            rect = rect.createIntersection(bounds);
        
        super.paintImmediately(rect.getBounds());
    }
    
    @Override
    public void mouseDragged(MouseEvent e) {
        if (e.isConsumed())
            return;
        if ((e.getModifiers() & MouseEvent.BUTTON1_MASK) != 0) {
            if (getSelectionCount() > 0) {
                //				Annotation current = getSelectedItem();
                //				if (current != null) {
                //					int x = e.getX(), y = e.getY();
                //					//if (annotationPanel.getBounds().contains(x, y)) {
                //					Rectangle rect = current.getRepaintArea();
                //					if (current.updatePosition(x, y))
                //						paintImmediately(rect.union(current.getRepaintArea()));
                //					//}
                //				}
            } else {
                if (chartFrame.getChartProperties().getMarkerVisibility()) {
                    Rectangle rect = getBounds();
                    rect.translate(2, 0);
                    
                    int index = chartFrame.getChartData().getIndex(e.getX(), e.getY(), rect);
                    if (index != -1) {
                        chartFrame.getMainPanel().getStackPanel().setMarkerIndex(index);
                        chartFrame.getMainPanel().getStackPanel().labelText();
                        chartFrame.getMainPanel().getStackPanel().repaint();
                    }
                } else {
                    chartFrame.getMainPanel().getStackPanel().setMarkerIndex(-1);
                }
            }
        }
    }
    
    @Override
    public void mouseMoved(MouseEvent e) {
    }
    
    /**
     * Translates the annotation {@code graphic} on the screen by the specified
     * distance.
     * <p>
     * The given {@code dx} and {@code dy} distance is interpreted in pixels.
     * With positive {@code dx} values the {@code graphic} is translated to the
     * right and with positive {@code dy} values it's translated down.
     * 
     * @param graphic
     *            the annotation graphic
     * @param dx
     *            the horizontal translation distance
     * @param dy
     *            the vertical translation distance
     */
    protected void translateAnnotation(Annotation graphic, double dx, double dy) {
        getModel().applyManipulator(graphic, null, (a, ap) -> {
            a.applyTransform(p -> new Point2D.Double(p.getX() + dx, p.getY() + dy), coordinateSystem);
        });
    }
    
    @Override
    public void keyTyped(KeyEvent e) {
    }
    
    @Override
    public void keyPressed(KeyEvent e) {
        if (e.isConsumed())
            return;
        
        requestFocusInWindow();
        switch (e.getKeyCode()) {
        case KeyEvent.VK_MINUS:
            chartFrame.zoomOut();
            break;
        case KeyEvent.VK_SUBTRACT:
            chartFrame.zoomOut();
            break;
        case KeyEvent.VK_ADD:
            chartFrame.zoomIn();
            break;
        }
        switch (e.getModifiers()) {
        case KeyEvent.SHIFT_MASK:
            if (e.getKeyCode() == KeyEvent.VK_EQUALS)
                chartFrame.zoomIn();
            break;
        }
        
        if (getSelectionCount() > 0) {
            switch (e.getKeyCode()) {
            case KeyEvent.VK_DELETE:
                for (Annotation ann : getSelectedGraphics())
                    removeAnnotation(ann);
                break;
            case KeyEvent.VK_UP:
                for (Annotation ann : getSelectedGraphics())
                    translateAnnotation(ann, 0, -10);
                break;
            case KeyEvent.VK_DOWN:
                for (Annotation ann : getSelectedGraphics())
                    translateAnnotation(ann, 0, 10);
                break;
            case KeyEvent.VK_LEFT:
                for (Annotation ann : getSelectedGraphics())
                    translateAnnotation(ann, -10, 0);
                break;
            case KeyEvent.VK_RIGHT:
                for (Annotation ann : getSelectedGraphics())
                    translateAnnotation(ann, 10, 0);
                break;
            }
            e.consume();
            repaint();
        } else {
            if (chartFrame.getChartProperties().getMarkerVisibility()) {
                switch (e.getKeyCode()) {
                case KeyEvent.VK_LEFT:
                    chartFrame.getMainPanel().getStackPanel().moveLeft();
                    e.consume();
                    break;
                case KeyEvent.VK_RIGHT:
                    chartFrame.getMainPanel().getStackPanel().moveRight();
                    e.consume();
                    break;
                }
            }
        }
    }
    
    @Override
    public void keyReleased(KeyEvent e) {
    }
    
    public void tooltipHandler(MouseEvent e) {
        ChartData chartData = chartFrame.getChartData();
        VisibleCandles dataset = chartData.getVisible();
        DecimalFormat decimalFormat = new DecimalFormat("#,##0.00");
        String newLine = "<br>";
        
        Rectangle rect = getBounds();
        rect.translate(2, 0);
        int index = chartData.getIndex(e.getX(), e.getY(), rect);
        if (index != -1) {
            StringBuilder builder = new StringBuilder();
            Container parent = getParent();
            if (parent instanceof ChartPanel) {
                ChartPanel chartPanel = (ChartPanel) parent;
                Candle q0 = dataset.getQuoteAt(index);
                long epochMicros = q0.getTime();
                double open = q0.open();
                double high = q0.high();
                double low = q0.low();
                double close = q0.close();
                double volume = q0.volume();
                
                builder.append("<html>");
                builder.append("Date: ")
                .append(TimeFrameHelper.formatDate(
                        chartData.getTimeFrame(), epochMicros)).append(newLine)
                .append(newLine);
                builder.append("Open: ").append(decimalFormat.format(open))
                .append(newLine);
                builder.append("High: ").append(decimalFormat.format(high))
                .append(newLine);
                builder.append("Low: ").append(decimalFormat.format(low))
                .append(newLine);
                builder.append("Close: ").append(decimalFormat.format(close))
                .append(newLine);
                builder.append("Volume: ").append(decimalFormat.format(volume))
                .append(newLine);
                
                if (chartPanel.hasOverlays()) {
                    for (Overlay overlay : chartPanel.getOverlays()) {
                        LinkedHashMap map = overlay.getHTML(chartFrame, index);
                        Iterator it = map.keySet().iterator();
                        while (it.hasNext()) {
                            String key = it.next().toString();
                            String value = map.get(key).toString();
                            if (value.equals(" ")) {
                                builder.append(newLine);
                                builder.append(key).append(newLine);
                            } else {
                                builder.append(key).append(" ").append(value)
                                .append(newLine);
                            }
                        }
                    }
                }
                
                builder.append("</html>");
            } else {
                IndicatorPanel indicatorPanel = (IndicatorPanel) parent;
                Indicator indicator = indicatorPanel.getIndicator();
                long epochMicros = dataset.getQuoteAt(index).getTime();
                
                builder.append("<html>");
                builder.append("Date: ")
                .append(TimeFrameHelper.formatDate(
                        chartData.getTimeFrame(), epochMicros)).append(newLine)
                .append(newLine);
                
                LinkedHashMap map = indicator.getHTML(chartFrame, index);
                Iterator it = map.keySet().iterator();
                while (it.hasNext()) {
                    String key = it.next().toString();
                    String value = map.get(key).toString();
                    if (value.equals(" ")) {
                        builder.append(key).append(newLine);
                    } else {
                        builder.append(key).append(" ").append(value)
                        .append(newLine);
                    }
                }
                
                builder.append("</html>");
            }
            
            setToolTipText(builder.toString());
        }
    }
    
    //	/**
    //	 * Draws a sequence of connected lines, also known as a {@link Polyline}
    //	 * annotation, defined by arrays of {@code x} and {@code y} coordinates.
    //	 * Each pair of (x, y) = <b>{@code (Date, price)}</b> coordinates defines a
    //	 * point on the financial chart. The figure is not closed if the first point
    //	 * differs from the last point.
    //	 * 
    //	 * @param x
    //	 *            an array of datetime coordinates of points in a polyline
    //	 * @param y
    //	 *            an array of price coordinates of points in a polyline
    //	 * @param nPoints
    //	 *            the total number of points
    //	 * @return the created {@link Polyline} object which can be further adjusted
    //	 *         by a caller
    //	 */
    //	public Polyline drawPolyline(long[] x, double[] y, int nPoints) {
    //		Polyline polyline = new Polyline(x, y, nPoints);
    //		polyline.setAnnotationPanel(this);
    //		addAnnotation(polyline);
    //		
    //		return polyline;
    //	}
    
    private final CoordinateSystem coordinateSystem = new ChartCoordinateSystem();
    
    /**
     * Returns the coordinate system bounds to this annotation panel. The
     * coordinate system provides a mapping between logical chart points (given
     * by date/time and value/price) and display points.
     * 
     * @return the coordinate system used to display the object on a screen
     */
    @Override
    public CoordinateSystem getCoordinateSystem() {
        return coordinateSystem;
    }
    
    final class ChartCoordinateSystem implements CoordinateSystem {
        
        @Override
        public Point2D transform(ChartPoint p) {
            return forward(p.getTime(), p.getValue());
        }
        
        @Override
        public ChartPoint inverseTransform(double x, double y) {
            return new ChartPoint(getTimeFromX(x), getViewToDataY(y));
        }
        
        private Point2D forward(long epochMicros, double value) {
            return new Point2D.Double(getDataToViewX(epochMicros), getDataToViewY(value));
        }
        
        @Override
        public Rectangle getBounds() {
            return AnnotationPanel.this.getBounds();
        }
        
        private double getDataToViewY(double v) {
            Rectangle bounds = getParent().getBounds();
            bounds.setLocation(0, 0);
            Insets insets = getParent().getInsets();
            boolean isLog = chartFrame.getChartProperties().getAxisLogarithmicFlag();
            
            return (int) Math.round(chartFrame.getChartData().getY(v, getRange(), bounds, insets, isLog));
        }
        
        private double getViewToDataY(double y) {
            Rectangle bounds = getParent().getBounds();
            bounds.setLocation(0, 0);
            Insets insets = getParent().getInsets();
            boolean isLog = chartFrame.getChartProperties().getAxisLogarithmicFlag();
            
            return chartFrame.getChartData().getReverseY(y, getRange(), bounds, insets, isLog);
        }
        
        @Override
        public int getSlotIndex(long epochMicros) {
            ChartData cd = chartFrame.getChartData();
            if (!cd.hasDataset())
                return 0;
            
            CandleSeries quotes = cd.getDataset();

            // TODO
            //long barTime = TimeFrameHelper.truncateMicros(epochMicros, cd.getTimeFrame());
            long barTime = epochMicros;

            int barNo = quotes.getTimeline().getTimeLocation(barTime);
            int count = quotes.length();
            if (barNo < 0) {
                // locate nearest neighbor bar
                long min = quotes.get(count - 1).getTime();
                long max = quotes.get(0).getTime();
                
                if (epochMicros < min)
                    barNo = count - 1;
                else if (epochMicros > max)
                    barNo = 0;
                else
                    barNo = -barNo - (TimeFrameHelper.isIntraday(cd.getTimeFrame())? 1: 2);
            }
            return barNo;
        }
        
        private double getDataToViewX(long epochMicros) {
            int barNo = getSlotIndex(epochMicros);
            ChartData cd = chartFrame.getChartData();
            Rectangle rect = getBounds();
            rect.translate(2, 0);
            
            int period = cd.getPeriod();
            int last = cd.getLast();
            
            int count = cd.getDatasetLength();
            
            int index = count - last + period - barNo - 1;
            
            return (int)(0.5 + chartFrame.getChartData().getX(index, rect));
        }
        
        private long getTimeFromX(double x) {
            Integer idx = null;
            ChartData cd = chartFrame.getChartData();
            Rectangle rect = getBounds();
            rect.translate(2, 0);
            
            int index = chartFrame.getChartData().getIndex2(
                    (int)x, 20, rect);
            
            Candle q0 = chartFrame.getChartData().getVisible().getQuoteAt2(index);
            return q0.getTime();
        }
    }
    
    /*------------ ORGANIZER methods --------------------------------*/
    
    /** The map of graphic objects currently selected. */
    private final Map<Annotation, Selection> selections = new IdentityHashMap<>();
    
    /**
     * Selects or deselects the specified graphic object.
     * <p>
     * The new selection state is determined by the given {@code selected} parameter.
     * 
     * 
     * @param graphic the graphic object to selected or deselect
     * @param selected the new selection state for the graphic object
     */
    public void setSelected(Annotation graphic, boolean selected) {
        // Check if selection state has changed
        Selection selection = selections.get(graphic);
        if (selected == (selection != null))
            return;
        
        // Change the selection state
        if (selected) {
            selection = graphic.makeSelection();
            if (selection == null) // the graphic might not produce a selection
                return;
            
            selections.put(graphic, selection);
        } else {
            selections.remove(graphic);
        }
        
        // Refresh the view
        repaint(selection);
    }
    
    @Override
    public void repaint(Annotation graphic) {
        if (isSelected(graphic))
            graphic = getSelection(graphic);
        
        repaint(graphic.getBoundingBox(coordinateSystem));
    }
    
    @Override
    public void repaint(Rectangle2D dirtyRegion) {
        repaint(dirtyRegion.getBounds());
    }
    
    /**
     * Deselects all the graphic objects.
     */
    public void deselectAll() {
        // Schedule view update
        int deselectCount = getSelectionCount();
        if (deselectCount == 1)
            repaint(selections.values().iterator().next());
        else if (deselectCount > 1)
            repaint();
        else if (deselectCount == 0)
            return;
        
        // Clear all selections
        selections.clear();
    }
    
    /**
     * Returns a collection of all selected graphic objects.
     * 
     * @return the selected graphic objects
     */
    public Collection<Annotation> getSelectedGraphics() {
        return selections.keySet();
    }
    
    /**
     * Returns a collection of all current selections.
     * 
     * @return the selections
     */
    public Collection<Selection> getSelections() {
        return selections.values();
    }
    
    /**
     * Returns the number of selected graphic objects.
     * 
     * @return the selected graphics count
     */
    public int getSelectionCount() {
        return selections.size();
    }
    
    /**
     * Tests if the specified graphic object is selected.
     * 
     * @param graphic
     *            the graphic object
     * @return {@code true} if the {@code graphic} is selected, and
     *         {@code false} otherwise
     */
    public boolean isSelected(Annotation graphic) {
        return selections.containsKey(graphic);
    }
    
    public Selection getSelection(Annotation graphic) {
        return selections.get(graphic);
    }
    
    /**
     * Searches for the selection graphic located at the specified
     * {@code point}. The method returns {@code null} if no selection graphic
     * could be found at the given {@code point} or the model has no current
     * selections at all. When multiple selections matches the given
     * {@code point} it is undefined which match is returned.
     * 
     * @param point
     *            the point to check
     * @param coords
     *            the coordinate system used to display the graphics
     * @return the selection at the specified point, or {@code null} if no
     *         selection was found
     */
    public Selection getSelection(Point2D point, CoordinateSystem coords) {
        if (!selections.isEmpty())
            for (Selection candidate : selections.values())
                if (candidate.contains(point, coords))
                    return candidate;
        
        return null;
    }
    
    /**
     * Returns the number of annotation graphic objects 
     * 
     * @return the number of annotations
     */
    public int getAnnotationCount() {
        return model.getAnnotationCount();
    }
    
    /**
     * {@inheritDoc}
     * <p>
     * By default the method repaints the area occupied by the {@code graphic}.
     * 
     * @param graphic
     *            the annotation graphic
     */
    @Override
    public void graphicCreated(Annotation graphic) {
        repaint(graphic);
    }
    
    /**
     * {@inheritDoc}
     * <p>
     * By default the method repaints the area occupied by the {@code graphic}
     * and additionally if the {@code graphic} is selected, cancels the
     * selection.
     * 
     * @param graphic
     *            the annotation graphic
     */
    @Override
    public void graphicRemoved(Annotation graphic) {
        if (isSelected(graphic))
            setSelected(graphic, false); // already repaints
        else
            repaint(graphic);
    }
    
    @Override
    public void graphicWillUpdate(Annotation graphic) {
        repaint(graphic);
    }
    
    @Override
    public void graphicUpdated(Annotation graphic, Annotation oldGraphic) {
        repaint(graphic);
        if (oldGraphic != graphic)
            repaint(oldGraphic);
    }
    
    /**
     * {@inheritDoc}
     * <p>
     * By default the method repaints all the area displayed on the screen.
     */
    @Override
    public void graphicContentChanged() {
        repaint();
    }
}
