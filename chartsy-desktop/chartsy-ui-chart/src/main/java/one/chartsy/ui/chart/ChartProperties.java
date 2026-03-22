/*
 * Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.ui.chart;

import one.chartsy.ui.chart.properties.AbstractPropertyListener;

import java.awt.Color;
import java.awt.Font;
import java.awt.Stroke;
import java.util.Objects;

/**
 *
 * @author Mariusz Bernacki
 */
public class ChartProperties extends AbstractPropertyListener {

    public static final double AXIS_TICK = 6;
    public static final double AXIS_DATE_STICK = 10;
    public static final double AXIS_PRICE_STICK = 5;
    public static final Color AXIS_COLOR = new Color(0x2e3436);
    public static final int AXIS_STROKE_INDEX = 0;
    public static final boolean AXIS_LOGARITHMIC_FLAG = true;
    
    public static final double BAR_WIDTH = PixelPerfectCandleGeometry.DEFAULT_BODY_WIDTH;
    public static final double SLOT_FILL_PERCENT = 87.5;
    public static final Color BAR_COLOR = new Color(0x2e3436);
    public static final int BAR_STROKE_INDEX = 0;
    public static final boolean BAR_VISIBILITY = true;
    public static final Color BAR_DOWN_COLOR = new Color(0xef2929);
    public static final boolean BAR_DOWN_VISIBILITY = true;
    public static final Color BAR_UP_COLOR = new Color(0x73d216);
    public static final boolean BAR_UP_VISIBILITY = true;
    
    public static final Color GRID_HORIZONTAL_COLOR = new Color(0xeeeeec);
    public static final int GRID_HORIZONTAL_STROKE_INDEX = 0;
    public static final boolean GRID_HORIZONTAL_VISIBILITY = true;
    public static final Color GRID_VERTICAL_COLOR = new Color(0xeeeeec);
    public static final int GRID_VERTICAL_STROKE_INDEX = 0;
    public static final boolean GRID_VERTICAL_VISIBILITY = true;
    
    public static final Color BACKGROUND_COLOR = new Color(0xffffff);
    public static final Font MAIN_FONT = ChartFonts.defaultChartFont();
    public static final Color FONT_COLOR = new Color(0x2e3436);
    
    public static final boolean MARKER_VISIBILITY = false;
    
    public static final boolean TOOLBAR_VISIBILITY = true;
    public static final boolean TOOLBAR_SMALL_ICONS = false;
    public static final boolean TOOLBAR_SHOW_LABELS = true;
    
    private final double axisTick = AXIS_TICK;
    private final double axisDateStick = AXIS_DATE_STICK;
    private final double axisPriceStick = AXIS_PRICE_STICK;
    private Color axisColor = AXIS_COLOR;
    private int axisStrokeIndex = AXIS_STROKE_INDEX;
    private boolean axisLogarithmicFlag = AXIS_LOGARITHMIC_FLAG;
    
    private double barWidth = BAR_WIDTH;
    private double slotFillPercent = SLOT_FILL_PERCENT;
    private Color barColor = BAR_COLOR;
    private int barStrokeIndex = BAR_STROKE_INDEX;
    private boolean barVisibility = BAR_VISIBILITY;
    private Color barDownColor = BAR_DOWN_COLOR;
    private boolean barDownVisibility = BAR_DOWN_VISIBILITY;
    private Color barUpColor = BAR_UP_COLOR;
    private boolean barUpVisibility = BAR_UP_VISIBILITY;
    
    private Color gridHorizontalColor = GRID_HORIZONTAL_COLOR;
    private int gridHorizontalStrokeIndex = GRID_HORIZONTAL_STROKE_INDEX;
    private boolean gridHorizontalVisibility = GRID_HORIZONTAL_VISIBILITY;
    private Color gridVerticalColor = GRID_VERTICAL_COLOR;
    private int gridVerticalStrokeIndex = GRID_VERTICAL_STROKE_INDEX;
    private boolean gridVerticalVisibility = GRID_VERTICAL_VISIBILITY;
    
    private Color backgroundColor = BACKGROUND_COLOR;
    private Font font = MAIN_FONT;
    private Color fontColor = FONT_COLOR;
    
    private boolean markerVisibility = MARKER_VISIBILITY;
    
