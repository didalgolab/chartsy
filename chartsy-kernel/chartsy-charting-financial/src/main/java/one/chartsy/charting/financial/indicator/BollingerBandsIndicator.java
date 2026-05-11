package one.chartsy.charting.financial.indicator;

import one.chartsy.charting.Chart;
import one.chartsy.charting.ChartRenderer;
import one.chartsy.charting.PlotStyle;
import one.chartsy.charting.renderers.PolylineChartRenderer;
import one.chartsy.charting.financial.StockDataSource;

/**
 * Implements the <i>Bollinger Bands</i> indicator.
 */
public class BollingerBandsIndicator extends TechnicalIndicator {
  ChartRenderer renderer;
  BollingerBandsDataSource dataSource;

  /** Initializes a new <code>BollingerBandsIndicator</code>. */
  public BollingerBandsIndicator(StockDataSource stockDS) {
    this(stockDS, 20, 2.);
  }

  /** Initializes a new <code>BollingerBandsIndicator</code>. */
  public BollingerBandsIndicator(StockDataSource stockDS, int period, double coeff) {
    super(stockDS);
    dataSource = new BollingerBandsDataSource(period, coeff);
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
    if (this.chart != null)
      this.chart.removeRenderer(renderer);
    renderer = null;
    super.detach();
  }

  @Override
  public void refresh() {
    dataSource.setPriceDataSet(stockDS.getCloseDataSet());
    if (renderer != null && renderer.getChildCount() == 2) {
      PlotStyle style = PlotStyle.createStroked(BOLLINGER_BANDS_COLOR);
      renderer.setStyles(new PlotStyle[] { style, style });
      // TODO
      //renderer.getChild(0).setVisibleInLegend(false);
    }
  }

  @Override
  public String getName() {
    return "Bollinger Bands";
  }

}

