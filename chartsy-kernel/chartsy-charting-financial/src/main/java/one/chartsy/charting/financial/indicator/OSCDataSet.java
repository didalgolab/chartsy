package one.chartsy.charting.financial.indicator;

import one.chartsy.charting.data.DataSet;
import one.chartsy.charting.event.DataSetContentsEvent;

/**
 * OSC data set.
 */
public class OSCDataSet extends IndicatorDataSet {
  static final int DEFAULT_PERIOD = 10;

  private int fastPeriod = 0;
  private int slowPeriod = 0;

  /**
   * Creates a OSC data set with the specified periods.
   */
  public OSCDataSet(DataSet dataSet, int fastPeriod, int slowPeriod) {
    super(new DataSet[] { dataSet });
    setMaxDataSetCount(1);
    setPeriods(fastPeriod, slowPeriod);
  }

  /**
   * Returns the name of this indicator.
   */
  @Override
  protected String getIndicatorName() {
    return "OSC(" + fastPeriod + "," + fastPeriod + ")";
  }

  /**
   * Returns the fast period of the oscillator.
   * 
   * @see #setPeriods
   */
  public int getFastPeriod() {
    return this.fastPeriod;
  }

  /**
   * Returns the slow period of the oscillator.
   * 
   * @see #setPeriods
   */
  public int getSlowPeriod() {
    return this.slowPeriod;
  }

  /**
   * Sets the periods of the oscillator.
   * 
   * @see #getFastPeriod
   * @see #getSlowPeriod
   */
  public void setPeriods(int fastPeriod, int slowPeriod) {
    if (slowPeriod <= 0 || fastPeriod <= 0 || (fastPeriod > slowPeriod))
      throw new IllegalArgumentException("Invalid periods");
    this.fastPeriod = fastPeriod;
    this.slowPeriod = slowPeriod;
    updateIndicatorData();
    fireDataSetContentsEvent(new DataSetContentsEvent(this));
  }

  public DataSet getMainDataSet() {
    return getDataSet(0);
  }

  // --------------------------------------------------------------------------
  @Override
  protected double[] computeIndicatorData() {
    if (fastPeriod == 0 || slowPeriod == 0 || getDataSetCount() == 0)
      return null;

    IndicatorData data = IndicatorData.get(getDataSet(0));
    if (data == null)
      return null;
    double[] res = IndicatorUtil.computeOscillator(data, fastPeriod, slowPeriod, IndicatorUtil.EMA);
    return res;
  }

}

