package one.chartsy.charting.financial;

import one.chartsy.charting.Chart;
import one.chartsy.charting.LabelRenderer;
import one.chartsy.charting.event.ChartDrawEvent;
import one.chartsy.charting.event.ChartDrawListener;

import java.awt.Graphics;
import java.awt.Rectangle;

/**
 * Draws a message at the center of the plotting area.
 */
class ChartMessage implements ChartDrawListener {

  private final LabelRenderer label;
  private final String text;

  public ChartMessage(String text) {
    this(text, new LabelRenderer());
  }

  public ChartMessage(String text, LabelRenderer label) {
    this.text = text;
    this.label = label;
  }

  @Override
  public void beforeDraw(ChartDrawEvent evt) {
  }

  @Override
  public void afterDraw(ChartDrawEvent evt) {
    Graphics g = evt.getGraphics();
    Chart.Area area = evt.getChart().getChartArea();
    Rectangle r = area.getPlotRect();
    int x = (r.x + r.width) / 2;
    int y = (r.y + r.height) / 2;
    Rectangle clipBounds = g.getClipBounds();
    if (clipBounds == null || label.getBounds(area, x, y, text, null).intersects(clipBounds))
      label.paintLabel(area, g, text, x, y);
  }
}