    private boolean toolbarVisibility = TOOLBAR_VISIBILITY;
    private boolean toolbarSmallIcons = TOOLBAR_SMALL_ICONS;
    private boolean toolbarShowLabels = TOOLBAR_SHOW_LABELS;
    /** Determines if the annotations are visible for a chart. */
    private boolean annotationLayerVisible = true;
    

    public double getAxisTick() { return this.axisTick; }
    public double getAxisDateStick() { return this.axisDateStick; }
    public double getAxisPriceStick() {  return this.axisPriceStick; }
    
    public Color getAxisColor()
    {
        return this.axisColor;
    }
    public void setAxisColor(Color color)
    {
        if (color == null)
            return;
        Color old = axisColor;
        axisColor = color;
        fire("axisColor", old, axisColor);
    }
    
    public int getAxisStrokeIndex() 
    {
        return this.axisStrokeIndex;
    }
    
    public void setAxisStrokeIndex(int i) 
    {
        if (!BasicStrokes.isStrokeIndex(i))
            return;
        int old = axisStrokeIndex;
        axisStrokeIndex = i;
        fire("axisStrokeIndex", old, axisStrokeIndex);
    }
    
    public Stroke getAxisStroke() 
    {
        return BasicStrokes.getStroke(axisStrokeIndex);
    }
    
    public void setAxisStroke(Stroke s)
    {
        if (s == null)
            return;
        int old = axisStrokeIndex;
        axisStrokeIndex = BasicStrokes.getStrokeIndex(s);
        fire("axisStrokeIndex", old, axisStrokeIndex);
    }
    
    public boolean getAxisLogarithmicFlag() 
    {
        return axisLogarithmicFlag;
    }
    
    public void setAxisLogarithmicFlag(boolean b) 
    {
        boolean old = axisLogarithmicFlag;
        axisLogarithmicFlag = b;
        fire("axisLogarithmicFlag", old, axisLogarithmicFlag);
    }
    
    public void setBarWidth(double itemWidth)
    {
        if (itemWidth <= 0)
            return;
        double oldBarWidth = barWidth;
        double oldSlotFillPercent = slotFillPercent;
        barWidth = PixelPerfectCandleGeometry.snapBodyWidth(itemWidth);
        slotFillPercent = PixelPerfectCandleGeometry.fillPercent(barWidth);
        fire("barWidth", oldBarWidth, barWidth);
        fire("slotFillPercent", oldSlotFillPercent, slotFillPercent);
    }
    
    public double getBarWidth()
    {
        return barWidth;
    }

    public double getSlotFillPercent() {
        return slotFillPercent;
    }

    public void setSlotFillPercent(double slotFillPercent) {
        if (slotFillPercent <= 0)
            return;
        double old = this.slotFillPercent;
        this.slotFillPercent = Math.clamp(slotFillPercent, 1.0, 100.0);
        fire("slotFillPercent", old, this.slotFillPercent);
    }
    
    public Color getBarColor() 
    {
        return this.barColor;
    }
    
    public void setBarColor(Color color) 
    {
        if (color == null)
            return;
        Color old = barColor;
        barColor = color;
        fire("barColor", old, barColor);
    }
    
    public int getBarStrokeIndex() 
    {
        return this.barStrokeIndex;
    }
    
    public void setBarStrokeIndex(int i)
    {
        if (!BasicStrokes.isStrokeIndex(i))
            return;
        int old = barStrokeIndex;
        barStrokeIndex = i;
        fire("barStrokeIndex", old, barStrokeIndex);
    }
    
    public Stroke getBarStroke() 
    {
        return BasicStrokes.getStroke(barStrokeIndex);
    }
    
    public void setBarStroke(Stroke s) 
    {
        if (s == null)
            return;
        int old = barStrokeIndex;
        barStrokeIndex = BasicStrokes.getStrokeIndex(s);
        fire("barStrokeIndex", old, barStrokeIndex);
    }
    
    public boolean getBarVisibility() 
    {
        return this.barVisibility;
    }
    
    public void setBarVisibility(boolean b)
    {
        boolean old = barVisibility;
        barVisibility = b;
        fire("barVisibility", old, barVisibility);
    }
    
    public Color getBarDownColor() 
    {
        return this.barDownColor;
    }
    
    public void setBarDownColor(Color color) 
    {
        if (color == null)
            return;
        Color old = barDownColor;
        barDownColor = color;
        fire("barDownColor", old, barDownColor);
    }
    
    public boolean getBarDownVisibility() 
    {
        return this.barDownVisibility;
    }
    
