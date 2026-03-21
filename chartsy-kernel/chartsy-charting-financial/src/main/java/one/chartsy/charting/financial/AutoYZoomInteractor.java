package one.chartsy.charting.financial;

import one.chartsy.charting.DataWindow;
import one.chartsy.charting.interactors.ChartZoomInteractor;

/**
 * A zoom interactor that automatically computes the y-range of the zoomed area.
 * 
 * @see StockUtil#getYDataRange
 */
public class AutoYZoomInteractor extends ChartZoomInteractor {

  /**
   * Initializes a new <code>AutoYZoomInteractor</code>. By default, unzooming
   * operations are disabled.
   */
  public AutoYZoomInteractor() {
    setZoomOutAllowed(false);
    setYZoomAllowed(false);
  }

  /**
   * Performs the zoom. The y-range is automatically computed according to the
   * data displayed within the specied x-range.
   */
  @Override
  protected void doIt() {
    DataWindow w = getZoomedDataWindow();
    if (w.xRange.getLength() < StockUtil.MIN_XLEN) {
      double mid = (w.getXMax() + w.getXMin()) / 2;
      w.xRange.set(mid - StockUtil.MIN_XLEN / 2, mid + StockUtil.MIN_XLEN / 2);
    }
    StockUtil.performAnimatedZoom(getChart(), getYAxisIndex(), w.xRange, getAnimationStep());
  }
}

