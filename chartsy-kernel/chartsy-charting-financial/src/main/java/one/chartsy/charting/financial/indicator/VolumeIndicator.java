package one.chartsy.charting.financial.indicator;

import one.chartsy.charting.*;
import one.chartsy.charting.ValueFormatter;
import one.chartsy.charting.data.DataSet;
import one.chartsy.charting.renderers.SingleBarRenderer;
import one.chartsy.charting.financial.ExponentAxisTransformer;
import one.chartsy.charting.financial.StockDataSource;

import java.text.NumberFormat;

/**
 * Implements the <i>Volume</i> indicator.
 */
public class VolumeIndicator extends TechnicalIndicator {
  ChartRenderer renderer;

  /** Initializes a new <code>VolumeIndicator</code>. */
  public VolumeIndicator(StockDataSource stockDS) {
    super(stockDS);
  }

  @Override
  public void attach(Chart chart) {
    super.attach(chart);
    PlotStyle style = new PlotStyle(VOLUME_COLOR.darker(), VOLUME_COLOR);
    renderer = new SingleBarRenderer(style, 100.);
    renderer.setName("Volume (x1000)");
    chart.addRenderer(renderer);
    chart.getYAxis(0).setDataMin(0);
    chart.getYScale(0).setSkipLabelMode(Scale.ADAPTIVE_SKIP);
    chart.getYAxis(0).setTransformer(new ExponentAxisTransformer(.5));
    chart.getYScale(0).setLabelFormat(new ValueFormatter() {
      final NumberFormat fmt = NumberFormat.getNumberInstance();

      @Override
      public String formatValue(double value) {
        return fmt.format(value / 1000);
      }
    });
    refresh();
  }

  @Override
  public void detach() {
    if (chart == null)
      return;
    chart.removeRenderer(renderer);
    chart.getYAxis(0).setAutoDataMin(true);
    chart.getYAxis(0).setTransformer(null);
    chart.getYScale(0).setLabelFormat(null);
    chart.getYScale(0).setSkipLabelMode(Scale.CONSTANT_SKIP);
    renderer = null;
    super.detach();
  }

  @Override
  public void refresh() {
    DataSet volumeDataSet = stockDS.getVolumeDataSet();
    if (volumeDataSet == null)
      renderer.getDataSource().setAll(null);
    else
      renderer.getDataSource().setAll(new DataSet[] { volumeDataSet });
  }

  @Override
  public DisplayPoint getHighlightedPoint(int dataIdx) {
    return (renderer == null || renderer.getDataSource().size() == 0) ? null
        : renderer.getDisplayPoint(renderer.getDataSource().get(0), dataIdx);
  }

  @Override
  public String getName() {
    return "Volume";
  }

  @Override
  public DataSet getMainDataSet() {
    return (renderer == null || renderer.getDataSource().size() == 0) ? null
        : renderer.getDataSource().get(0);
  }
}

