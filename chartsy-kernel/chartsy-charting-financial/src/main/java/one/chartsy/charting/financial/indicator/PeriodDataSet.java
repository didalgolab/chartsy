package one.chartsy.charting.financial.indicator;

import one.chartsy.charting.data.DataSet;
import one.chartsy.charting.event.DataSetContentsEvent;

/**
 * A base class for indicator data sets that are computed according to a given
 * period.
 */
public abstract class PeriodDataSet extends IndicatorDataSet {
  private int period = 0;

  /**
   * Creates a <code>PeriodDataSet</code> with the specified period.
   */
  public PeriodDataSet(int period) {
    this(null, period);
  }

  /**
   * Creates a <code>PeriodDataSet</code> with the specified period.
   */
  public PeriodDataSet(DataSet[] dataSets, int period) {
    super(dataSets, false);
    setPeriod(period);
  }

  /**
   * Returns the period over which the indicator is computed.
   * 
   * @see #setPeriod
   */
  public int getPeriod() {
    return this.period;
  }

  /**
   * Sets the period over which the indicator is computed.
   * 
   * @see #getPeriod
   */
  public void setPeriod(int period) {
    if (period <= 0)
      throw new IllegalArgumentException("Period must be strictly positive");
    this.period = period;
    updateIndicatorData();
    fireDataSetContentsEvent(new DataSetContentsEvent(this)); // Full update
  }
}

