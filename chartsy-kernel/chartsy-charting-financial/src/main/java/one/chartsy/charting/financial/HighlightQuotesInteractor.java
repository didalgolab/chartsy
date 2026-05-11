package one.chartsy.charting.financial;

import one.chartsy.charting.ChartDataPicker;
import one.chartsy.charting.ChartInteractor;
import one.chartsy.charting.ChartRenderer;
import one.chartsy.charting.DefaultChartDataPicker;
import one.chartsy.charting.DisplayPoint;
import one.chartsy.charting.interactors.ChartHighlightInteractor;

import java.awt.Rectangle;
import java.awt.event.MouseEvent;

/**
 * An interactor that highlight a data point using a data indicator.
 */
public class HighlightQuotesInteractor extends ChartHighlightInteractor {
  // private ChartRenderer[] targets;

  /** Initializes a new <code>HighlightQuotesInteractor</code>. */
  public HighlightQuotesInteractor() {
  }

  /**
   * Indicates whether the specified renderer must be considered for
   * highlighting. The default implementation always returns <code>true</code>.
   */
  protected boolean isTarget(ChartRenderer r) {
    return true;
  }

  /**
   * Creates a new <code>ChartDataPicker</code> object.
   */
  @Override
  protected ChartDataPicker createDataPicker(MouseEvent evt) {
    return new DefaultChartDataPicker(evt.getX(), evt.getY(), Integer.MAX_VALUE) {
      @Override
      public double computeDistance(double x1, double y1, double x2, double y2) {
        return Math.abs(x2 - x1) + Math.log(Math.abs(y2 - y1) + .1);
      }

      @Override
      public boolean accept(ChartRenderer renderer) {
        return isTarget(renderer);
      }
    };
  }

  /**
   * Returns a display point depending on the given data picker and the current
   * picking mode.
   */
  @Override
  protected DisplayPoint pickData(ChartDataPicker picker) {
    Rectangle plotRect = getChart().getChartArea().getPlotRect();
    if (plotRect.contains(picker.getPickX(), picker.getPickY()))
      return super.pickData(picker);
    else
      return null;
  }

  /**
   * Called when the given interactor is about to start an interaction on the
   * given event.
   */
  @Override
  public void interactionStarted(ChartInteractor inter, MouseEvent evt) {
    DisplayPoint dp = getHighlightedPoint();
    if (dp != null)
      publishHighlightChange(dp, false, evt);

  }
}

