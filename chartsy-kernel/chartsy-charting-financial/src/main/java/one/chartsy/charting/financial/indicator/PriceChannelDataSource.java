package one.chartsy.charting.financial.indicator;

import one.chartsy.charting.data.AbstractDataSource;
import one.chartsy.charting.data.DataSet;

/**
 * A data source used by the <i>Price Channel</i> indicator.
 */
class PriceChannelDataSource extends AbstractDataSource {
  private final int period;

  /** Creates new PriceChannelDataSource. */
  public PriceChannelDataSource(int period) {
    this.period = period;
  }

  /** Creates new PriceChannelDataSource. */
  public PriceChannelDataSource(DataSet highDataSet, DataSet lowDataSet, int period) {
    this(period);
    setHighLowDataSets(new DataSet[] { highDataSet, lowDataSet });
  }

  /**
   * Sets the high/low data sets.
   */
  public void setHighLowDataSets(DataSet[] dataSets) {
    if (dataSets == null) {
      getDataSetList().setDataSets(null);
    } else {
      PeriodDataSet ds1 = new LowDataSet(dataSets[1], period);
      ds1.setName("Price Channel(" + period + ")");
      PeriodDataSet ds2 = new HighDataSet(dataSets[0], period);
      ds2.setName("Price Channel(" + period + ")");
      getDataSetList().setDataSets(new DataSet[] { ds1, ds2 });
    }
  }

}

