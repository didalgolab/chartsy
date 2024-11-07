/*
 * Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.ui.chart.components;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JLayeredPane;
import javax.swing.JTable;
import javax.swing.table.AbstractTableModel;

import one.chartsy.*;
import one.chartsy.core.event.ListenerList;
import one.chartsy.data.CandleSeries;
import one.chartsy.ui.chart.*;
import one.chartsy.ui.chart.internal.ColorServices;
import one.chartsy.ui.chart.internal.CoordCalc;
import one.chartsy.ui.chart.internal.Graphics2DHelper;
import org.openide.util.NbBundle;

/**
 * The component representing a stack of chart panels (and possibly indicator panels).
 *
 * @author Mariusz Bernacki
 */
public class ChartStackPanel extends JLayeredPane {
    /** The chart frame to which this panel is associated. */
    private final ChartContext chartFrame;
    
    private final ChartPanel chartPanel;
    private final JDataBox label;
    
    /** The chart marker index, or {@code -1} if the marker is disabled. */
    private int markerIndex = -1;
    /** The marker listeners registry. */
    private ListenerList<MarkerListener> markerListeners;

    private static final Font font = new Font("Dialog", Font.PLAIN, 10);
    private static final int width = 200;
    private static final int height = 14;

    private final Color lineColor = new Color(0xef2929);
    private final Color color = new Color(0x1C2331);
    private final Color backgroundColor = ColorServices.getDefault().getTransparentColor(color, 100);
    private final Color fontColor = new Color(0xffffff);

    private int lines = 5;


    public ChartStackPanel(ChartContext frame) {
        chartFrame = frame;
        chartPanel = new ChartPanel(chartFrame);
        //indicatorsPanel = new IndicatorsPanel(chartFrame);
        label = new JDataBox();
        
        setOpaque(false);
        // setDoubleBuffered(true);
        
        setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
        setLayout(new LayoutHandler());
        
        //add(label);
        add(chartPanel);
        label.setLocation(0, 50);
        Resizeable resizeable = new Resizeable();
        addMouseListener(resizeable);
        addMouseMotionListener(resizeable);
        
        ChartFrameListener frameAdapter = new ChartFrameListener() {
            @Override
            public void indicatorAdded(Indicator indicator) {
                IndicatorPanel indicatorPanel = new IndicatorPanel(chartFrame, indicator);
                add(indicatorPanel);
                updateIndicatorsToolbar();
                chartFrame.getMainPanel().validate();
                chartFrame.getMainPanel().repaint();
            }
            
            @Override
            public void indicatorRemoved(Indicator indicator) {
                IndicatorPanel indicatorPanel = getIndicatorPanel(indicator);
                if (indicatorPanel != null) {
                    indicator.clearPlots();
                    remove(indicatorPanel);
                    updateIndicatorsToolbar();
                    chartFrame.getMainPanel().validate();
                    chartFrame.getMainPanel().repaint();
                }
            }
        };
        chartFrame.addChartFrameListener(frameAdapter);
    }
    
    public ChartPanel getChartPanel() {
        return chartPanel;
    }
    
    /**
     * Adds a new marker listener.
     * 
     * @param listener the listener to add
     */
    public void addMarkerListener(MarkerListener listener) {
        if (markerListeners == null)
            markerListeners = new ListenerList<>(MarkerListener.class);
        markerListeners.addListener(listener);
    }
    
    /**
     * Removes the specified marker listener.
     * 
     * @param listener the listener to remove
     */
    public void removeMarkerListener(MarkerListener listener) {
        if (markerListeners != null)
            markerListeners.removeListener(listener);
    }
    
    /**
     * Sets a new marker position index. If the index is changed, the
     * {@link MarkerListener#onMarker(MarkerEvent)} notification is emitted as a
     * result of this method call.
     * 
     * @param index
     *            the marker index
     */
    public void setMarkerIndex(int index) {
        if (markerIndex != index) {
            markerIndex = index;
            if (markerListeners != null && markerListeners.getListenerCount() > 0)
                markerListeners.fire().onMarker(new MarkerEvent(this, index));
        }
    }
    
