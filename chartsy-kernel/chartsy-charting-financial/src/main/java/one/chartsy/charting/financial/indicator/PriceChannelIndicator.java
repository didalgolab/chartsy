package one.chartsy.charting.financial.indicator;

import one.chartsy.charting.Chart;
import one.chartsy.charting.ChartRenderer;
import one.chartsy.charting.PlotStyle;
import one.chartsy.charting.data.DataSet;
import one.chartsy.charting.renderers.PolylineChartRenderer;
import one.chartsy.charting.financial.StockDataSource;

/**
 * Implements the <i>Price Channel</i> indicator.
 */
public class PriceChannelIndicator extends TechnicalIndicator {
  ChartRenderer renderer;
  PriceChannelDataSource dataSource;

  /** Initializes a new <code>PriceChannelIndicator</code>. */
  public PriceChannelIndicator(StockDataSource stockDS) {
    this(stockDS, 20);
  }

  /** Initializes a new <code>PriceChannelIndicator</code>. */
  public PriceChannelIndicator(StockDataSource stockDS, int period) {
    super(stockDS);
    dataSource = new PriceChannelDataSource(period);
  }

  @Override
  public void attach(Chart chart) {
    super.attach(chart);
    renderer = new PolylineChartRenderer();
    renderer.setDataSource(dataSource);
    refresh();
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
    DataSet highDataSet = stockDS.getHighDataSet();
    DataSet lowDataSet = stockDS.getLowDataSet();

    DataSet[] dataSets = null;
    if (highDataSet != null && lowDataSet != null)
      dataSets = new DataSet[] { highDataSet, lowDataSet };

    dataSource.setHighLowDataSets(dataSets);

    if (renderer.getChildCount() == 2) {
      PlotStyle style = PlotStyle.createStroked(PRICE_CHANNEL_COLOR);
      renderer.setStyles(new PlotStyle[] { style, style });
      // TODO
      //renderer.getChild(0).setVisibleInLegend(false);
    }
  }

  @Override
  public String getName() {
    return "Price Channel";
  }
}

