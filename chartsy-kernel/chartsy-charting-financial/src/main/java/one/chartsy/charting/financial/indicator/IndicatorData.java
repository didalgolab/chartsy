package one.chartsy.charting.financial.indicator;

import one.chartsy.charting.data.DataPoints;
import one.chartsy.charting.data.DataSet;
import one.chartsy.charting.financial.StockDataSource;

/**
 * Represents the data used to compute a technical indicator.
 */
public class IndicatorData {
  private static final String HEADER_DATA_KEY = "_HEADER_DATA_";

  private IndicatorData(double[] data, int headerLength, int dataCount) {
    this.data = data;
    this.headerLength = headerLength;
    this.dataCount = dataCount;
  }

  /**
   * The whole data, including the header.
   */
  public double[] data;

  /**
   * The number of values in the header.
   */
  public int headerLength;

  /**
   * The number of values after the header.
   */
  public int dataCount;

  /**
   * Returns the indicator data of the specified data set.
   */
  public static IndicatorData get(DataSet dataSet) {
    if (dataSet instanceof StockDataSource.DataSet) {
      double[] vals = ((StockDataSource.DataSet) dataSet).getAllData();
      int hlen = ((StockDataSource.DataSet) dataSet).getHeaderLength();
      return new IndicatorData(vals, hlen, vals.length - hlen);
    } else {
      DataPoints pts = dataSet.getData();
      if (pts == null)
        return null;
      double[] vals = pts.getYValuesClone();
      return new IndicatorData(vals, 0, vals.length);
    }
  }

  /**
   * Associates the specified header data to a data set.
   */
  public static void setHeaderData(DataSet dataSet, double[] data) {
    dataSet.putProperty(HEADER_DATA_KEY, data, false);
  }
}

