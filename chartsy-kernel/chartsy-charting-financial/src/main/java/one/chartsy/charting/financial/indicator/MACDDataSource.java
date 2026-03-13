package one.chartsy.charting.financial.indicator;

import one.chartsy.charting.data.AbstractDataSource;
import one.chartsy.charting.data.DataSet;

/**
 * A data source used by the <i>MACD</i> indicator.
 */
class MACDDataSource extends AbstractDataSource {
  int fastPeriod;
  int slowPeriod;
  int signalPeriod;
  boolean divergence;

  /**
   * Initializes a new <code>MACDDataSource</code>.
   * 
   * @param fastPeriod
   *          The fast period of the oscillator.
   * @param slowPeriod
   *          The slow period of the oscillator.
   * @param signalPeriod
   *          The signal period, used to compute the average of the oscillator.
   * @param divergence
   *          A Boolean value indicating whether the difference between the
   *          oscillator and the signal should be computed.
   */
  public MACDDataSource(int fastPeriod, int slowPeriod, int signalPeriod, boolean divergence) {
    this.fastPeriod = fastPeriod;
    this.slowPeriod = slowPeriod;
    this.signalPeriod = signalPeriod;
    this.divergence = divergence;
  }

  /**
   * Specifies the price data set.
   */
  public void setPriceDataSet(DataSet priceDataSet) {
    DataSet[] dataSets = null;
    if (priceDataSet != null) {
      int count = divergence ? 3 : 2;
      dataSets = new DataSet[count];
      IndicatorDataSet macd = new OSCDataSet(priceDataSet, fastPeriod, slowPeriod);
      macd.setName("MACD(" + fastPeriod + "," + slowPeriod + ")");
      IndicatorDataSet signal = new MovingAverageDataSet(macd, IndicatorUtil.EMA, signalPeriod);
      signal.setName("MACD EMA(" + signalPeriod + ")");
      dataSets[--count] = signal;
      dataSets[--count] = macd;
      if (divergence) {
        IndicatorDataSet div = new DiffDataSet(macd, signal);
        div.setName("Divergence");
        dataSets[--count] = div;
      }

    }
    getDataSetList().setDataSets(dataSets);
  }
}

