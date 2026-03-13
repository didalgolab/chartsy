package one.chartsy.charting.financial.indicator;

import one.chartsy.charting.Chart;
import one.chartsy.charting.ChartRenderer;
import one.chartsy.charting.data.DataSet;
import one.chartsy.charting.renderers.SinglePolylineRenderer;
import one.chartsy.charting.financial.StockDataSource;

/**
 * Implements <i>Moving Average</i> indicators.
 */
public class MovingAverageIndicator extends TechnicalIndicator {
  ChartRenderer renderer;
  PeriodDataSet avgDataSet;

  public MovingAverageIndicator(StockDataSource stockDS, int avgType, int period) {
    super(stockDS);
    avgDataSet = new MovingAverageDataSet(avgType, period);
  }

  @Override
  public void attach(Chart chart) {
    super.attach(chart);
    renderer = new SinglePolylineRenderer();
    refresh();
    chart.addRenderer(renderer, avgDataSet);
  }

  @Override
  public void detach() {
    if (chart != null)
      chart.removeRenderer(renderer);
    renderer = null;
    super.detach();
  }

  @Override
  public void refresh() {
    DataSet closeDataSet = stockDS.getCloseDataSet();
    if (closeDataSet == null)
      avgDataSet.removeDataSet(0);
    else
      avgDataSet.setDataSet(0, stockDS.getCloseDataSet());
  }

  @Override
  public String getName() {
    return avgDataSet.getName();
  }

  @Override
  public DataSet getMainDataSet() {
    return avgDataSet;
  }

  /**
   * Creates an <i>Exponential Moving Average</i> indicator with the specified
   * period.
   */
  public static TechnicalIndicator createEMA(StockDataSource stockDS, int period) {
    return new MovingAverageIndicator(stockDS, IndicatorUtil.EMA, period);
  }

  /**
   * Creates a <i>Simple Moving Average</i> indicator with the specified period.
   */
  public static TechnicalIndicator createSMA(StockDataSource stockDS, int period) {
    return new MovingAverageIndicator(stockDS, IndicatorUtil.SMA, period);
  }
}

