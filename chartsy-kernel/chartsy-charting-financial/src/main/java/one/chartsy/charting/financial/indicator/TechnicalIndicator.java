package one.chartsy.charting.financial.indicator;

import one.chartsy.charting.Chart;
import one.chartsy.charting.DisplayPoint;
import one.chartsy.charting.data.DataSet;
import one.chartsy.charting.financial.StockColors;
import one.chartsy.charting.financial.StockDataSource;

/**
 * Represents a technical indicator of stock data.
 */
public abstract class TechnicalIndicator implements StockColors {
  /** The data source holding the stock data. */
  protected StockDataSource stockDS;

  /** The chart displaying the indicator. */
  protected Chart chart;

  /**
   * Initializes a new <code>TechnicalIndicator</code> for the specified data
   * source.
   */
  public TechnicalIndicator(StockDataSource stockDS) {
    this.stockDS = stockDS;
  }

  /**
   * Attaches the indicator to the specified chart.
   * 
   * @exception UnsupportedOperationException
   *              if the indicator is already displayed by a chart.
   */
  public void attach(Chart chart) {
    if (this.chart != null)
      throw new UnsupportedOperationException("Indicator already attached to a chart");
    this.chart = chart;

  }

  /**
   * Detaches the indicator from the specified chart.
   */
  public void detach() {
    this.chart = null;
  }

  /**
   * Invoked to refresh the indicator data.
   */
  public void refresh() {
  }

  /**
   * Returns the highlighted point corresponding to the specified data index.
   * The default implementation returns <code>null</code>.
   */
  public DisplayPoint getHighlightedPoint(int dataIdx) {
    return null;
  }

  /**
   * Returns the name of this indicator.
   */
  public abstract String getName();

  /**
   * Returns the main data set or null if not applicable.
   */
  public DataSet getMainDataSet() {
    return null;
  }
}

