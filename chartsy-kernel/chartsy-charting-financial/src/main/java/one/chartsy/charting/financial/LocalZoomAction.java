package one.chartsy.charting.financial;

import one.chartsy.charting.DataInterval;
import one.chartsy.charting.LocalZoomAxisTransformer;
import one.chartsy.charting.action.ChartAction;

import java.awt.event.ActionEvent;

/**
 * An <code>ChartAction</code> subclass to modify the zoom factor of an
 * <code>LocalZoomAxisTransformer</code> instance set on the x-axis of the
 * associated chart.
 */
class LocalZoomAction extends ChartAction {
  private final boolean zoomOut;
  private final double coeff;
  private static final String zoomInText = "Increase the lens zoom factor";
  private static final String zoomOutText = "Decrease the lens zoom factor";

  public LocalZoomAction(double coeff, boolean zoomOut) {
    super(zoomOut ? zoomOutText : zoomInText, null, null, zoomOut ? zoomOutText : zoomInText, null);
    setIcon(StockDemo.class, zoomOut ? "lzoomout.gif" : "lzoomin.gif");
    this.coeff = zoomOut ? 1.d / coeff : coeff;
    this.zoomOut = zoomOut;
  }

  /**
   * Handles events.
   */
  @Override
  public void actionPerformed(ActionEvent evt) {
    if (!isEnabled())
      return;
    LocalZoomAxisTransformer t = getTransformer();
    t.setZoomFactor(coeff * t.getZoomFactor());
  }

  /**
   * Computes the enable state of the action.
   */
  @Override
  protected void computeEnabled() {
    super.computeEnabled();
    if (isEnabled()) {
      LocalZoomAxisTransformer t = getTransformer();
      if (t == null)
        setEnabled(false);
      else if (!zoomOut) {
        DataInterval zRange = t.getTransformedRange();
        if (zRange.contains(getChart().getXAxis().getVisibleRange()))
          setEnabled(false);
      } else if (t.getZoomFactor() * coeff < 1) {
        setEnabled(false);
      }
    }
  }

  private LocalZoomAxisTransformer getTransformer() {
    return (LocalZoomAxisTransformer) getChart().getXAxis().getTransformer();
  }
}

