package one.chartsy.charting.financial.indicator;

import one.chartsy.charting.data.AbstractDataSource;
import one.chartsy.charting.data.DataSet;

/**
 * A data source used by the <i>Stochastic</i> indicator.
 */
class StochasticDataSource extends AbstractDataSource {
  int period1;
  int period2;
  int period3;

  /**
   * Creates a <code>StochasticDataSource</code>.
   * 
   * @param period1
   *          The period used to compute the <i>%K oscillator</i>.
   * @param period2
   *          The average period of the <i>%K oscillator</i>.
   * @param period2
   *          The average period of the <i>%D oscillator</i>.
   */
  public StochasticDataSource(int period1, int period2, int period3) {
    this.period1 = period1;
    this.period2 = period2;
    this.period3 = period3;
  }

  /**
   * Creates a <code>StochasticDataSource</code>.
   * 
   * @param dataSets
   *          The high, low, and close data sets.
   * @param period1
   *          The period used to compute the <i>%K oscillator</i>.
   * @param period2
   *          The average period of the <i>%K oscillator</i>.
   * @param period2
   *          The average period of the <i>%D oscillator</i>.
   * @exception IllegalArgumentException
   *              If the array of data sets does not contain three elements.
   */
  public StochasticDataSource(DataSet[] dataSets, int period1, int period2, int period3) {
    this(period1, period2, period3);
    setHLCDataSets(dataSets);
  }

  /**
   * Sets the high, low, and close data sets.
   * 
   * @param dataSets
   *          The high, low, and close data sets.
   * @exception IllegalArgumentException
   *              If the array of data sets does not contain three elements.
   */
  public void setHLCDataSets(DataSet[] dataSets) {
    if (dataSets == null) {
      getDataSetList().setDataSets(null);
      return;
    }
    if (dataSets.length != 3)
      throw new IllegalArgumentException("StochasticDataSource needs 3 data sets");
    IndicatorDataSet kDataSet = new StochasticDataSet(dataSets, period1);
    IndicatorDataSet kds = kDataSet;
    if (period2 > 1) {
      kds = new MovingAverageDataSet(kDataSet, IndicatorUtil.SMA, period2);
      kds.setName(kDataSet.getName());
    }
    IndicatorDataSet dds = new MovingAverageDataSet(kds, IndicatorUtil.SMA, period3);
    dds.setName("%D(" + period3 + ")");
    getDataSetList().setDataSets(new DataSet[] { kds, dds });
  }
}

