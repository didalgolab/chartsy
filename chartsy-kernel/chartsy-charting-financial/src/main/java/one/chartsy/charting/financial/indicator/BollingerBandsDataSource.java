package one.chartsy.charting.financial.indicator;

import one.chartsy.charting.data.AbstractDataSource;
import one.chartsy.charting.data.DataSet;

/**
 * A data source used by the <i>Bollinger Bands</i> indicator.
 */
class BollingerBandsDataSource extends AbstractDataSource {
  int period;
  double coeff;

  /**
   * Initializes a new <code>BollingerBandsDataSource</code>.
   * 
   * @param period
   *          The period used to compute the averages.
   * @param coeff
   *          The deviation coefficient.
   */
  public BollingerBandsDataSource(int period, double coeff) {
    this.period = period;
    this.coeff = coeff;
  }

  /**
   * Initializes a new <code>BollingerBandsDataSource</code>.
   * 
   * @param priceDataSet
   *          The price data set.
   * @param period
   *          The period used to compute the averages.
   * @param coeff
   *          The deviation coefficient.
   */
  public BollingerBandsDataSource(DataSet priceDataSet, int period, double coeff) {
    this(period, coeff);
    setPriceDataSet(priceDataSet);
  }

  /**
   * Specifies the price data set.
   */
  public void setPriceDataSet(DataSet priceDataSet) {
    DataSet[] dataSets = null;
    if (priceDataSet != null) {
      MovingAverageDataSet avg = new MovingAverageDataSet(priceDataSet, IndicatorUtil.SMA, period);
      dataSets = new DataSet[] { new BollingerBandsDataSet(avg, -coeff), new BollingerBandsDataSet(avg, coeff) };
    }
    getDataSetList().setDataSets(dataSets);
  }
}