    public void setBarDownVisibility(boolean b)
    {
        boolean old = barDownVisibility;
        barDownVisibility = b;
        fire("barDownVisibility", old, barDownVisibility);
    }
    
    public Color getBarUpColor() 
    {
        return this.barUpColor;
    }
    
    public void setBarUpColor(Color color) 
    {
        if (color == null)
            return;
        Color old = barUpColor;
        barUpColor = color;
        fire("barUpColor", old, barUpColor);
    }
    
    public boolean getBarUpVisibility() 
    {
        return this.barUpVisibility;
    }
    
    public void setBarUpVisibility(boolean b)
    {
        boolean old = barUpVisibility;
        barUpVisibility = b;
        fire("barUpVisibility", old, barUpVisibility);
    }
    
    public Color getGridHorizontalColor() 
    {
        return this.gridHorizontalColor;
    }
    
    public void setGridHorizontalColor(Color color) 
    {
        if (color == null)
            return;
        Color old = gridHorizontalColor;
        gridHorizontalColor = color;
        fire("gridHorizontalColor", old, gridHorizontalColor);
    }
    
    public int getGridHorizontalStrokeIndex() 
    {
        return this.gridHorizontalStrokeIndex;
    }
    
    public void setGridHorizontalStrokeIndex(int i)
    {
        if (!BasicStrokes.isStrokeIndex(i))
            return;
        int old = gridHorizontalStrokeIndex;
        gridHorizontalStrokeIndex = i;
        fire("gridHorizontalStrokeIndex", old, gridHorizontalStrokeIndex);
    }
    
    public Stroke getGridHorizontalStroke() 
    {
        return BasicStrokes.getStroke(gridHorizontalStrokeIndex);
    }
    
    public void setGridHorizontalStroke(Stroke s) 
    {
        if (s == null)
            return;
        int old = gridHorizontalStrokeIndex;
        gridHorizontalStrokeIndex = BasicStrokes.getStrokeIndex(s);
        fire("gridHorizontalStrokeIndex", old, gridHorizontalStrokeIndex);
    }
    
    public boolean getGridHorizontalVisibility() 
    {
        return this.gridHorizontalVisibility;
    }
    
    public void setGridHorizontalVisibility(boolean b)
    {
        boolean old = gridHorizontalVisibility;
        gridHorizontalVisibility = b;
        fire("gridHorizontalVisibility", old, gridHorizontalVisibility);
    }
    
    public Color getGridVerticalColor() 
    {
        return this.gridVerticalColor;
    }
    
    public void setGridVerticalColor(Color color) 
    {
        if (color == null)
            return;
        Color old = gridVerticalColor;
        gridVerticalColor = color;
        fire("gridVerticalColor", old, gridVerticalColor);
    }
    
    public int getGridVerticalStrokeIndex() 
    {
        return this.gridVerticalStrokeIndex;
    }
    
    public void setGridVerticalStrokeIndex(int i)
    {
        if (!BasicStrokes.isStrokeIndex(i))
            return;
        int old = gridVerticalStrokeIndex;
        gridVerticalStrokeIndex = i;
        fire("gridVerticalStrokeIndex", old, gridVerticalStrokeIndex);
    }
    
    public Stroke getGridVerticalStroke() {
        return BasicStrokes.getStroke(gridVerticalStrokeIndex);
    }
    
    public void setGridVerticalStroke(Stroke s) 
    {
        if (s == null)
            return;
        int old = gridVerticalStrokeIndex;
        gridVerticalStrokeIndex = BasicStrokes.getStrokeIndex(s);
        fire("gridVerticalStrokeIndex", old, gridVerticalStrokeIndex);
    }
    
    public boolean getGridVerticalVisibility() 
    {
        return this.gridVerticalVisibility;
    }
    
    public void setGridVerticalVisibility(boolean b)
    {
        boolean old = gridVerticalVisibility;
        gridVerticalVisibility = b;
        fire("gridVerticalVisibility", old, gridVerticalVisibility);
    }
    
    public Color getBackgroundColor() 
    {
        return this.backgroundColor;
    }
    
    public void setBackgroundColor(Color color) 
    {
        if (color == null)
            return;
        Color old = backgroundColor;
        backgroundColor = color;
        fire("backgroundColor", old, backgroundColor);
    }
    
    public Font getFont() 
    {
        return this.font;
    }
    
