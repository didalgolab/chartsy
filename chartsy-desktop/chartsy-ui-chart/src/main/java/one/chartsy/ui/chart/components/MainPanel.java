/*
 * Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.ui.chart.components;

import java.awt.*;
import java.awt.image.VolatileImage;

import javax.swing.BorderFactory;
import javax.swing.JLayeredPane;

import one.chartsy.ui.chart.ChartContext;
import one.chartsy.ui.chart.ChartData;
import one.chartsy.ui.chart.ChartRenderingSystem;
import one.chartsy.ui.chart.axis.DateAxis;
import one.chartsy.ui.chart.axis.Grid;
import one.chartsy.ui.chart.axis.PriceAxis;

public class MainPanel extends JLayeredPane {

    private final ChartContext chartFrame;
    private final ChartStackPanel sPane;
    private final Grid grid;
    private final DateAxis dateAxis;
    private final PriceAxis priceAxis;
    
    
    public MainPanel(ChartContext frame) {
        chartFrame = frame;
        sPane = new ChartStackPanel(chartFrame);
        grid = new Grid(chartFrame);
        dateAxis = new DateAxis(chartFrame);
        priceAxis = new PriceAxis(chartFrame);
        
        setOpaque(true);
        setBackground(chartFrame.getChartProperties().getBackgroundColor());
        setBorder(BorderFactory.createEmptyBorder(2, 20, 0, 0));
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
                int right = ChartData.dataOffset.right;
                int bottom = dateAxis.getPreferredSize().height;
                Insets insets = parent.getInsets();
                int w = parent.getWidth() - insets.left - insets.right - right;
                int h = parent.getHeight() - insets.top - insets.bottom - bottom;
                
                grid.setBounds(insets.left, insets.top, w, h);
                dateAxis.setBounds(insets.left, insets.top + h, w, bottom);
                priceAxis.setBounds(insets.left + insets.right + w, insets.top,
                        right, /*insets.top + insets.bottom +*/ h);
                sPane.setBounds(insets.left, insets.top, w, h);
            }
        });
        
        add(sPane);
        add(dateAxis);
        add(priceAxis);
        add(grid);
        
        putClientProperty("print.printable", Boolean.TRUE);
        putClientProperty("print.name", "");
    }
    
    public ChartStackPanel getStackPanel() {
        return sPane;
    }
    
    //@Override
    public void paint0(Graphics g) {
        chartFrame.getChartData().calculate(chartFrame);
        chartFrame.getChartData().calculateRange(chartFrame, sPane.getChartPanel().getOverlays());
        
        setBackground(chartFrame.getChartProperties().getBackgroundColor());
        super.paint(g);
    }
    
    private VolatileImage vImg;

    public VolatileImage createVolatileImage() {
        return createVolatileImage(getWidth(), getHeight());
    }

    public void renderOffscreen(Shape clip) {
        do {
            if (vImg == null
                    || vImg.validate(getGraphicsConfiguration()) == VolatileImage.IMAGE_INCOMPATIBLE
                    || vImg.getHeight() != getHeight()
                    || vImg.getWidth() != getWidth()) {
                vImg = createVolatileImage();
            }
            Graphics2D g2 = vImg.createGraphics();
            if (clip != null)
                g2.setClip(clip);
            // paint Component to the volatile image
            super.paint(g2);
            g2.dispose();
            // in case of lost contents next iteration should restore the entire image area
            clip = null;
        } while (vImg.contentsLost());
    }

    public void paintOnscreen(Graphics gScreen) {
        do {
            int returnCode = vImg.validate(getGraphicsConfiguration());
            if (returnCode == VolatileImage.IMAGE_RESTORED) {
                // Contents need to be restored
                renderOffscreen(null);
            } else if (returnCode == VolatileImage.IMAGE_INCOMPATIBLE) {
                // old vImg doesn't work with new GraphicsConfig; re-create it
                vImg = createVolatileImage();
                renderOffscreen(null);
            }
            int width = getWidth() - 2;
            int height = getHeight() - 2;
            gScreen.drawImage(vImg, 1, 1, width, height, 1, 1, width, height, null);
        } while (vImg.contentsLost());
    }

    @Override
    public void paint(Graphics g) {
        chartFrame.getChartData().calculate(chartFrame);
        chartFrame.getChartData().calculateRange(chartFrame, sPane.getChartPanel().getOverlays());
        
        if (vImg == null || ChartRenderingSystem.current().isRerender())
            renderOffscreen(g.getClip());
        paintOnscreen(g);
    }
    
    @Override
    public void print(Graphics g) {
        super.paint(g);
    }
    
    public ChartPanel getChartPanel() {
        return getStackPanel().getChartPanel();
    }
    
    public void deselectAll() {
        getChartPanel().getAnnotationPanel().deselectAll();
        for (IndicatorPanel ip : getStackPanel().getIndicatorPanels()) {
            ip.getAnnotationPanel().deselectAll();
        }
    }
}
