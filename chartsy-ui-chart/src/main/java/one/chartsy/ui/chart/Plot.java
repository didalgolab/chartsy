/* Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.ui.chart;

import one.chartsy.core.Range;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;

public interface Plot {
    
    Color getPrimaryColor();
    
    void paint(Graphics2D g, ChartContext cf, Range range, Rectangle bounds);
    
}
