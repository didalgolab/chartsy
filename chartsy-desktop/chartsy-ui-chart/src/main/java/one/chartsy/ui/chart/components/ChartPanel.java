/*
 * Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.ui.chart.components;

import one.chartsy.charting.Scale;
import one.chartsy.charting.Legend;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GraphicsConfiguration;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JLayeredPane;
import javax.swing.JPanel;
import javax.swing.JToolBar;
import javax.swing.SwingConstants;
import javax.swing.border.Border;

import one.chartsy.TimeFrameHelper;
import one.chartsy.core.Range;
import one.chartsy.data.CandleSeries;
import one.chartsy.ui.chart.*;
import one.chartsy.ui.chart.action.ChartAction;
import one.chartsy.ui.chart.internal.ColorServices;
import one.chartsy.ui.chart.internal.Graphics2DHelper;
import one.chartsy.ui.chart.internal.engine.EngineChartHost;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import org.openide.util.lookup.Lookups;
import org.openide.util.lookup.ProxyLookup;


public class ChartPanel extends JLayeredPane implements Serializable {

    private static final double SCALE_EPSILON = 0.0001d;
    private static final int PLOT_WIDTH_EPSILON = 1;

    private final ChartContext chartFrame;
    private final EngineChartHost engineHost;
    private final Legend nativeLegend;
    private AnnotationPanel annotationPanel;
    private JLabel stockInfo;
    private JToolBar overlayToolboxes;
    private double lastPaintScaleX = -1.0d;
    private int lastPlotWidth = -1;
    /** The list of overlays displayed in this panel. */
    private final List<Overlay> overlays = new ArrayList<>();
    private boolean overlayToolboxesUpdated;
    public static final java.util.UUID UUID = java.util.UUID.fromString("43229972-5474-11e7-b114-b2f933d5fe66");
    
    public ChartPanel(ChartContext frame, Scale sharedTimeScale) {
        chartFrame = frame;
        engineHost = new EngineChartHost(sharedTimeScale);
        nativeLegend = engineHost.legend();
        initializeUIElements();
    }
    
    private void initializeUIElements() {
        setOpaque(false);
        setDoubleBuffered(true);
        
        annotationPanel = new AnnotationPanel(chartFrame);
        
        overlayToolboxes = new JToolBar(JToolBar.HORIZONTAL);
        overlayToolboxes.setBorder(BorderFactory.createEmptyBorder());
        overlayToolboxes.setOpaque(false);
        overlayToolboxes.setFloatable(false);
        
        stockInfo = new JLabel();
        stockInfo.setOpaque(false);
        stockInfo.setHorizontalAlignment(SwingConstants.LEFT);
        stockInfo.setVerticalAlignment(SwingConstants.TOP);
        Font defaultFont = chartFrame.getChartProperties().getFont();
        Font font = defaultFont.deriveFont(Font.BOLD, defaultFont.getSize2D() * 1.2f);
        stockInfo.setFont(font);
        stockInfo.setForeground(Color.GRAY);
        stockInfo.setVisible(false);
        
        setStockTitleFromChartData(chartFrame.getChartData());
        
        ChartFrameListener frameAdapter = new ChartFrameListener() {

            @Override
            public void datasetChanged(CandleSeries quotes) {
                setStockTitleFromChartData(chartFrame.getChartData());
                refreshEngine(getParent() == null || ((ChartStackPanel) getParent()).getIndicatorPanels().isEmpty());
            }
            
            //			@Override
            //			public void symbolChanged(Symbol newSymbol) {
            //				setStockTitleFromChartData(chartFrame.getChartData());
            //			}
            //
            //			@Override
            //			public void timeFrameChanged(TimeFrame newInterval) {
            //				setStockTitleFromChartData(chartFrame.getChartData());
            //			}
            
            @Override
            public void chartChanged(Chart newChart) {
                refreshEngine(getParent() == null || ((ChartStackPanel) getParent()).getIndicatorPanels().isEmpty());
            }
            
            @Override
            public void overlayAdded(Overlay overlay) {
                addOverlay(overlay);
                chartFrame.getChartData().calculateRange(chartFrame, overlays);
                refreshEngine(getParent() == null || ((ChartStackPanel) getParent()).getIndicatorPanels().isEmpty());
                chartFrame.getMainPanel().revalidate();
                chartFrame.getMainPanel().repaint();
            }
            
            @Override
            public void overlayRemoved(Overlay overlay) {
                removeOverlay(overlay);
                chartFrame.getChartData().calculateRange(chartFrame, overlays);
                refreshEngine(getParent() == null || ((ChartStackPanel) getParent()).getIndicatorPanels().isEmpty());
                chartFrame.getMainPanel().revalidate();
                chartFrame.getMainPanel().repaint();
            }
        };
        chartFrame.addChartFrameListener(frameAdapter);
        
        setLayout(new LayoutManager() {
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
                int width = parent.getWidth();
                int height = parent.getHeight();
                Dimension overlaySize = overlayToolboxes.getPreferredSize();

                engineHost.chart().setBounds(0, 0, width, height);
                stockInfo.setBounds(0, 0, 0, 0);
                annotationPanel.setBounds(0, 0, width, height);
                if (nativeLegend.isVisible()) {
                    Dimension legendSize = nativeLegend.getPreferredSize();
                    int legendWidth = Math.min(Math.max(0, width - overlaySize.width - 28), legendSize.width);
                    nativeLegend.setBounds(8, 6, Math.max(0, legendWidth), legendSize.height);
                } else {
                    nativeLegend.setBounds(0, 0, 0, 0);
                }
                overlayToolboxes.setBounds(
                        Math.max(0, width - overlaySize.width - 12),
                        6,
                        overlaySize.width,
                        overlaySize.height);
            }
        });
        
        setFont(chartFrame.getChartProperties().getFont());
        setForeground(chartFrame.getChartProperties().getFontColor());
        
        add(engineHost.chart());
        add(overlayToolboxes);
        add(annotationPanel);
        add(nativeLegend);
        add(stockInfo);

        // Keep overlays, annotation hit-testing, and in-pane legend above the engine chart.
        setComponentZOrder(stockInfo, 0);
        setComponentZOrder(nativeLegend, 1);
        setComponentZOrder(annotationPanel, 2);
        setComponentZOrder(overlayToolboxes, 3);
        setComponentZOrder(engineHost.chart(), 4);
    }
    
    public ChartContext getChartFrame() {
        return chartFrame;
    }
    
    public AnnotationPanel getAnnotationPanel() {
        return annotationPanel;
    }
    
    public void setAnnotationPanel(AnnotationPanel panel) {
        annotationPanel = panel;
    }
    
    public Range getRange() {
        return chartFrame.getChartData().getVisibleRange();
    }

    public one.chartsy.charting.Chart getEngineChart() {
        return engineHost.chart();
    }

    public Rectangle getRenderBounds() {
        return engineHost.plotBounds(this);
    }

    public double getLastPaintScaleX() {
        return lastPaintScaleX;
    }

    public void refreshEngine(boolean showTimeScale) {
        if (!overlayToolboxesUpdated)
            updateOverlayToolbar();
        if (!chartFrame.getChartData().hasDataset()) {
            annotationPanel.repaint();
            return;
        }
        chartFrame.getChartData().calculateRange(chartFrame, overlays);
        engineHost.configurePriceChart(chartFrame, overlays, showTimeScale);
        annotationPanel.repaint();
    }
    
    @Override
    public void paintComponent(Graphics g) {
        if (!overlayToolboxesUpdated)
            updateOverlayToolbar();
        updatePaintMetrics(g);
        super.paintComponent(g);
    }

    private void updatePaintMetrics(Graphics g) {
        if (!(g instanceof Graphics2D g2))
            return;

        double scaleX = effectiveScaleX(g2, this);
        boolean scaleChanged = Double.isFinite(scaleX)
                && scaleX > 0.0
                && Math.abs(scaleX - lastPaintScaleX) > SCALE_EPSILON;
        if (scaleChanged)
            lastPaintScaleX = scaleX;

        Rectangle plotBounds = getRenderBounds();
        int plotWidth = Math.max(0, plotBounds.width);
        boolean plotWidthChanged = plotWidth > 0
                && Math.abs(plotWidth - lastPlotWidth) >= PLOT_WIDTH_EPSILON;
        if (plotWidthChanged)
            lastPlotWidth = plotWidth;

        if (ChartViewportDebugDumper.isEnabled()) {
            double effectiveScale = Double.isFinite(scaleX) && scaleX > 0.0 ? scaleX : lastPaintScaleX;
            ChartViewportDebugDumper.dump(chartFrame, this, effectiveScale);
            ChartViewportDebugDumper.capture(this, plotBounds,
                    plotWidth + "@" + String.format(Locale.ROOT, "%.4f", lastPaintScaleX));
        }

        if ((scaleChanged || plotWidthChanged)
                && chartFrame.getChartData() != null
                && chartFrame.getChartData().hasDataset()) {
            javax.swing.SwingUtilities.invokeLater(() -> {
                if (isDisplayable())
                    chartFrame.refreshChartView();
            });
        }
    }

    private static double effectiveScaleX(Graphics2D g2, Component component) {
        double transformScale = Math.abs(g2.getTransform().getScaleX());
        GraphicsConfiguration configuration = (component != null) ? component.getGraphicsConfiguration() : null;
        double graphicsScale = 1.0d;
        if (configuration != null) {
            graphicsScale = Math.abs(configuration.getDefaultTransform().getScaleX());
            if (!Double.isFinite(graphicsScale) || graphicsScale <= 0.0)
                graphicsScale = 1.0d;
        }
        if (!Double.isFinite(transformScale) || transformScale <= 0.0)
            return graphicsScale;
        return Math.max(transformScale, graphicsScale);
    }
    
    public void setStockTitle(String title) {
        if (!stockInfo.getText().equals(title))
            stockInfo.setText(title);
    }
    
    protected void setStockTitleFromChartData(ChartData chartData) {
        String title;
        if (chartData == null || chartData.getSymbol() == null) {
            title = NbBundle.getMessage(ChartPanel.class, "ChartPanel.stockTitle.noData");
        } else {
            String name = chartData.getSymbol().name();
            String timeFrame = TimeFrameHelper.getName(chartData.getTimeFrame());
            String provider = (chartData.getDataProvider() == null)? " - ": chartData.getDataProvider().getName();
            title = NbBundle.getMessage(ChartPanel.class, "ChartPanel.stockTitle", name, timeFrame, provider);
        }
        
        // change label text if differs
        setStockTitle(title);
    }
    
    /**
     * Returns a copy list of overlays currently attached to this chart
     * panel.
     * 
     * @return an overlay list copy
     */
    public List<Overlay> getOverlays() {
        return new ArrayList<>(overlays);
    }
    
    public Overlay getOverlay(int index) {
        if (index < 0 || index >= overlays.size()) {
            return null;
        }
        return overlays.get(index);
    }
    
    public boolean hasOverlays() {
        return !overlays.isEmpty();
    }
    
    public int getOverlaysCount() {
        return overlays.size();
    }
    
    public void addOverlay(Overlay overlay) {
        overlays.add(overlay);
        updateOverlayToolbar();
    }
    
    public void removeOverlay(Overlay overlay) {
        overlays.remove(overlay);
        overlay.close();
        updateOverlayToolbar();
    }
    
    public void removeAllOverlays() {
        List<Overlay> oldOverlays = new ArrayList<>(overlays);
        overlays.clear();
        oldOverlays.forEach(Overlay::close);
        updateOverlayToolbar();
    }
    
    public void updateOverlayToolbar() {
        int width = 0;
        int height = 0;
        
        overlayToolboxes.removeAll();
        for (Overlay overlay : overlays) {
            OverlayToolbox overlayToolbox = new OverlayToolbox(overlay);
            overlayToolboxes.add(overlayToolbox);
            overlayToolbox.update();
            
            width += overlayToolbox.getWidth() + 16;
            height = overlayToolbox.getHeight() + 4;
        }
        
        overlayToolboxes.validate();
        overlayToolboxes.repaint();
        
        overlayToolboxes.setBounds(overlayToolboxes.getX(), overlayToolboxes.getY(), width, height);
        overlayToolboxesUpdated = true;
    }
    
    @Override
    public Rectangle getBounds() {
        return new Rectangle(0, 0, getWidth(), getHeight());
    }
    
    public Rectangle getBounds(Insets insets) {
        Rectangle bounds = getBounds();
        bounds.x += insets.left;
        bounds.y += insets.top;
        bounds.width -= insets.left + insets.right;
        bounds.height -= insets.top + insets.bottom;
        
        return bounds;
    }
    
    @Override
    public Insets getInsets() {
        return getInsets(null);
    }
    
    @Override
    public Insets getInsets(Insets insets) {
        Rectangle bounds = getBounds();
        if (insets == null)
            insets = new Insets(0, 0, 0, 0);
        
        insets = super.getInsets(insets);
        insets.left += 2;
        insets.right += 2;
        insets.top += 2 + bounds.height/100;
        insets.bottom += 2 + bounds.height/100;
        return insets;
    }
    
    public final class OverlayToolbox extends JToolBar implements Serializable {
        
        private final Overlay overlay;
        private final JLabel overlayLabel;
        private final JComponent container;
        public boolean mouseOver = false;
        private final Color backColor = ColorServices.getDefault().getTransparentColor(Color.LIGHT_GRAY, 192);
        
        public OverlayToolbox(Overlay overlay) {
            super(JToolBar.HORIZONTAL);
            this.overlay = Objects.requireNonNull(overlay);
            setOpaque(false);
            setFloatable(false);
            setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
            
            overlayLabel = new JLabel();
            overlayLabel.setHorizontalTextPosition(SwingConstants.LEFT);
            overlayLabel.setVerticalTextPosition(SwingConstants.CENTER);
            overlayLabel.setOpaque(false);
            overlayLabel.setBorder(BorderFactory.createEmptyBorder());
            overlayLabel.setFont(ChartPanel.this.getFont());
            overlayLabel.setForeground(ChartPanel.this.getForeground());
            add(overlayLabel);
            
            container = new JPanel();
            container.setOpaque(false);
            container.setLayout(new BoxLayout(container, BoxLayout.X_AXIS));
            add(container);
            update();
            
            addMouseListener(new MouseAdapter() {
                
                public @Override
                void mouseEntered(MouseEvent e) {
                    mouseOver = true;
                    revalidate();
                    repaint();
                }
                
                public @Override
                void mouseExited(MouseEvent e) {
                    setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
                    mouseOver = false;
                    revalidate();
                    repaint();
                }
            });
        }
        
        @Override
        public int getWidth() {
            return getLayout().preferredLayoutSize(this).width;
        }
        
        @Override
        public int getHeight() {
            return getLayout().preferredLayoutSize(this).height;
        }
        
        public void update() {
            // remove all buttons
            container.removeAll();
            
            OverlayToolboxButton button;
            
            // Settings
            container.add(button = new OverlayToolboxButton(overlaySettings(overlay)));
            button.setText("");
            button.setToolTipText("Settings");
            
            // Remove
            container.add(button = new OverlayToolboxButton(removeAction(overlay)));
            button.setText("");
            button.setToolTipText("Remove");
            
            revalidate();
            repaint();
        }
        
        @Override
        public void paint(Graphics g) {
            if (!overlayLabel.getFont().equals(chartFrame.getChartProperties().getFont()))
                overlayLabel.setFont(chartFrame.getChartProperties().getFont());
            if (!overlayLabel.getForeground().equals(chartFrame.getChartProperties().getFontColor()))
                overlayLabel.setForeground(chartFrame.getChartProperties().getFontColor());
            
            Graphics2D g2 = Graphics2DHelper.prepareGraphics2D(g);
            g2.setPaintMode();
            
            if (mouseOver) {
                g2.setColor(backColor);
                int x = overlayLabel.getLocation().x - getInsets().left;
                int y = overlayLabel.getLocation().y - getInsets().top;
                RoundRectangle2D roundRectangle = new RoundRectangle2D.Double(x, y, getWidth(), getHeight(), 10, 10);
                g2.fill(roundRectangle);
            }
            
            super.paint(g);
        }
        
        public class OverlayToolboxButton extends JButton implements Serializable {
            
            public OverlayToolboxButton(Action action) {
                super(action);
                setOpaque(false);
                setFocusable(false);
                setFocusPainted(false);
                setBorderPainted(false);
                setContentAreaFilled(false);
                setMargin(new Insets(0, 0, 0, 0));
                setBorder(new Border() {

                    @Override
                    public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
                    }
                    
                    @Override
                    public Insets getBorderInsets(Component c) {
                        return new Insets(0, 2, 0, 2);
                    }
                    
                    @Override
                    public boolean isBorderOpaque() {
                        return true;
                    }
                });
                addMouseListener(new MouseAdapter() {
                    @Override
                    public  void mouseExited(MouseEvent e) {
                        setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
                        mouseOver = false;
                        OverlayToolbox.this.repaint();
                    }

                    @Override
                    public void mouseEntered(MouseEvent e) {
                        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                        mouseOver = true;
                        OverlayToolbox.this.repaint();
                    }
                });
            }
        }
    }
    
    private AbstractAction overlaySettings(Overlay overlay) {
        return new AbstractAction("Overlay Settings", IconResource.getIcon("settings")) {
            @Override
            public void actionPerformed(ActionEvent e) {
                ChartAction.openSettingsWindow(overlay);
                chartFrame.getMainPanel().repaint();
            }
        };
    }
    
    private AbstractAction removeAction(Overlay overlay) {
        return new AbstractAction("Remove Indicator", IconResource.getIcon("remove")) {
            @Override
            public void actionPerformed(ActionEvent e) {
                chartFrame.fireOverlayRemoved(overlay);
            }
        };
    }
}
