package one.chartsy.charting.financial.indicator;

import one.chartsy.charting.data.DataSet;

/**
 * Computes the lowest value within a given period.
 */
public class LowDataSet extends PeriodDataSet {
  /**
   * Initializes a <code>LowDataSet</code> with the specified period.
   */
  public LowDataSet(DataSet dataSet, int period) {
    super(new DataSet[] { dataSet }, period);
    setMaxDataSetCount(1);
  }

  /**
   * Returns the name of this indicator.
   */
  @Override
  protected String getIndicatorName() {
    return "Low(" + getPeriod() + ")";
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
    return IndicatorUtil.computeLow(data, getPeriod(), true);
  }
}

