package one.chartsy.charting.financial.indicator;

import one.chartsy.charting.data.DataSet;

/**
 * Computes the highest value within a given period.
 */
public class HighDataSet extends PeriodDataSet {
  /**
   * Initializes a <code>HighDataSet</code> with the specified period.
   */
  public HighDataSet(DataSet dataSet, int period) {
    super(new DataSet[] { dataSet }, period);
    setMaxDataSetCount(1);
  }

  /**
   * Returns the name of this indicator.
   */
  @Override
  protected String getIndicatorName() {
    return "High(" + getPeriod() + ")";
  }

  /**
   * Computes the indicator data.
   */
  @Override
  protected double[] computeIndicatorData() {
    if (getPeriod() == 0 || getDataSetCount() == 0)
      return null;
    IndicatorData data = IndicatorData.get(getDataSet(0));
    if (data == null)
      return null;
    return IndicatorUtil.computeHigh(data, getPeriod(), true);
  }
}