    /**
     * Gives the marker position index within this chart. The marker index
     * corresponds to candle index in the underlying market data series.
     * 
     * @return the marker index, or {@code -1} if the marker is disabled
     */
    public int getMarkerIndex() {
        return markerIndex;
    }
    
    /**
     * Checks if the marker is enabled in this chart.
     * 
     * @return {@code true} if the marker is enabled and thus visible, or
     *         {@code false} otherwise
     */
    public boolean isMarkerEnabled() {
        return getMarkerIndex() > 0;
    }
    
    @Override
    public void paint(Graphics g) {
        super.paint(g);
        
        Graphics2D g2 = Graphics2DHelper.prepareGraphics2D(g);
        if (chartFrame.getChartProperties().getMarkerVisibility()) {
            if (isMarkerEnabled()) {
                labelText();
                paintMarkerFigure(g2);
                label.setVisible(true);
            }
        } else {
            label.setVisible(false);
        }
    }
    
    @Override
    protected void paintChildren(Graphics g) {
        // paint all children of this panel
        super.paintChildren(g);
        
        // draw borders between the children
        Graphics2D g2 = (Graphics2D) g;
        Color oldColor = g2.getColor();
        Stroke oldStroke = g2.getStroke();
        
        ChartProperties properties = chartFrame.getChartProperties();
        g2.setColor(properties.getAxisColor());
        g2.setStroke(properties.getAxisStroke());
        for (int i = 0; i < getComponentCount(); i++) {
            Component c = getComponent(i);
            if (i == 0 && c == chartPanel)
                continue;
            g2.drawLine(c.getX(), c.getY(), c.getX() + c.getWidth(), c.getY());
        }
        g2.setStroke(oldStroke);
        g2.setColor(oldColor);
    }
    
    /**
     * Draws the marker line on the chart.
     * 
     * @param g the chart's graphics context
     */
    private void paintMarkerFigure(Graphics2D g) {
        Rectangle bounds = chartPanel.getBounds(chartPanel.getInsets());
        
        int index = getMarkerIndex();
        long epochMicros = chartFrame.getChartData().getVisible().getQuoteAt(index).getTime();
        String s = TimeFrameHelper.formatDate(chartFrame.getChartData().getTimeFrame(), epochMicros);
        double dx = chartFrame.getChartData().getX(index, bounds);
        g.setFont(font);
        
        FontMetrics fm = g.getFontMetrics(font);
        int w = fm.stringWidth(s) + 2;
        int h = fm.getHeight() + 2;
        boolean inv = (getWidth() - dx < w);
        
        // paint line
        g.setPaint(lineColor);
        g.draw(CoordCalc.line(dx, 0, dx, getHeight()));
        // paint background
        g.fill(CoordCalc.rectangle(inv ? dx - w : dx, 0, w, h));
        // paint rectangle and string
        g.draw(CoordCalc.rectangle(inv ? dx - w : dx, 0, w, h));
        g.setPaint(fontColor);
        g.drawString(s, inv ? (float) (dx - w + 1) : (float) (dx + 1), (float) (fm.getAscent() + 1));
    }
    
    private String addLine(String left, String right) {
        if (!right.equals(" "))
            return NbBundle.getMessage(ChartStackPanel.class, "HTML_Line",
                    new String[] { String.valueOf(width / 2), left, right });
        else
            return NbBundle.getMessage(ChartStackPanel.class, "HTML_EmptyLine",
                    new String[] { String.valueOf(width), left });
    }
    
