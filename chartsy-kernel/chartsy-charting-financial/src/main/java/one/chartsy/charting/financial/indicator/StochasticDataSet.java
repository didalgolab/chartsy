package one.chartsy.charting.financial.indicator;

import one.chartsy.charting.data.DataSet;

/**
 * A data set used by the <i>Stochastic</i> indicator.
 */
class StochasticDataSet extends PeriodDataSet {
  /**
   * Initializes a <code>StochasticDataSet</code> with the specified period.
   */
  public StochasticDataSet(DataSet[] dataSets, int period) {
    super(dataSets, period);
    setMaxDataSetCount(3);
  }

  /**
   * Returns the name of this indicator.
   */
  @Override
  protected String getIndicatorName() {
    return "%K(" + getPeriod() + ")";
  }

  /**
   * Computes the indicator data.
   * 
   * @return The indicator data.
   */
  @Override
  protected double[] computeIndicatorData() {
    if (getPeriod() == 0 || getDataSetCount() != 3)
      return null;

    IndicatorData hiData = IndicatorData.get(getDataSet(0));
    IndicatorData loData = IndicatorData.get(getDataSet(1));
    IndicatorData closeData = IndicatorData.get(getDataSet(2));
    if (hiData == null || loData == null || closeData == null)
      return null;
    return IndicatorUtil.computeStochastic(hiData, loData, closeData, getPeriod());
  }
}

