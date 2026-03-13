package one.chartsy.charting.financial.indicator;

import one.chartsy.charting.Chart;
import one.chartsy.charting.DisplayPoint;
import one.chartsy.charting.data.DataSet;
import one.chartsy.charting.financial.StockDataSource;

/**
 * Implements the <i>MACD</i> indicator.
 */
public class MACDIndicator extends TechnicalIndicator {
  MACDChartRenderer renderer;
  MACDDataSource macdDS;

  /**
   * Initializes a new <code>MACDIndicator</code>.
   * 
   * @param stockDS
   *          The data source holding the stock data.
   * @param fastPeriod
   *          The fast period of the oscillator.
   * @param slowPeriod
   *          The slow period of the oscillator.
   * @param signalPeriod
   *          The signal period, used to compute the average of the oscillator.
   * @param divergence
   *          A Boolean value indicating whether the difference between the
   *          oscillator and the signal should be displayed.
   */
  public MACDIndicator(StockDataSource stockDS, int fastPeriod, int slowPeriod, int signalPeriod, boolean divergence) {
    super(stockDS);
    macdDS = new MACDDataSource(fastPeriod, slowPeriod, signalPeriod, divergence);
  }

  @Override
  public void attach(Chart chart) {
    super.attach(chart);
    renderer = new MACDChartRenderer(MACD_COLOR, MACD_SIGNAL_COLOR, MACD_DIV_COLOR);
    refresh();
    renderer.setDataSource(macdDS);
    chart.addRenderer(renderer);
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
    macdDS.setPriceDataSet(stockDS.getCloseDataSet());
  }

  @Override
  public DisplayPoint getHighlightedPoint(int dataIdx) {
    return (renderer == null || macdDS.size() == 0) ? null
        : renderer.getDisplayPoint(getMainDataSet(), dataIdx);
  }

  @Override
  public String getName() {
    return "MACD";
  }

  /**
   * Creates an <i>MACD</i> indicator with default parameters.
   */
  public static TechnicalIndicator createMACD(StockDataSource stockDS) {
    return new MACDIndicator(stockDS, 12, 26, 9, true);
  }

  /**
   * Returns the main data set.
   */
  @Override
  public DataSet getMainDataSet() {
    return macdDS.get(1);
  }

}

