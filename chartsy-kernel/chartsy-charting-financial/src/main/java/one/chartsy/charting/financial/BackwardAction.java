package one.chartsy.charting.financial;

import one.chartsy.charting.DataInterval;
import one.chartsy.charting.action.ChartAction;

import java.awt.event.ActionEvent;

/**
 * An <code>ChartAction</code> subclass associated with an
 * <code>AxisZoomHistory</code> object.
 */
class BackwardAction extends ChartAction implements HistoryListener {
  private final AxisZoomHistory history;

  /**
   * Creates a new <code>BackwardAction</code> object associated with the
   * specified <code>AxisZoomHistory</code>.
   */
  public BackwardAction(AxisZoomHistory history) {
    super("Previous Zoom", null, null, "Previous Zoom", null);
    setIcon(StockDemo.class, "back.gif");
    this.history = history;
    history.addHistoryListener(this);
  }

  /**
   * Computes the enable state of the action.
   */
  @Override
  protected void computeEnabled() {
    super.computeEnabled();
    if (isEnabled() && !history.hasPrevious()) {
      setEnabled(false);
    }
  }

  /**
   * Handles events.
   */
  @Override
  public void actionPerformed(ActionEvent evt) {
    if (!isEnabled())
      return;
    // Get the previous data interval available in the history.
    DataInterval itv = history.previous();
    StockUtil.performAnimatedZoom(getChart(), 0, itv, StockUtil.ANIMATION_STEPS);
  }

  /**
   * Called when navigation occurred in the history.
   */
  @Override
  public void navigationPerformed() {
    computeEnabled();
  }
}

