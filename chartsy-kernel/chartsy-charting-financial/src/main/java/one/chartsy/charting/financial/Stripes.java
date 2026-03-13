package one.chartsy.charting.financial;

import one.chartsy.charting.Axis;
import one.chartsy.charting.Chart;
import one.chartsy.charting.ChartDecoration;
import one.chartsy.charting.ChartProjector;
import one.chartsy.charting.CoordinateSystem;
import one.chartsy.charting.DataInterval;
import one.chartsy.charting.DataWindow;
import one.chartsy.charting.PlotStyle;
import one.chartsy.charting.Scale;
import one.chartsy.charting.StepsDefinition;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Paint;
import java.awt.Rectangle;

/**
 * A chart decoration that displays stripes for a given scale. This decoration
 * displays a stripe every two steps of the associated scale.
 */
public class Stripes extends ChartDecoration {
  private PlotStyle fillStyle;
  Scale scale;

  /**
   * Initialzes a new <code>Stripes</code> for the specified scale.
   * 
   * @param scale
   *          The considered scale.
   * @param fillPaint
   *          The <code>Paint</code> used to draw the stripes.
   */
  public Stripes(Scale scale, Paint fillPaint) {
    this.scale = scale;
    setFillPaint(fillPaint);
  }

  /**
   * Sets the <code>Paint</code> used to draw the stripes.
   */
  public void setFillPaint(Paint paint) {
    fillStyle = getFillStyle().setFillPaint(paint);
  }

  /**
   * Returns the <code>Paint</code> used to draw the stripes.
   */
  public final Paint getFillPaint() {
    return getFillStyle().getFillPaint();
  }

  private PlotStyle getFillStyle() {
    if (fillStyle == null)
      fillStyle = new PlotStyle(Color.lightGray);
    return fillStyle;
  }

  /**
   * Returns the next stripe.
   * 
   * @param itv
   *          interval of the previous stripe. If <code>null</code>, the method
   *          must compute the interval of the first stripe. The method stores
   *          the result into <i>itv</i> and returns <i>itv</i>.
   * @return The interval of the next stripe.
   */
  protected DataInterval nextStripe(DataInterval itv) {
    StepsDefinition def = scale.getStepsDefinition();
    if (itv == null) {
      itv = getAxis().getVisibleRange();
      double v = def.previousStep(itv.getMin());
      itv.setMin(def.incrementStep(v));
      itv.setMax(def.incrementStep(itv.getMin()));
    } else {
      itv.setMax(def.incrementStep(def.incrementStep(itv.getMax())));
      itv.setMin(def.incrementStep(def.incrementStep(itv.getMin())));
    }

    return itv;
  }

  private final Axis getAxis() {
    return this.scale.getAxis();
  }

  /**
   * Draws the stripes.
   * 
   * @param g
   *          The <code>Graphics</code> context used for drawing.
   */
  @Override
  public void draw(Graphics g) {
    Chart chart = getChart();
    if (chart == null)
      return;

    DataInterval itv = nextStripe(null);
    DataWindow w = null;
    if (getAxis().getType() == Axis.X_AXIS) {
      w = new DataWindow(itv, chart.getYAxis(0).getVisibleRange());
    } else {
      w = new DataWindow(chart.getXAxis().getVisibleRange(), itv);
    }

    ChartProjector prj = getChart().getProjector();
    CoordinateSystem coordSys = getChart().getCoordinateSystem(0);
    Rectangle plotRect = getChart().getChartArea().getPlotRect();

    PlotStyle style = getFillStyle();
    while (itv.getMin() < getAxis().getVisibleMax()) {
      style.fill(g, prj.getShape(w, plotRect, coordSys));
      if (getAxis().getType() == Axis.X_AXIS)
        w.xRange = nextStripe(itv);
      else
        w.yRange = nextStripe(itv);
    }
  }
}

