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
import java.util.UUID;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JToolBar;
import javax.swing.SwingConstants;
import javax.swing.border.Border;

import one.chartsy.ui.chart.ChartContext;
import one.chartsy.ui.chart.ChartProperties;
import one.chartsy.ui.chart.IconResource;
import one.chartsy.ui.chart.Indicator;
import one.chartsy.ui.chart.action.ChartAction;
import one.chartsy.ui.chart.internal.ColorServices;
import one.chartsy.ui.chart.internal.Graphics2DHelper;

/**
 * Indicator visual container.
 *
 * @author Mariusz Bernacki
 * 
 */
public class IndicatorPanel extends JPanel {
    /** The chart frame to which this indicator panel is attached. */
    private final ChartContext chartFrame;
    
    private AnnotationPanel annotationPanel;
    private IndicatorToolbox toolbox;
    private final Indicator indicator;
    private final UUID id;
    
    /** Indicates the minimized state of this indicator panel. */
    private boolean minimized;
    
    
    public IndicatorPanel(ChartContext frame, Indicator indicator) {
        this.chartFrame = frame;
        this.indicator = indicator;
        this.id = indicator.getPanelId();
        initializeUIElements();
    }
    
    public Rectangle getBounds(Insets insets) {
        Rectangle bounds = getBounds();
        bounds.x = insets.left;
        bounds.y = insets.top;
        bounds.width -= insets.left + insets.right;
        bounds.height -= insets.top + insets.bottom;
        
        return bounds;
    }
    
    private void initializeUIElements() {
        annotationPanel = new AnnotationPanel(chartFrame);
        toolbox = new IndicatorToolbox();
        toolbox.setLocation(0, 0);
        
        setOpaque(false);
        setDoubleBuffered(true);
        setBorder(BorderFactory.createEmptyBorder(1, 2, 1, 2));
        setLayout(new LayoutManager() {
            @Override
            public void addLayoutComponent(String name, Component comp) {}
            @Override
            public void removeLayoutComponent(Component comp) {}
            
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
                int toolboxWidth = toolbox.getWidth();
                int toolboxHeight = toolbox.getHeight();
                
                annotationPanel.setBounds(0, 2, width - 4, height - 4);
                toolbox.setBounds(0, 0, toolboxWidth, toolboxHeight);
            }
        });
        
        add(toolbox);
        add(annotationPanel);
        doLayout();
        
