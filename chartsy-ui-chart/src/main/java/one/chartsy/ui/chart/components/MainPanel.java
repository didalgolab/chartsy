/*
 * Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.ui.chart.components;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.image.BufferedImage;

import javax.swing.BorderFactory;
import javax.swing.JLayeredPane;

import one.chartsy.ui.chart.ChartContext;
import one.chartsy.ui.chart.ChartData;
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
        setDoubleBuffered(true);
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
                int bottom = ChartData.dataOffset.bottom;
                Insets insets = parent.getInsets();
                int w = parent.getWidth() - insets.left - insets.right - right;
                int h = parent.getHeight() - insets.top - insets.bottom - bottom;
                
                grid.setBounds(insets.left, insets.top, w, h);
                dateAxis.setBounds(insets.left, insets.top + h /* + bottom */,
                        w, bottom);
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
    
    @Override
    public void paint(Graphics g) {
        chartFrame.getChartData().calculate(chartFrame);
        chartFrame.getChartData().calculateRange(chartFrame, sPane.getChartPanel().getOverlays());
        
        setBackground(chartFrame.getChartProperties().getBackgroundColor());
        super.paint(g);
    }
    
    private BufferedImage img;
    
    //	@Override
    public void paint2(Graphics g) {
        chartFrame.getChartData().calculate(chartFrame);
        chartFrame.getChartData().calculateRange(chartFrame, sPane.getChartPanel().getOverlays());
        
        setBackground(chartFrame.getChartProperties().getBackgroundColor());
        if (img == null || img.getHeight() != getHeight() || img.getWidth() != getWidth()) {
            if (img != null)
                img.flush();
            img = (BufferedImage) createImage(getWidth(), getHeight());
        }
        Graphics2D g2 = img.createGraphics();
        g2.setClip(g.getClip());
        super.paint(g2);
        g2.dispose();
        g.drawImage(img, 0, 0, null);
    }
    
    @Override
    public void print(Graphics g) {
        // TODO Auto-generated method stub
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