    public void labelText() {
        if (isMarkerEnabled()) {
            ChartData cd = chartFrame.getChartData();
            DecimalFormat df = new DecimalFormat("#,##0.00");
            
            Candle q0 = cd.getVisible().getQuoteAt(getMarkerIndex());
            long epochMicros = q0.getTime();
            String date = TimeFrameHelper.formatDate(chartFrame.getChartData().getTimeFrame(), epochMicros);
            
            StringBuilder sb = new StringBuilder();
            // Date
            sb.append(addLine("Date:", date));
            // Open
            sb.append(addLine("Open:", df.format(q0.open())));
            // High
            sb.append(addLine("High:", df.format(q0.high())));
            // Low
            sb.append(addLine("Low:", df.format(q0.low())));
            // Close
            sb.append(addLine("Close:", df.format(q0.close())));
            
            lines = 5;
            
            boolean hasOverlays = chartPanel.getOverlaysCount() > 0;
            boolean hasIndicators = getIndicatorPanels().size() > 0;
            
            if (hasOverlays || hasIndicators) {
                sb.append(addLine(" ", " "));
                lines++;
            }
            
            if (hasOverlays) {
                for (Overlay overlay : chartPanel.getOverlays()) {
                    LinkedHashMap map = overlay.getHTML(chartFrame, getMarkerIndex());
                    Iterator it = map.keySet().iterator();
                    while (it.hasNext()) {
                        Object key = it.next();
                        sb.append(addLine(key.toString(), map.get(key).toString()));
                        lines++;
                    }
                }
            }
            
            if (hasIndicators) {
                if (hasOverlays) {
                    sb.append(addLine(" ", " "));
                    lines++;
                }
                
                for (Indicator indicator : getIndicators()) {
                    LinkedHashMap map = indicator.getHTML(chartFrame, getMarkerIndex());
                    Iterator it = map.keySet().iterator();
                    while (it.hasNext()) {
                        Object key = it.next();
                        sb.append(addLine(key.toString(), map.get(key).toString()));
                        lines++;
                    }
                }
            }
            
            String labelText = NbBundle.getMessage(ChartStackPanel.class, "HTML_Marker",
                    new String[] { String.valueOf(width), sb.toString() });
            //			if (!label.getText().equals(labelText))
            //				label.setText(labelText);
            
            Dimension dimension = new Dimension(width, height * lines);
            if (!label.getPreferredSize().equals(dimension)) {
                label.setPreferredSize(dimension);
                label.revalidate();
            }
        } else {
            label.setVisible(false);
        }
    }
    
    public Indicator[] getIndicators() {
        List<IndicatorPanel> indicatorPanels = getIndicatorPanels();
        Indicator[] indicators = new Indicator[indicatorPanels.size()];
        for (int i = 0; i < indicatorPanels.size(); i++)
            indicators[i] = indicatorPanels.get(i).getIndicator();
        return indicators;
    }
    
    public void moveLeft() {
        ChartData cd = chartFrame.getChartData();
        int last = cd.getLast();
        int items = cd.getPeriod() - 1;
        int i = getMarkerIndex() - 1;
        if (i < 0) {
            if (last - 1 > items) {
                cd.setLast(last - 1);
            }
        } else {
            setMarkerIndex(i);
        }
        labelText();
        cd.calculate(chartFrame);
        chartFrame.getMainPanel().repaint();
    }
    
    public void moveRight() {
        ChartData cd = chartFrame.getChartData();
        CandleSeries dataset = cd.getDataset();
        if (dataset != null) {
            int all = dataset.length();
            int last = cd.getLast();
            int items = cd.getPeriod() - 1;
            int i = getMarkerIndex() + 1;
            if (i > items) {
                if (last < all) {
                    cd.setLast(last + 1);
                }
            } else {
                setMarkerIndex(i);
            }
            labelText();
            cd.calculate(chartFrame);
            chartFrame.getMainPanel().repaint();
        }
    }
    
    public class LayoutHandler implements LayoutManager {
        private static final int DEFAULT_SUBPANEL_HEIGHT = 168;
        /**
         * The share in the overall component height of a panel from the stack
         * of indicators positioned above the price panel. Must be a numeric
         * value between 0 and 1.
         */
        private double northShare = 0.2;
        /**
         * The share in the overall component height of a panel from the stack
         * of indicators positioned below the price panel. Must be a value
         * between 0 and 1.
         */
        private double southShare = 0.2;
        