        // revalidate parent layout whenever the panel state change
        addPropertyChangeListener("minimized", e -> {
            updateToolbox();
            
            Container parent = getParent();
            parent.revalidate();
            parent.repaint();
        });
    }
    
    @Override
    public void removeNotify() {
        super.removeNotify();
        indicator.close();
    }
    
    public ChartContext getChartFrame() {
        return chartFrame;
    }
    
    public AnnotationPanel getAnnotationPanel() {
        return annotationPanel;
    }
    
    public Indicator getIndicator() {
        return indicator;
    }
    
    /**
     * Gets the minimized state of the indicator panel.
     * 
     * @return current minimized state
     */
    public boolean isMinimized() {
        return minimized;
    }
    
    /**
     * Sets the minimized state of the indicator panel.
     * <p>
     * If the argument is {@code true} causes the panel to switch to the
     * minimized state, and if the argument is {@code false} causes the panel to
     * switch back to the normal state.
     * <p>
     * If the panel is already minimized the method does nothing. Otherwise the
     * panel state is changed and the {@code PropertyChangeEvent} is generated.
     * 
     * @param minimized
     *            the new minimized state to set
     */
    public void setMinimized(boolean minimized) {
        boolean old = isMinimized();
        if (minimized != old) {
            this.setMaximumSize(minimized? toolbox.getPreferredSize() : null);
            this.minimized = minimized;
            
            firePropertyChange("minimized", old, minimized);
        }
    }
    
    public IndicatorToolbox getToolbox() {
        return toolbox;
    }
    
    public void updateToolbox() {
        toolbox.update();
    }
    
    private AbstractAction indicatorSettings(ChartContext frame, IndicatorPanel panel) {
        return new AbstractAction("Indicator Settings", IconResource.getIcon("settings")) {
            @Override
            public void actionPerformed(ActionEvent e) {
                ChartAction.openSettingsWindow(panel.getIndicator());
                chartFrame.getMainPanel().repaint();
            }
        };
    }
    
    private AbstractAction moveUp(ChartContext frame, IndicatorPanel panel) {
        return new AbstractAction("Move Indicator Up", IconResource.getIcon("up")) {
            @Override
            public void actionPerformed(ActionEvent e) {
                frame.getMainPanel().getStackPanel().moveUp(panel);
            }
        };
    }
    
    private AbstractAction moveDown(ChartContext frame, IndicatorPanel panel) {
        return new AbstractAction("Move Indicator Down", IconResource.getIcon("down")) {
            @Override
            public void actionPerformed(ActionEvent e) {
                frame.getMainPanel().getStackPanel().moveDown(panel);
            }
        };
    }
    
    private Action minimize() {
        return new AbstractAction("Minimize Indicator", IconResource.getIcon("minimize")) {
            @Override
            public void actionPerformed(ActionEvent e) {
                setMinimized(true);
            }
        };
    }
    
    private Action maximize() {
        return new AbstractAction("Maximize Indicator", IconResource.getIcon("maximize")) {
            @Override
            public void actionPerformed(ActionEvent e) {
                setMinimized(false);
            }
        };
    }
    
    private Action removeAction(ChartContext frame, IndicatorPanel panel) {
        return new AbstractAction("Remove Indicator", IconResource.getIcon("remove")) {
            @Override
            public void actionPerformed(ActionEvent e) {
                frame.indicatorRemoved(indicator);
            }
        };
    }
    
    @Override
    public void paintComponent(Graphics g) {
        Graphics2D g2 = Graphics2DHelper.prepareGraphics2D(g);
        boolean isAdjusting = chartFrame.getValueIsAdjusting();
        if (isAdjusting) {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            g2.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_SPEED);
        }
        
        ChartProperties cp = chartFrame.getChartProperties();
        Rectangle bounds = getBounds(getInsets());
        //bounds.setLocation(0, 0);
        
        if (indicator != null) {
            boolean isLog = cp.getAxisLogarithmicFlag();
            if (isLog)
                cp.setAxisLogarithmicFlag(false);
            if (!isMinimized() || indicator.isMinimizedPaint())
                indicator.paint(g2, chartFrame, bounds);
            if (isLog)
                cp.setAxisLogarithmicFlag(true);
        }
        
        super.paintComponent(g);
    }
    
    public final class IndicatorToolbox extends JToolBar implements Serializable {
        
        private final JLabel indicatorLabel;
        private final JComponent container;
        private boolean mouseOver;
        private final Color backColor = ColorServices.getDefault().getTransparentColor(new Color(0x1C2331), 50);
        
        public IndicatorToolbox() {
            super(JToolBar.HORIZONTAL);
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
            setFloatable(false);
            
            indicatorLabel = new JLabel(indicator.getLabel());
            indicatorLabel.setHorizontalTextPosition(SwingConstants.LEFT);
            indicatorLabel.setVerticalTextPosition(SwingConstants.CENTER);
            indicatorLabel.setOpaque(false);
            add(indicatorLabel);
            
            container = new JPanel();
            container.setOpaque(false);
            container.setLayout(new BoxLayout(container, BoxLayout.X_AXIS));
            add(container);
            update();
            
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    mouseOver = true;
                    validate();
                    repaint();
                }
                
                @Override
                public void mouseExited(MouseEvent e) {
                    setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
                    mouseOver = false;
                    validate();
                    repaint();
                }
            });
        }
        
        public @Override int getWidth() {
            return this.getLayout().preferredLayoutSize(this).width;
        }
        
        public @Override int getHeight() {
            return this.getLayout().preferredLayoutSize(this).height;
        }
        
        public void update() {
            // remove all buttons
            container.removeAll();
            
            // number of indicators
            Container stack = chartFrame.getMainPanel().getStackPanel();
            
            ToolboxButton button;
            
            // Settings
            container.add(
                    button = new ToolboxButton(indicatorSettings(IndicatorPanel.this.chartFrame, IndicatorPanel.this)));
            button.setText("");
            button.setToolTipText("Settings");
            
            int panelCount = stack.getComponentCount();
            if (panelCount > 1) {
                // Attach Move Up Action except for the uppermost indicator panel
                if (stack.getComponent(0) != IndicatorPanel.this) {
                    container.add(button = new ToolboxButton(moveUp(IndicatorPanel.this.chartFrame, IndicatorPanel.this)));
                    button.setText("");
                    button.setToolTipText("Move Up");
                }
                
                // Attach Move Down Action except for the lowermost indicator panel
                if (stack.getComponent(panelCount - 1) != IndicatorPanel.this) {
                    // Move Down
                    container
                    .add(button = new ToolboxButton(moveDown(IndicatorPanel.this.chartFrame, IndicatorPanel.this)));
                    button.setText("");
                    button.setToolTipText("Move Down");
                }
            }
            
            // Toggle Maximize/Minimize
            container.add(button = new ToolboxButton(
                    isMinimized() ? maximize() : minimize()));
            button.setText("");
            button.setToolTipText(isMinimized() ? "Maximize" : "Minimize");
            
            // Remove
            container
            .add(button = new ToolboxButton(removeAction(IndicatorPanel.this.chartFrame, IndicatorPanel.this)));
            button.setText("");
            button.setToolTipText("Remove");
            
            revalidate();
            repaint();
        }
        
        @Override
        public void paint(Graphics g) {
            if (!indicatorLabel.getFont().equals(chartFrame.getChartProperties().getFont()))
                indicatorLabel.setFont(chartFrame.getChartProperties().getFont());
            if (!indicatorLabel.getForeground().equals(chartFrame.getChartProperties().getFontColor()))
                indicatorLabel.setForeground(chartFrame.getChartProperties().getFontColor());
            if (!indicatorLabel.getText().equals(indicator.getLabel()))
                indicatorLabel.setText(indicator.getLabel());
            
            Rectangle oldClip = g.getClipBounds();
            Rectangle newClip = getBounds();
            g.setClip(newClip);
            
            if (mouseOver) {
                Graphics2D g2 = Graphics2DHelper.prepareGraphics2D(g);
                g2.setColor(backColor);
                RoundRectangle2D roundRectangle = new RoundRectangle2D.Double(getX(), getY(), getWidth(), getHeight(),10, 10);
                g2.fill(roundRectangle);
            }
            
            super.paint(g);
            
            g.setClip(oldClip);
        }
        
        public class ToolboxButton extends JButton implements Serializable {
            
            public ToolboxButton(Action action) {
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
                    public void mouseExited(MouseEvent e) {
                        setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
                        IndicatorToolbox.this.mouseOver = false;
                        IndicatorToolbox.this.validate();
                        IndicatorToolbox.this.repaint();
                    }

                    @Override
                    public void mouseEntered(MouseEvent e) {
                        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                        IndicatorToolbox.this.mouseOver = true;
                        IndicatorToolbox.this.validate();
                        IndicatorToolbox.this.repaint();
                    }

                    @Override
                    public void mousePressed(MouseEvent e) {
                        IndicatorToolbox.this.mouseOver = false;
                        IndicatorToolbox.this.validate();
                        IndicatorToolbox.this.repaint();
                    }
                });
            }
        }
    }
    
    public UUID getId() {
        return id;
    }
    
}