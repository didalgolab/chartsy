/* Copyright 2016 by Mariusz Bernacki. PROPRIETARY and CONFIDENTIAL content.
 * Unauthorized copying of this file, via any medium is strictly prohibited.
 * See the file "LICENSE.txt" for the full license governing this code. */
package one.chartsy.ui.chart;

import one.chartsy.core.Range;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;

public interface Plot {
    
    Color getPrimaryColor();
    
    void paint(Graphics2D g, ChartContext cf, Range range, Rectangle bounds);
    
}
