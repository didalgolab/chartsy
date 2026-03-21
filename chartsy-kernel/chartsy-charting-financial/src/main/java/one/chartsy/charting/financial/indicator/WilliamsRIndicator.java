package one.chartsy.charting.financial.indicator;

import one.chartsy.charting.Chart;
import one.chartsy.charting.ChartRenderer;
import one.chartsy.charting.DisplayPoint;
import one.chartsy.charting.PlotStyle;
import one.chartsy.charting.data.DataSet;
import one.chartsy.charting.graphic.DataIndicator;
import one.chartsy.charting.renderers.SinglePolylineRenderer;
import one.chartsy.charting.financial.StockDataSource;

/**
 * Implements the <i>Stochastic</i> indicator.
 */
public class WilliamsRIndicator extends TechnicalIndicator {
  ChartRenderer renderer;
  WilliamsRDataSet wDataSet;
  DataIndicator indicator;

  /**
   * Creates a <code>WilliamsRIndicator</code>.
   * 
   * @param stockDS
   *          The stock data source.
   * @param period
   *          The period used to compute the <i>%R momentum</i>.
   */
  public WilliamsRIndicator(StockDataSource stockDS, int period) {
    super(stockDS);
    wDataSet = new WilliamsRDataSet(null, period);
  }

  @Override
  public void attach(Chart chart) {
    super.attach(chart);
    PlotStyle style = PlotStyle.createStroked(WILLIAMSR_COLOR);
    renderer = new SinglePolylineRenderer(style);
    indicator = new DataIndicator(0, -50, null);
    indicator.setStyle(new PlotStyle(2.f, WILLIAMSR_INDIC_COLOR));
    chart.addDecoration(indicator);
    refresh();
    chart.addRenderer(renderer);
  }

  @Override
  public void detach() {
    if (chart != null) {
      chart.removeRenderer(renderer);
      chart.removeDecoration(indicator);
    }
    renderer = null;
    super.detach();
  }

  @Override
  public void refresh() {
    DataSet hids = stockDS.getHighDataSet();
    DataSet lods = stockDS.getLowDataSet();
    DataSet closeds = stockDS.getCloseDataSet();

    DataSet[] dataSets = null;
    if (hids != null && lods != null && closeds != null)
      dataSets = new DataSet[] { hids, lods, closeds };

    wDataSet.setDataSets(dataSets);

    if (dataSets == null)
      renderer.getDataSource().setAll(null);
    else if (!renderer.getDataSource().contains(wDataSet))
      renderer.getDataSource().add(wDataSet);

  }

  @Override
  public String getName() {
    return "Williams %R";
  }

  @Override
  public DisplayPoint getHighlightedPoint(int dataIdx) {
    return (renderer == null || wDataSet.getDataSetCount() == 0) ? null : renderer.getDisplayPoint(wDataSet, dataIdx);
  }

  @Override
  public DataSet getMainDataSet() {
    return wDataSet;
  }
}