        private double chartShare = 1.0;
        /**
         * The index of the price panel among the list of components.
         */
        private int overlayIndex;
        
        @Override
        public void addLayoutComponent(String name, Component comp) {
        }
        
        @Override
        public void removeLayoutComponent(Component comp) {
        }
        
        @Override
        public Dimension preferredLayoutSize(Container parent) {
            return new Dimension(0, 0);
        }
        
        @Override
        public Dimension minimumLayoutSize(Container parent) {
            return new Dimension(0, 0);
        }
        
        @Override
        public void layoutContainer(Container parent) {
            Insets insets = parent.getInsets();
            int x = insets.left;
            int y = insets.top;
            int width = parent.getWidth() - insets.left - insets.right;
            int height = parent.getHeight() - insets.top - insets.bottom;
            
            // Firstly, check some trivial cases regarding the component count
            int componentCount = parent.getComponentCount();
            if (componentCount == 0)
                return;
            if (componentCount == 1) {
                getComponent(0).setBounds(x, y, width, height);
                return;
            }
            
            // First pass of the layout algorithm
            boolean north = true;
            int northMinimizedCount = 0, southMinimizedCount = 0;
            for (int i = 0; i < componentCount; i++) {
                Component c = getComponent(i);
                if (c == chartPanel) {
                    // Component representing chart panel found
                    overlayIndex = i;
                    north = false;
                } else if (c.isMaximumSizeSet()) { // TODO: check custom property
                    // Chart Panel is probably collapsed
                    if (north)
                        northMinimizedCount++;
                    else
                        southMinimizedCount++;
                    height -= c.getMaximumSize().height;
                }
            }
            int northCount = (overlayIndex - northMinimizedCount),
                    southCount = (componentCount - 1 - overlayIndex - southMinimizedCount);
            double totalShare = northShare * northCount + southShare * southCount + chartShare;
            northShare = Math.min(northShare / totalShare, 1.0 / componentCount);
            southShare = Math.min(southShare / totalShare, 1.0 / componentCount);
            chartShare = 1.0 - northShare*northCount - southShare*southCount;
            
            // Second pass of the algorithm to lay out upper stack of indicator panels
            for (int i = 0; i < componentCount; i++) {
                Component c = getComponent(i);
                int y1 = (int) Math.rint(northShare*height*i);
                int y2 = (int) Math.rint(northShare*height*(i + 1));
                
                if (c == chartPanel) {
                    // Component representing chart panel found, go to third pass
                    c.setLocation(x, y);
                    break;
                } else if (c.isMaximumSizeSet()) {
                    // Chart Panel is probably collapsed, set component bounds to its max size
                    Dimension d = c.getMaximumSize();
                    c.setBounds(x, y, width, d.height);
                    y += d.height;
                } else {
                    // Lay out component as usual based on its computed height
                    c.setBounds(x, y, width, y2 - y1);
                    y += y2 - y1;
                }
            }
            
            // Third pass of the layout algorithm, over the south stack of indicator components
            y = parent.getHeight() - insets.top - insets.bottom - 1;
            for (int i = componentCount - 1; i >= 0; i--) {
                Component c = getComponent(i);
                int y1 = height - (int)Math.rint(southShare*height*(i + 1));
                int y2 = height - (int)Math.rint(southShare*height*i);
                
                if (c == chartPanel) {
                    // Component representing chart panel found
                    c.setSize(width, y - c.getY());
                    break;
                } else if (c.isMaximumSizeSet()) {
                    // Chart Panel is probably collapsed, set component bounds to its max size
                    Dimension d = c.getMaximumSize();
                    c.setBounds(x, y -= d.height, width, d.height);
                } else {
                    // Lay out component as usual based on its computed height
                    c.setBounds(x, y -= y2 - y1, width, y2 - y1);
                }
            }
            
            
            //				Point dp = new Point(0, 50);
            //				Point p = label.getLocation();
            //				if (!dp.equals(p))
            //					label.setBounds(p.x, p.y, width + 2, height * lines + 2);
            //				else
            //					label.setBounds(dp.x, dp.y, width + 2, height * lines + 2);
        }
    }
    
