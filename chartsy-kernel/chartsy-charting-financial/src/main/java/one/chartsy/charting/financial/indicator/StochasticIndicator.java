package one.chartsy.charting.financial.indicator;

import java.awt.BasicStroke;

import one.chartsy.charting.Chart;
import one.chartsy.charting.ChartRenderer;
import one.chartsy.charting.DisplayPoint;
import one.chartsy.charting.PlotStyle;
import one.chartsy.charting.data.DataSet;
import one.chartsy.charting.graphic.DataIndicator;
import one.chartsy.charting.renderers.PolylineChartRenderer;
import one.chartsy.charting.financial.StockDataSource;

/**
 * Implements the <i>Stochastic</i> indicator.
 */
public class StochasticIndicator extends TechnicalIndicator {
  ChartRenderer renderer;
  StochasticDataSource stoDS;
  String desc;
  DataIndicator[] indicators;

  /**
   * Creates a <code>StochasticDataSource</code>.
   * 
   * @param stockDS
   *          The stock data source.
   * @param period1
   *          The period used to compute the <i>%K oscillator</i>.
   * @param period2
   *          The average period of the <i>%K oscillator</i>.
   * @param period2
   *          The average period of the <i>%D oscillator</i>.
   * @param desc
   *          The description of the stochastic indicator.
   */
  public StochasticIndicator(StockDataSource stockDS, int period1, int period2, int period3, String desc) {
    super(stockDS);
    stoDS = new StochasticDataSource(period1, period2, period3);
    this.desc = desc;
  }

  @Override
  public void attach(Chart chart) {
    super.attach(chart);
    renderer = new PolylineChartRenderer();
    renderer.setDataSource(stoDS);
    BasicStroke stroke = new BasicStroke(1.f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 10.f, new float[] { 4.f },
        .0f);
    PlotStyle style1 = new PlotStyle(stroke, STOCHASTIC_INDIC_COLOR);
    PlotStyle style2 = new PlotStyle(PlotStyle.DEFAULT_STROKE, STOCHASTIC_INDIC_COLOR);

    indicators = new DataIndicator[3];
    indicators[0] = new DataIndicator(0, 20, null);
    indicators[0].setStyle(style2);
    indicators[1] = new DataIndicator(0, 50, null);
    indicators[1].setStyle(style1);
    indicators[2] = new DataIndicator(0, 80, null);
    indicators[2].setStyle(style2);
    for (int i = 0; i < indicators.length; ++i) {
      indicators[i].setDrawOrder(1);
      chart.addDecoration(indicators[i]);
    }
    refresh();
    chart.addRenderer(renderer);
  }

  @Override
  public void detach() {
    if (chart != null) {
      chart.removeRenderer(renderer);
      for (int i = 0; i < indicators.length; ++i) {
        chart.removeDecoration(indicators[i]);
      }
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

    stoDS.setHLCDataSets(dataSets);
    if (renderer.getChildCount() == 2) {
      renderer.setStyles(new PlotStyle[] { new PlotStyle(2.f, STOCHASTIC_COLOR1), new PlotStyle(1.f, STOCHASTIC_COLOR2) });
    }
  }

  @Override
  public DisplayPoint getHighlightedPoint(int dataIdx) {
    return (renderer == null || stoDS.size() == 0) ? null
        : renderer.getDisplayPoint(getMainDataSet(), dataIdx);
  }

  @Override
  public DataSet getMainDataSet() {
    return stoDS.size() == 0 ? null : stoDS.get(0);
  }

  @Override
  public String getName() {
    return (desc == null) ? "Stochastic" : desc;
  }

  /**
   * Creates a predefined <i>Fast Stochastic</i> indicator.
   */
  public static StochasticIndicator createFastStochastic(StockDataSource stockDS) {
    return new StochasticIndicator(stockDS, 14, 1, 3, "Fast Stochastic");
  }

  /**
   * Creates a predefined <i>Slow Stochastic</i> indicator.
   */
  public static StochasticIndicator createSlowStochastic(StockDataSource stockDS) {
    return new StochasticIndicator(stockDS, 14, 3, 3, "Slow Stochastic");
  }
}

