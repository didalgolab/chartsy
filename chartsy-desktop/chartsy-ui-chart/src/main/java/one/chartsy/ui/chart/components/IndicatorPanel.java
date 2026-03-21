/*
 * Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.ui.chart.components;

import one.chartsy.charting.Legend;
import one.chartsy.charting.Scale;
import one.chartsy.core.Range;
import one.chartsy.ui.chart.ChartContext;
import one.chartsy.ui.chart.IconResource;
import one.chartsy.ui.chart.Indicator;
import one.chartsy.ui.chart.action.ChartActions;
import one.chartsy.ui.chart.internal.ColorServices;
import one.chartsy.ui.chart.internal.Graphics2DHelper;
import one.chartsy.ui.chart.internal.IndicatorPaneSupport;
import one.chartsy.ui.chart.internal.engine.EngineChartHost;
import one.chartsy.ui.chart.data.VisualRange;

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
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Indicator pane visual container. A single pane may host multiple indicators
 * that share the same persisted {@code panelId}.
 */
public class IndicatorPanel extends JPanel {
    private final ChartContext chartFrame;
    private final EngineChartHost engineHost;
    private final Legend nativeLegend;
    private final List<Indicator> indicators = new ArrayList<>();
    private final UUID id;

    private AnnotationPanel annotationPanel;
    private IndicatorToolbox toolbox;
    private VisualRange paneRange = new VisualRange(Range.of(0.0, 1.0));

    private boolean minimized;