    public static class Draggable extends MouseAdapter implements MouseMotionListener {
        Point lastP;
        Component cDraggable;
        
        public Draggable(Component comp) {
            comp.setLocation(0, 0);
            cDraggable = comp;
        }
        
        private void setCursorType(Point p) {
            Point loc = cDraggable.getLocation();
            Dimension size = cDraggable.getSize();
            if ((p.y + 4 < loc.y + size.height) && (p.x + 4 < p.x + size.width)) {
                cDraggable.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
            }
        }
        
        @Override
        public void mousePressed(MouseEvent e) {
            if (cDraggable.getCursor().equals(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR))) {
                lastP = e.getPoint();
            } else {
                lastP = null;
            }
        }
        
        @Override
        public void mouseReleased(MouseEvent e) {
            lastP = null;
        }
        
        @Override
        public void mouseMoved(MouseEvent e) {
            setCursorType(e.getPoint());
        }
        
        @Override
        public void mouseDragged(MouseEvent e) {
            int x, y;
            if (lastP != null) {
                x = cDraggable.getX() + (e.getX() - (int) lastP.getX());
                y = cDraggable.getY() + (e.getY() - (int) lastP.getY());
                cDraggable.setLocation(x, y);
            }
        }
        
    }
    
    /**
     * The table model used by the data box component.
     * 
     * @author Mariusz Bernacki
     *
     */
    static class DataBoxModel extends AbstractTableModel {
        
        @Override
        public int getRowCount() {
            return 0;
        }
        
        @Override
        public int getColumnCount() {
            // TODO Auto-generated method stub
            return 0;
        }
        
        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            // TODO Auto-generated method stub
            return null;
        }
        
        
    }
    
    /**
     * Represents the data box table attached to the chart frame.
     * 
     * @author Mariusz Bernacki
     *
     */
    private class JDataBox extends JTable {
        
        
        private JDataBox() {
            setOpaque(false);
            setFont(font);
            setForeground(fontColor);
            setVisible(isMarkerEnabled());
            setPreferredSize(new Dimension(width, height));
            Draggable draggable = new Draggable(this);
            addMouseListener(draggable);
            addMouseMotionListener(draggable);
        }
        
        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION,
                    RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
            
            g2.setColor(backgroundColor);
            g2.fillRect(0, 0, width + 2, height * lines + 2);
            g2.setColor(color);
            g2.drawRect(0, 0, width + 1, height * lines + 1);
            
            super.paintComponent(g);
        }
        
    }
    
    class Resizeable extends MouseAdapter implements MouseMotionListener {
        
        int fix_pt_x = -1;
        int fix_pt_y = -1;
        Cursor oldCursor;
        boolean resizable;
        
        public Resizeable() {
        }
        
        private void setCursorType(Point p) {
            int y = chartPanel.getY() + chartPanel.getHeight();
            resizable = Math.abs(p.getY() - y) <= 4;
            
            if (resizable) {
                setCursor(Cursor.getPredefinedCursor(Cursor.N_RESIZE_CURSOR));
            } else {
                setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
            }
        }
        
        public boolean allMinimized() {
            for (IndicatorPanel ip : getIndicatorPanels())
                if (!ip.isMinimized())
                    return true;
            return false;
        }
        
        @Override
        public void mouseEntered(MouseEvent e) {
            requestFocusInWindow();
            if (allMinimized())
                setCursorType(e.getPoint());
        }
        
        @Override
        public void mouseExited(MouseEvent e) {
            if (oldCursor != null)
                ((Component) e.getSource()).setCursor(oldCursor);
            oldCursor = null;
        }
        
        @Override
        public void mousePressed(MouseEvent e) {
            requestFocusInWindow();
            Cursor c = getCursor();
            if (c.equals(Cursor.getPredefinedCursor(Cursor.N_RESIZE_CURSOR))) {
                fix_pt_y = e.getY();
            } else {
                fix_pt_y = -1;
            }
        }
        
        @Override
        public void mouseReleased(MouseEvent e) {
            fix_pt_y = -1;
            setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
            doLayout();
            getChartFrame().getMainPanel().getStackPanel().doLayout();
            getChartFrame().getMainPanel().validate();
            getChartFrame().getMainPanel().repaint();
        }
        
        @Override
        public void mouseMoved(MouseEvent e) {
            if (allMinimized())
                setCursorType(e.getPoint());
        }
        
        @Override
        public void mouseDragged(MouseEvent e) {
            if (!resizable)
                return;
            
            LayoutHandler layout = (LayoutHandler) getLayout();
            Container parent = ChartStackPanel.this;
            Insets insets = parent.getInsets();
            int y = parent.getHeight() - insets.top - insets.bottom - 1;
            int height = parent.getHeight() - insets.top - insets.bottom;
            int panelCount = 0; // number of panels affected by layout in a north-stack
            for (int i = getComponentCount() - 1; i >= 0; i--) {
                Component c = getComponent(i);
                int y1 = height - (int)Math.rint(layout.southShare*height*(i + 1));
                int y2 = height - (int)Math.rint(layout.southShare*height*i);
                
                if (c == chartPanel) {
                    // Component representing chart panel found
                    break;
                } else if (c.isMaximumSizeSet()) {
                    // Chart Panel is probably collapsed, set component bounds to its max size
                    y -= c.getMaximumSize().height;
                } else {
                    // Normal component found, count it
                    y -= y2 - y1;
                    panelCount++;
                }
            }
            
            if (panelCount == 0)
                return;
            
            int yDelta = y - e.getY();
            chartPanel.setSize(chartPanel.getWidth(), chartPanel.getHeight() - yDelta);
            layout.southShare += (double)yDelta / panelCount / height;
            validate();
            repaint();
        }
    }
    
    public List<Indicator> getIndicatorsList() {
        Indicator[] inds = getIndicators();
        return new ArrayList<>(Arrays.asList(inds));
    }
    
    public int getIndicatorsCount() {
        // TODO
        return getIndicators().length;
    }
    
    public List<IndicatorPanel> getIndicatorPanels() {
        List<IndicatorPanel> indicatorPanels = new ArrayList<>(getComponentCount());
        for (int i = 0; i < getComponentCount(); i++) {
            Component component = getComponent(i);
            if (component instanceof IndicatorPanel)
                indicatorPanels.add((IndicatorPanel) component);
        }
        return indicatorPanels;
    }
    
    public ChartContext getChartFrame() {
        return chartFrame;
    }
    
    public void moveUp(IndicatorPanel panel) {
        int index = getComponentZOrder(panel);
        int newIndex = Math.max(index - 1, 0);
        
        if (newIndex != index) {
            setComponentZOrder(panel, newIndex);
            revalidate();
            getParent().repaint();
        }
    }
    
    public void moveDown(IndicatorPanel panel) {
        int index = getComponentZOrder(panel);
        int count = getComponentCount();
        int newIndex = Math.min(index + 1, count - 1);
        
        if (newIndex != index) {
            setComponentZOrder(panel, newIndex);
            revalidate();
            getParent().repaint();
        }
    }
    
    public void updateIndicatorsToolbar() {
        getIndicatorPanels().forEach(IndicatorPanel::updateToolbox);
    }
    
    public IndicatorPanel getIndicatorPanel(Indicator indicator) {
        return getIndicatorPanels().stream()
                .filter(panel -> indicator.equals(panel.getIndicator()))
                .findFirst().orElse(null);
    }
}
