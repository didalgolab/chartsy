package one.chartsy.charting.financial.indicator;

import one.chartsy.charting.data.DataSet;

/**
 * A data set used by the <i>Relative Strength Index</i> indicator.
 */
class RSIDataSet extends PeriodDataSet {
  /**
   * Creates a <code>RSIDataSet</code> with the specified period.
   */
  public RSIDataSet(int period) {
    this(null, period);
  }

  /**
   * Creates a <code>RSIDataSet</code> with the specified period.
   */
  public RSIDataSet(DataSet dataSet, int period) {
    super((dataSet == null) ? null : new DataSet[] { dataSet }, period);
    setMaxDataSetCount(1);
  }

  /**
   * Returns the name of this indicator.
   */
  @Override
  protected String getIndicatorName() {
    return "RSI(" + getPeriod() + ")";
  }

  /**
   * Computes the indicator data.
   * 
   * @return The indicator data.
   */
  @Override
  protected double[] computeIndicatorData() {
    if (getPeriod() == 0 || getDataSetCount() == 0)
      return null;

    IndicatorData data = IndicatorData.get(getDataSet(0));
    if (data == null)
      return null;
    return IndicatorUtil.computeRSI(data, getPeriod());
  }
}

