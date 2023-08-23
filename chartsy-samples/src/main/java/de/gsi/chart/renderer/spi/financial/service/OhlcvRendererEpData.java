/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package de.gsi.chart.renderer.spi.financial.service;

import javafx.scene.canvas.GraphicsContext;

import de.gsi.dataset.DataSet;
import de.gsi.dataset.spi.financial.api.attrs.AttributeModelAware;
import de.gsi.dataset.spi.financial.api.ohlcv.IOhlcvItem;
import de.gsi.dataset.spi.financial.api.ohlcv.IOhlcvItemAware;

/**
 * Domain object for OHLC/V Renderer Extension Points
 */
public class OhlcvRendererEpData {
    public GraphicsContext gc;
    public DataSet ds;
    public AttributeModelAware attrs; // addon (if available)
    public IOhlcvItemAware ohlcvItemAware; // get item by index (if available)
    public IOhlcvItem ohlcvItem; // item domain object (if available)
    public int minIndex; // min index of rendered bar
    public int maxIndex; // max index of rendered bar
    public int index; // index of rendered bar
    public double barWidth; // width of bar
    public double barWidthHalf; // half of bar
    public double xCenter; // x0 center of bar
    public double yOpen; // open in display coords
    public double yHigh; // high in display coords
    public double yLow; // low in display coords
    public double yClose; // close in display coords
    public double yDiff; // diff = open - close
    public double yMin; // minimal y coord of bar
    public Object addon; // addon defined by specific renderer
}
