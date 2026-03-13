package one.chartsy.charting.financial.indicator;

import java.awt.Paint;

import one.chartsy.charting.Chart;
import one.chartsy.charting.ChartRenderer;
import one.chartsy.charting.DataInterval;
import one.chartsy.charting.DisplayPoint;
import one.chartsy.charting.PlotStyle;
import one.chartsy.charting.data.DataSet;
import one.chartsy.charting.graphic.DataIndicator;
import one.chartsy.charting.renderers.SinglePolylineRenderer;
import one.chartsy.charting.util.java2d.Pattern;
import one.chartsy.charting.financial.StockDataSource;

/**
 * Implements the <i>Relative Strength Index</i> indicator.
 */
public class RSIIndicator extends TechnicalIndicator {
  ChartRenderer renderer;
  DataIndicator[] indicators;
  RSIDataSet rsiDataSet;

  public RSIIndicator(StockDataSource stockDS, int period) {
    super(stockDS);
    rsiDataSet = new RSIDataSet(period);
  }

  @Override
  public void attach(Chart chart) {
    super.attach(chart);
    renderer = new SinglePolylineRenderer(PlotStyle.createStroked(RSI_COLOR));
    indicators = new DataIndicator[3];
    indicators[0] = new DataIndicator(0, new DataInterval(0, 30), null);
    Paint p = new Pattern(Pattern.UP_LIGHT_DIAGONAL, RSI_PATTERN_COLOR, null);
    indicators[0].setStyle(new PlotStyle(p));
    indicators[1] = new DataIndicator(0, 50, null);
    indicators[1].setStyle(new PlotStyle(2.f, RSI_INDIC_COLOR));
    indicators[2] = new DataIndicator(0, new DataInterval(70, 100), null);
    indicators[2].setStyle(new PlotStyle(p));
    for (int i = 0; i < indicators.length; ++i) {
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
      renderer.getDataSource().setAll(null);
      renderer = null;
      indicators = null;
    }
    super.detach();
  }

  @Override
  public void refresh() {
    DataSet priceDataSet = stockDS.getCloseDataSet();
    if (priceDataSet == null) {
      rsiDataSet.setDataSets(null);
      renderer.getDataSource().setAll(null);
    } else {
      rsiDataSet.setDataSets(new DataSet[] { priceDataSet });
      if (renderer.getDataSource().size() == 0)
        renderer.getDataSource().add(rsiDataSet);
    }
  }

  @Override
  public DisplayPoint getHighlightedPoint(int dataIdx) {
    return (renderer == null) ? null : renderer.getDisplayPoint(rsiDataSet, dataIdx);
  }

  @Override
  public String getName() {
    return "RSI";
  }

  @Override
  public DataSet getMainDataSet() {
    return rsiDataSet;
  }
}