    public void setFont(Font font) 
    {
        if (font == null)
            return;
        Font old = this.font;
        this.font = font;
        fire("font", old, this.font);
    }
    
    public Color getFontColor() 
    {
        return this.fontColor;
    }
    
    public void setFontColor(Color color)
    {
        if (color == null)
            return;
        Color old = fontColor;
        fontColor = color;
        fire("fontColor", old, fontColor);
    }
    
    public void setMarkerVisibility(boolean b) 
    {
        boolean old = markerVisibility;
        markerVisibility = b;
        fire("markerVisibility", old, markerVisibility);
    }
    
    public boolean getMarkerVisibility()
    {
        return markerVisibility;
    }
    
    public boolean getToolbarVisibility() 
    {
        return toolbarVisibility;
    }
    
    public void setToolbarVisibility(boolean b) 
    {
        boolean old = toolbarVisibility;
        toolbarVisibility = b;
        fire("toolbarVisibility", old, toolbarVisibility);
    }
    
    public void toggleToolbarVisibility()
    {
        setToolbarVisibility(!toolbarVisibility);
    }
    
    public boolean getToolbarSmallIcons() 
    {
        return toolbarSmallIcons;
    }
    
    public void setToolbarSmallIcons(boolean b) 
    {
        boolean old = toolbarSmallIcons;
        toolbarSmallIcons = b;
        fire("toolbarSmallIcons", old, toolbarSmallIcons);
    }
    
    public void toggleToolbarSmallIcons()
    {
        setToolbarSmallIcons(!toolbarSmallIcons);
    }
    
    public boolean getToolbarShowLabels() 
    {
        return toolbarShowLabels;
    }
    
    public void setToolbarShowLabels(boolean b) 
    {
        boolean old = toolbarShowLabels;
        toolbarShowLabels = b;
        fire("toolbarShowLabels", old, toolbarShowLabels);
    }
    
    public boolean toggleShowLabels() {
        setToolbarShowLabels(!toolbarShowLabels);
        return getToolbarShowLabels();
    }

    public static ChartProperties copyOf(ChartProperties chartProperties) {
        ChartProperties copy = new ChartProperties();
        copy.copyFrom(Objects.requireNonNull(chartProperties, "chartProperties"));
        return copy;
    }
    
    public void copyFrom(ChartProperties source)
    {
        setAxisColor(source.getAxisColor());
        setAxisStrokeIndex(source.getAxisStrokeIndex());
        setAxisLogarithmicFlag(source.getAxisLogarithmicFlag());
        setBarWidth(source.getBarWidth());
        setSlotFillPercent(source.getSlotFillPercent());
        setBarColor(source.getBarColor());
        setBarStrokeIndex(source.getBarStrokeIndex());
        setBarVisibility(source.getBarVisibility());
        setBarDownColor(source.getBarDownColor());
        setBarDownVisibility(source.getBarDownVisibility());
        setBarUpColor(source.getBarUpColor());
        setBarUpVisibility(source.getBarUpVisibility());
        setGridHorizontalColor(source.getGridHorizontalColor());
        setGridHorizontalStrokeIndex(source.getGridHorizontalStrokeIndex());
        setGridHorizontalVisibility(source.getGridHorizontalVisibility());
        setGridVerticalColor(source.getGridVerticalColor());
        setGridVerticalStrokeIndex(source.getGridVerticalStrokeIndex());
        setGridVerticalVisibility(source.getGridVerticalVisibility());
        setBackgroundColor(source.getBackgroundColor());
        setFont(source.getFont());
        setFontColor(source.getFontColor());
        setMarkerVisibility(source.getMarkerVisibility());
        setToolbarVisibility(source.getToolbarVisibility());
        setToolbarSmallIcons(source.getToolbarSmallIcons());
        setToolbarShowLabels(source.getToolbarShowLabels());
        setAnnotationLayerVisible(source.isAnnotationLayerVisible());
    }

    /**
     * @return the annotationLayerVisible
     */
    public boolean isAnnotationLayerVisible() {
        return annotationLayerVisible;
    }
    
    /**
     * @param annotationLayerVisible the annotationLayerVisible to set
     */
    public void setAnnotationLayerVisible(boolean annotationLayerVisible) {
        boolean old = this.annotationLayerVisible;
        this.annotationLayerVisible = annotationLayerVisible;
        fire("annotationLayerVisible", old, this.annotationLayerVisible);
    }
    
}
