/* Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.ui.chart.axis;

import java.awt.*;
import java.text.DecimalFormat;

import one.chartsy.ui.chart.ChartContext;
import one.chartsy.ui.chart.ChartData;

/**
 *
 * @author Mariusz Bernacki
 */
public class PriceAxisMarker {
    /** The decimal format used to display prices in this axis marker. */
    private final DecimalFormat decimalFormat = new DecimalFormat("#,##0.00");
    /** The precise decimal format used to display prices in range (-10, 10). */
    private final DecimalFormat preciseDecimalFormat = new DecimalFormat("#,##0.0000");
    /** The singleton instance of this marker. */
    private static final PriceAxisMarker instance = new PriceAxisMarker();
    
    
    private PriceAxisMarker() {
        // can't instantiate
    }
    
    public static PriceAxisMarker getInstance() {
        return instance;
    }
    
    protected synchronized String formatValue(double value) {
        DecimalFormat format = this.preciseDecimalFormat;
        if (value > 9.9999 || value < -9.9999)
            format = this.decimalFormat;
        return format.format(value);
    }
    
    public void paint(Graphics2D g, ChartContext cf, double value, Color color, double y) {
        Insets dataOffset = ChartData.dataOffset;
        FontMetrics fm = g.getFontMetrics();
        
        g.setPaint(color);
        double x = 3;
        double w = dataOffset.right;
        double h = fm.getAscent() + 2;
        
        g.fillRect((int) x, (int)(y - h/2), (int)w - 5, (int)h);
        
        int sat = (color.getRed() + color.getGreen() + color.getBlue()) / 3;
        g.setPaint((sat < 128)? Color.white : Color.black);
        g.drawString(formatValue(value), (int) (x + 7), (int)(y - h/2 + h - 3));
    }
}
