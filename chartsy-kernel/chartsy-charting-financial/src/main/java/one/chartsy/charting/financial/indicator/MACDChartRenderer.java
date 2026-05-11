package one.chartsy.charting.financial.indicator;

import one.chartsy.charting.ChartRenderer;
import one.chartsy.charting.PlotStyle;
import one.chartsy.charting.data.DataSet;
import one.chartsy.charting.renderers.SimpleCompositeChartRenderer;
import one.chartsy.charting.renderers.SingleBarRenderer;
import one.chartsy.charting.renderers.SinglePolylineRenderer;

import java.awt.Color;
import java.awt.Paint;

/**
 * A renderer displaying the <code>MACD</code> indicator.
 */
class MACDChartRenderer extends SimpleCompositeChartRenderer {
  private final boolean showDivergence = true;
  private final Paint[] paints;

  /** Initializes a new <code>MACD</code> renderer. */
  public MACDChartRenderer() {
    this(Color.blue, Color.red, Color.black);
  }

  /** Initializes a new <code>MACD</code> renderer. */
  public MACDChartRenderer(Paint macdPaint, Paint signalPaint, Paint divergencePaint) {
    paints = new Paint[] { macdPaint, signalPaint, divergencePaint };
  }

  /** Creates a new child renderer to display the specified data set. */
  @Override
  protected ChartRenderer createChild(DataSet dataSet) {
    int idx = getDataSource().indexOf(dataSet);
    if (showDivergence)
      --idx;
    if (idx == -1) {
      return new SingleBarRenderer(new PlotStyle(paints[2]));
    } else {
      idx = idx % 2;
      PlotStyle style = (idx == 1) ? PlotStyle.createStroked(paints[idx]) : new PlotStyle(2.f, paints[idx]);
      return new SinglePolylineRenderer(style);
    }
  }
}

