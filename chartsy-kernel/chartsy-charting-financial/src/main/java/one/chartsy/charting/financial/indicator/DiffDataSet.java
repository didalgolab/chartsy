package one.chartsy.charting.financial.indicator;

import one.chartsy.charting.data.DataSet;

/**
 * A data set class that computes the difference of two data sets.
 */
public class DiffDataSet extends IndicatorDataSet {
  /**
   * Creates a difference data set that computes the difference between the
   * y-values of the two specified data sets.
   */
  public DiffDataSet(DataSet ds1, DataSet ds2) {
    super(new DataSet[] { ds1, ds2 });
    setMaxDataSetCount(2);
  }

  /**
   * Returns the name of this indicator.
   */
  @Override
  protected String getIndicatorName() {
    return null;
  }

  @Override
  protected double[] computeIndicatorData() {
    if (getDataSetCount() != 2)
      return null;

    DataSet ds1 = getDataSet(0);
    DataSet ds2 = getDataSet(1);
    int count = Math.max(ds1.size(), ds2.size());
    double[] res = new double[count];
    double uv = getUndefValue().doubleValue();
    for (int i = 0; i < count; ++i) {
      double v1 = ds1.getYData(i);
      double v2 = ds2.getYData(i);
      if (v1 == uv || v2 == uv)
        res[i] = uv;
      else
        res[i] = v1 - v2;

    }
    return res;
  }
}

