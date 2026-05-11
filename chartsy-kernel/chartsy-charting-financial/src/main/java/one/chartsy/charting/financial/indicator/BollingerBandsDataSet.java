package one.chartsy.charting.financial.indicator;

import one.chartsy.charting.data.DataSet;

/**
 * A data set used by the <i>Bollinger Bands</i> indicator.
 */
class BollingerBandsDataSet extends IndicatorDataSet {
  private final double coeff;

  public BollingerBandsDataSet(MovingAverageDataSet avg, double coeff) {
    super(new DataSet[] { avg }, false);
    setMaxDataSetCount(1);
    this.coeff = coeff;
    updateIndicatorData();
  }

  @Override
  protected String getIndicatorName() {
    if (getDataSetCount() == 0)
      return null;
    MovingAverageDataSet avgDataSet = (MovingAverageDataSet) getDataSet(0);
    return "Bollinger Bands (" + avgDataSet.getPeriod() + "," + coeff + ")";
  }

  @Override
  protected double[] computeIndicatorData() {
    if (getDataSetCount() == 0)
      return null;

    MovingAverageDataSet avgDataSet = (MovingAverageDataSet) getDataSet(0);
    DataSet dataSet = avgDataSet.getDataSet(0);

    if (dataSet == null)
      return null;

    IndicatorData data = IndicatorData.get(dataSet);
    if (data == null)
      return null;

    double[] stdDev = IndicatorUtil.computeStdDev(data, avgDataSet.getPeriod());

    int count = stdDev.length;
    double[] res = new double[count];
    double uv = getUndefValue().doubleValue();
    for (int i = 0; i < count; ++i) {
      double v = avgDataSet.getYData(i);
      if (v == uv || stdDev[i] == uv)
        res[i] = uv;
      else
        res[i] = v + coeff * stdDev[i];
    }
    return res;
  }
}