    public IndicatorPanel(ChartContext frame, UUID paneId, List<? extends Indicator> paneIndicators, Scale sharedTimeScale) {
        this.chartFrame = frame;
        this.id = paneId;
        this.indicators.addAll(paneIndicators);
        this.engineHost = new EngineChartHost(sharedTimeScale);
        this.nativeLegend = engineHost.legend();
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
                int toolboxWidth = toolbox.getWidth();
                int toolboxHeight = toolbox.getHeight();

                engineHost.chart().setBounds(0, 0, width, height);
                annotationPanel.setBounds(0, 0, width, height);
                if (nativeLegend.isVisible()) {
                    Dimension legendSize = nativeLegend.getPreferredSize();
                    int legendWidth = Math.min(Math.max(0, width - toolboxWidth - 28), legendSize.width);
                    nativeLegend.setBounds(8, 6, Math.max(0, legendWidth), legendSize.height);
                } else {
                    nativeLegend.setBounds(0, 0, 0, 0);
                }
                toolbox.setBounds(Math.max(0, width - toolboxWidth - 12), 6, toolboxWidth, toolboxHeight);
            }
        });

        add(engineHost.chart());
        add(toolbox);
        add(annotationPanel);
        add(nativeLegend);
        setComponentZOrder(nativeLegend, 0);
        setComponentZOrder(annotationPanel, 1);
        setComponentZOrder(toolbox, 2);
        setComponentZOrder(engineHost.chart(), 3);
        doLayout();
        updatePaneVisibility();

        addPropertyChangeListener("minimized", e -> {
            updateToolbox();
            updatePaneVisibility();

            Container parent = getParent();
            if (parent != null) {
                if (parent instanceof ChartStackPanel stackPanel)
                    stackPanel.refreshPanels();
                parent.revalidate();
                parent.repaint();
            }
        });
    }

    public ChartContext getChartFrame() {
        return chartFrame;
    }

    public AnnotationPanel getAnnotationPanel() {
        return annotationPanel;
    }

    public Indicator getIndicator() {
        return indicators.isEmpty() ? null : indicators.getFirst();
    }

    public List<Indicator> getIndicators() {
        return List.copyOf(indicators);
    }

    public boolean containsIndicator(Indicator indicator) {
        return indicators.contains(indicator);
    }

    public void addIndicator(Indicator indicator) {
        if (indicator != null && !indicators.contains(indicator))
            indicators.add(indicator);
    }

    public boolean removeIndicator(Indicator indicator) {
        return indicators.remove(indicator);
    }

    public boolean isEmpty() {
        return indicators.isEmpty();
    }

    public VisualRange getPaneRange() {
        return paneRange;
    }

    public boolean isMinimized() {
        return minimized;
    }

    public void setMinimized(boolean minimized) {
        boolean old = isMinimized();
        if (minimized != old) {
            this.setMaximumSize(minimized ? toolbox.getPreferredSize() : null);
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

    public one.chartsy.charting.Chart getEngineChart() {
        return engineHost.chart();
    }

    public Rectangle getRenderBounds() {
        return engineHost.plotBounds(this);
    }

    public void refreshEngine(boolean showTimeScale, one.chartsy.charting.Chart masterChart) {
        updateToolbox();
        if (!chartFrame.getChartData().hasDataset() || indicators.isEmpty()) {
            updatePaneVisibility();
            annotationPanel.repaint();
            return;
        }
        paneRange = IndicatorPaneSupport.combinedRange(indicators, chartFrame);
        engineHost.configureIndicatorChart(chartFrame, indicators, paneRange, showTimeScale, masterChart);
        updatePaneVisibility();
        annotationPanel.repaint();
    }

    private void updatePaneVisibility() {
        boolean showPlot = !isMinimized() || indicators.stream().anyMatch(Indicator::isMinimizedPaint);
        engineHost.chart().setVisible(showPlot);
        annotationPanel.setVisible(showPlot);
        nativeLegend.setVisible(showPlot && nativeLegend.getComponentCount() > 0);
    }

    public void disposeResources() {
        indicators.forEach(Indicator::close);
        engineHost.close();
    }

    private AbstractAction indicatorSettings(ChartContext frame, IndicatorPanel panel) {
        return new AbstractAction("Edit Indicators", IconResource.getIcon("settings")) {
            @Override
            public void actionPerformed(ActionEvent e) {
                ChartActions.openIndicators((one.chartsy.ui.chart.ChartFrame) frame).actionPerformed(e);
            }
        };
    }

    private AbstractAction moveUp(ChartContext frame, IndicatorPanel panel) {
        return new AbstractAction("Move Indicator Pane Up", IconResource.getIcon("up")) {
            @Override
            public void actionPerformed(ActionEvent e) {
                frame.getMainPanel().getStackPanel().moveUp(panel);
            }
        };
    }

    private AbstractAction moveDown(ChartContext frame, IndicatorPanel panel) {
        return new AbstractAction("Move Indicator Pane Down", IconResource.getIcon("down")) {
            @Override
            public void actionPerformed(ActionEvent e) {
                frame.getMainPanel().getStackPanel().moveDown(panel);
            }
        };
    }

    private Action minimize() {
        return new AbstractAction("Minimize Indicator Pane", IconResource.getIcon("minimize")) {
            @Override
            public void actionPerformed(ActionEvent e) {
                setMinimized(true);
            }
        };
    }

    private Action maximize() {
        return new AbstractAction("Maximize Indicator Pane", IconResource.getIcon("maximize")) {
            @Override
            public void actionPerformed(ActionEvent e) {
                setMinimized(false);
            }
        };
    }

    private Action removeAction(ChartContext frame, IndicatorPanel panel) {
        return new AbstractAction("Remove Indicator Pane", IconResource.getIcon("remove")) {
            @Override
            public void actionPerformed(ActionEvent e) {
                frame.getMainPanel().getStackPanel().removePane(panel);
            }
        };
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

            indicatorLabel = new JLabel();
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

        @Override
        public int getWidth() {
            return getLayout().preferredLayoutSize(this).width;
        }

        @Override
        public int getHeight() {
            return getLayout().preferredLayoutSize(this).height;
        }

        public void update() {
            container.removeAll();
            indicatorLabel.setText(paneTitle());

            Container stack = chartFrame.getMainPanel().getStackPanel();
            ToolboxButton button;

            container.add(button = new ToolboxButton(indicatorSettings(chartFrame, IndicatorPanel.this)));
            button.setText("");
            button.setToolTipText("Edit Indicators");

            int panelCount = chartFrame.getMainPanel().getStackPanel().getIndicatorPanels().size();
            if (panelCount > 1) {
                if (stack.getComponent(0) != IndicatorPanel.this) {
                    container.add(button = new ToolboxButton(moveUp(chartFrame, IndicatorPanel.this)));
                    button.setText("");
                    button.setToolTipText("Move Up");
                }
                if (stack.getComponent(stack.getComponentCount() - 1) != IndicatorPanel.this) {
                    container.add(button = new ToolboxButton(moveDown(chartFrame, IndicatorPanel.this)));
                    button.setText("");
                    button.setToolTipText("Move Down");
                }
            }

            container.add(button = new ToolboxButton(isMinimized() ? maximize() : minimize()));
            button.setText("");
            button.setToolTipText(isMinimized() ? "Maximize" : "Minimize");

            container.add(button = new ToolboxButton(removeAction(chartFrame, IndicatorPanel.this)));
            button.setText("");
            button.setToolTipText("Remove Pane");

            revalidate();
            repaint();
        }

        @Override
        public void paint(Graphics g) {
            if (!indicatorLabel.getFont().equals(chartFrame.getChartProperties().getFont()))
                indicatorLabel.setFont(chartFrame.getChartProperties().getFont());
            if (!indicatorLabel.getForeground().equals(chartFrame.getChartProperties().getFontColor()))
                indicatorLabel.setForeground(chartFrame.getChartProperties().getFontColor());

            Rectangle oldClip = g.getClipBounds();
            Rectangle newClip = getBounds();
            g.setClip(newClip);

            if (mouseOver) {
                Graphics2D g2 = Graphics2DHelper.prepareGraphics2D(g);
                g2.setColor(backColor);
                RoundRectangle2D roundRectangle = new RoundRectangle2D.Double(getX(), getY(), getWidth(), getHeight(), 10, 10);
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

    private String paneTitle() {
        if (indicators.isEmpty())
            return "Pane";
        if (indicators.size() == 1)
            return indicators.getFirst().getLabel();
        return indicators.getFirst().getLabel() + " +" + (indicators.size() - 1);
    }
}
