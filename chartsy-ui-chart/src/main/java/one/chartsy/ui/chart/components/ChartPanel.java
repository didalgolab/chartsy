/*
 * Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.ui.chart.components;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
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
import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import org.openide.util.lookup.Lookups;
import org.openide.util.lookup.ProxyLookup;


public class ChartPanel extends JLayeredPane implements Serializable {

    private final ChartContext chartFrame;
    private AnnotationPanel annotationPanel;
    private JLabel stockInfo;
    private JToolBar overlayToolboxes;
    /** The list of overlays displayed in this panel. */
    private final List<Overlay> overlays = new ArrayList<>();
    private boolean overlayToolboxesUpdated;
    public static final java.util.UUID UUID = java.util.UUID.fromString("43229972-5474-11e7-b114-b2f933d5fe66");
    
    public ChartPanel(ChartContext frame) {
        chartFrame = frame;
        Lookup lookup = new ProxyLookup(Lookups.singleton(chartFrame), Lookup.getDefault());
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
        //stockInfo.setForeground(chartFrame.getChartProperties().getFontColor());
        stockInfo.setForeground(Color.GRAY);
        
        setStockTitleFromChartData(chartFrame.getChartData());
        
        ChartFrameListener frameAdapter = new ChartFrameListener() {

            @Override
            public void datasetChanged(CandleSeries quotes) {
                setStockTitleFromChartData(chartFrame.getChartData());
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
                repaint();
            }
            
            @Override
            public void overlayAdded(Overlay overlay) {
                addOverlay(overlay);
                chartFrame.getChartData().calculateRange(chartFrame, overlays);
                chartFrame.getMainPanel().revalidate();
                chartFrame.getMainPanel().repaint();
            }
            
            @Override
            public void overlayRemoved(Overlay overlay) {
                removeOverlay(overlay);
                chartFrame.getChartData().calculateRange(chartFrame, overlays);
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
                
                stockInfo.setBounds(0, 0, width, stockInfo.getPreferredSize().height);
                annotationPanel.setBounds(0, 2, width - 4, height - 4);
                overlayToolboxes.setLocation(0, stockInfo.getPreferredSize().height + 1);
            }
        });
        
        setFont(chartFrame.getChartProperties().getFont());
        setForeground(chartFrame.getChartProperties().getFontColor());
        
        add(overlayToolboxes);
        add(annotationPanel);
        add(stockInfo);
        
        setComponentZOrder(overlayToolboxes, 0);
        setComponentZOrder(annotationPanel, 1);
        setComponentZOrder(stockInfo, 2);
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
    
    @Override
    public void paintComponent(Graphics g) {
        Graphics2D g2 = Graphics2DHelper.prepareGraphics2D(g);
        boolean isAdjusting = chartFrame.getValueIsAdjusting();
        if (isAdjusting) {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            g2.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_SPEED);
        }
        
        if (!overlayToolboxesUpdated)
            updateOverlayToolbar();
        
        chartFrame.getChartData().calculateRange(chartFrame, overlays);
        Chart chart = chartFrame.getChartData().getChart();
        if (chart != null)
            chart.paint(g2, chartFrame, getWidth(), getHeight());
        
        if (!overlays.isEmpty()) {
            Rectangle bounds = getBounds(getInsets());
            for (Overlay overlay : overlays)
                overlay.paint(g2, chartFrame, bounds);
        }
        
        super.paintComponent(g);
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
        if (index < 0 || index > overlays.size()) {
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
            
            overlayLabel = new JLabel(overlay.getLabel());
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
            if (!overlayLabel.getText().equals(overlay.getLabel()))
                overlayLabel.setText(overlay.getLabel());
            
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
