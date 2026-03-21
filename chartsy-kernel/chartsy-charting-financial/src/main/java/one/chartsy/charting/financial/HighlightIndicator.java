package one.chartsy.charting.financial;

import one.chartsy.charting.DisplayPoint;
import one.chartsy.charting.PlotStyle;
import one.chartsy.charting.graphic.DataIndicator;
import one.chartsy.charting.graphic.Marker;
import one.chartsy.charting.util.GraphicUtil;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.geom.Rectangle2D;

/**
 * A chart decoration used to highlight a display point.
 */
public class HighlightIndicator extends DataIndicator {
  private int makerSize = 3;
  private Marker marker;
  // private boolean showingBounds;
  protected DisplayPoint dp = null;
  private PlotStyle markerStyle;
  // private Style boundsStyle;

  /**
   * Initializes a new <code>HighlightIndicator</code>.
   */
  public HighlightIndicator(int axisIdx) {
    super(axisIdx, 0, null);
    setVisible(false);
    marker = Marker.SQUARE;
  }

  public void setHighlightMarker(Marker marker, int size) {
    this.marker = marker;
    this.makerSize = size;
    if (getChart() != null && isVisible())
      getChart().getChartArea().repaint();
  }

  public void setShowingBounds(boolean showing) {
    // this.showingBounds = showing;
  }

  /**
   * Sets the overall color of the indicator.
   */
  public void setColor(Color c) {
    markerStyle = new PlotStyle(Color.black, c);
    // boundsStyle = new Style(c, c);
    setStyle(PlotStyle.createStroked(c));
  }

  /**
   * Sets the highlighted point
   */
  public void setHighlightedPoint(DisplayPoint dp) {
    if (dp == this.dp)
      return;

    DisplayPoint olddp = this.dp;
    if (dp != null) {
      this.dp = dp;
      if (getAxisIndex() == -1)
        setValue(dp.getXData());
      else
        setValue(dp.getYData());
      if (olddp == null)
        setVisible(true);
    } else {
      setVisible(false);
      this.dp = dp;
    }
  }

  /**
   * Draws the indicator into the specified <code>Graphics</code> context.
   */
  @Override
  public void draw(Graphics g) {
    super.draw(g);
    if (dp != null)
      drawMark(g);
  }

  /**
   * Draws the highlight mark.
   */
  protected void drawMark(Graphics g) {
    // if (false && dp.getRenderer().getParent() instanceof
    // HiLoChartRenderer) {
    // Rectangle2D bounds = addMarkBounds(new Rectangle2D.Double());
    // Rectangle rect = GraphicUtil.toRectangle(bounds, null);
    // Style s = (boundsStyle == null) ? getStyle() : boundsStyle;
    // s.renderRect(g, rect.x, rect.y, rect.width, rect.height);
    // } else
    if (marker != null) {
      PlotStyle s = (markerStyle == null) ? getStyle() : markerStyle;
      marker.draw(g, GraphicUtil.toInt(dp.getXCoord()), GraphicUtil.toInt(dp.getYCoord()), makerSize, s);
    }
  }

  /**
   * Returns the bounds of this decoration.
   * 
   * @param retBounds
   *          A rectangle optionally used to store the result. If this parameter
   *          is <code>null</code>, a new <code>Rectangle2D</code> is allocated.
   * @return The bounds of this decoration.
   */
  @Override
  public Rectangle2D getBounds(Rectangle2D retBounds) {
    Rectangle2D bounds = super.getBounds(retBounds);
    if (dp != null) {
      // if (false && dp.getRenderer().getParent() instanceof
      // HiLoChartRenderer) {
      // addMarkBounds(bounds);
      // bounds.setRect(bounds.getX(), bounds.getY(), bounds.getWidth() + 1,
      // bounds.getHeight() + 1);
      // } else
      GraphicUtil.grow(bounds, makerSize, makerSize);
    }
    return bounds;
  }

  // private Rectangle2D addMarkBounds(Rectangle2D bounds) {
  // int idx = dp.getIndex();
  // HiLoChartRenderer renderer = (HiLoChartRenderer)
  // dp.getRenderer().getParent();
  // DataSet ds = renderer.getDataSource().getDataSet(3);
  // ChartRenderer child = renderer.getChild(ds);
  // if (child != null) {
  // Rectangle2D rect = child.getBounds(ds, idx, idx, null, false);
  // GraphicUtil.addToRect(bounds, rect);
  // }
  // // java.util.Iterator iter = renderer.getDataSource().getDataSetIterator();
  // // Rectangle2D rect = null;
  // // while (iter.hasNext()) {
  // // DataSet ds = (DataSet)iter.next();
  // // rect = renderer.getChild(ds).getBounds(ds, idx, idx, rect, false);
  // // GraphicUtil.addToRect(bounds, rect);
  // // }
  // return bounds;
  // }
}

