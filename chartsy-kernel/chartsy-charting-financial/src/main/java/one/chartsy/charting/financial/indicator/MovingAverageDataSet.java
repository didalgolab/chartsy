package one.chartsy.charting.financial.indicator;

import one.chartsy.charting.data.DataSet;

/**
 * Computes the <i>Simple Moving Average</i> of a data set.
 */
public class MovingAverageDataSet extends PeriodDataSet {
  private final int type;

  /**
   * Creates a moving average.
   * 
   * @param period
   *          The period of the moving average.
   * @param type
   *          The type of the moving average.
   */
  public MovingAverageDataSet(int type, int period) {
    this(null, type, period);
  }

  /**
   * Creates a moving average.
   * 
   * @param dataSet
   *          The referenced data set.
   * @param period
   *          The period of the moving average.
   * @param type
   *          The type of the moving average.
   */
  public MovingAverageDataSet(DataSet dataSet, int type, int period) {
    super(((dataSet == null) ? null : new DataSet[] { dataSet }), period);
    setMaxDataSetCount(1);
    if (type != IndicatorUtil.SMA && type != IndicatorUtil.EMA)
      throw new IllegalArgumentException("Invalid average type: " + type);
    this.type = type;
  }

  /**
   * Returns the name of this indicator.
   */
  @Override
  protected String getIndicatorName() {
    String name = (type == IndicatorUtil.SMA) ? "SMA(" : "EMA(";
    return name + getPeriod() + ")";
  }

  /**
   * Returns the type of the moving average.
   */
  public final int getType() {
    return type;
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
    return (type == IndicatorUtil.SMA) ? IndicatorUtil.computeSMA(data, getPeriod())
        : IndicatorUtil.computeEMA(data, getPeriod());
  }
}

