package one.chartsy.charting.financial;

import one.chartsy.charting.Axis;
import one.chartsy.charting.Chart;
import one.chartsy.charting.ColorEx;
import one.chartsy.charting.DataInterval;
import one.chartsy.charting.LocalZoomAxisTransformer;
import one.chartsy.charting.PlotStyle;
import one.chartsy.charting.Scale;
import one.chartsy.charting.ScaleAnnotation;
import one.chartsy.charting.event.AxisChangeEvent;
import one.chartsy.charting.event.AxisListener;
import one.chartsy.charting.event.AxisRangeEvent;
import one.chartsy.charting.graphic.DataIndicator;

import java.awt.BasicStroke;
import java.awt.Color;

/**
 * A handler class for local zoom functionality.
 */
public class LocalZoomHandler implements AxisListener {
  private ScaleAnnotation _startAnnotation;
  private ScaleAnnotation _endAnnotation;
  private final LocalZoomAxisTransformer _t;
  private final Chart _chart;
  private final Chart[] _charts;
  private DataIndicator[] _indicators;

  /**
   * Creates a new <code>LocalZoomHandler</code> object.
   * 
   * @param chart
   *          The chart to handle.
   * @param t
   *          The transformer to handle.
   * @param useScaleAnnotation
   *          A Boolean value indicating whether annotations should be set on
   *          the scales to display the values of the local zoom interval.
   * @param useIndicator
   *          A Boolean value indicating whether a data indicator should be used
   *          to display the zoom window.
   */
  private LocalZoomHandler(Chart chart, Chart[] auxCharts, LocalZoomAxisTransformer t,
      boolean useScaleAnnotation, boolean useIndicator) {
    _t = t;
    _chart = chart;

    int count = (auxCharts == null ? 0 : auxCharts.length);
    _charts = new Chart[count + 1];
    _charts[0] = chart;
    if (count > 0)
      System.arraycopy(auxCharts, 0, _charts, 1, count);
    Scale scale = _chart.getScale(t.getAxis());
    if (scale != null && useScaleAnnotation) {
      DataInterval zw = t.getZoomRange();
      _startAnnotation = new ScaleAnnotation(zw.getMin());
      _endAnnotation = new ScaleAnnotation(zw.getMax());
      scale.addAnnotation(_startAnnotation);
      scale.addAnnotation(_endAnnotation);
    }
    setUsingIndicator(useIndicator);
    t.getAxis().addAxisListener(this);
  }

  /**
   * Returns the chart associated with this object.
   */
  public final Chart getChart() {
    return _chart;
  }

  /**
   * Returns the transformer associated with this object.
   */
  public final LocalZoomAxisTransformer getTransformer() {
    return _t;
  }

  /**
   * Returns the scale annotation that displays the start of the zoomed
   * interval, or <code>null</code> if no annotation is used.
   * 
   * @see #setStartAnnotation
   */
  public final ScaleAnnotation getStartAnnotation() {
    return _startAnnotation;
  }

  /**
   * Sets the scale annotation that displays the start of the zoomed interval.
   * 
   * @param anno
   *          The new annotation, or <code>null</code> to remove the previous
   *          one.
   * @see #getStartAnnotation
   */
  public void setStartAnnotation(ScaleAnnotation anno) {
    Scale scale = _chart.getScale(_t.getAxis());
    if (scale != null && _startAnnotation != null)
      scale.removeAnnotation(_startAnnotation);
    _startAnnotation = anno;
    if (scale != null && anno != null)
      scale.addAnnotation(anno);
  }

  /**
   * Returns the scale annotation that displays the end of the zoomed interval,
   * or <code>null</code> if no annotation is used.
   * 
   * @see #setEndAnnotation
   */
  public final ScaleAnnotation getEndAnnotation() {
    return _endAnnotation;
  }

  /**
   * Sets the scale annotation that displays the end of the zoomed interval.
   * 
   * @param cursor
   *          The new annotation, or <code>null</code> to remove the previous
   *          one.
   * @see #getEndAnnotation
   */
  public void setEndAnnotation(ScaleAnnotation cursor) {
    Scale scale = _chart.getScale(_t.getAxis());
    if (scale != null && _endAnnotation != null)
      scale.removeAnnotation(_endAnnotation);
    _endAnnotation = cursor;
    if (scale != null && cursor != null)
      scale.addAnnotation(cursor);
  }

  /**
   * Returns the graphical indicator of the zoom window.
   */
  public DataIndicator[] getIndicators() {
    return _indicators;
  }

  /**
   * Specifies whether an indicator must be used to display the zoom window.
   * 
   * @param b
   *          A Boolean value indicating whether an indicator must be used.
   */
  public void setUsingIndicator(boolean b) {
    if (_indicators != null && !b) {
      for (int i = 0; i < _charts.length; ++i) {
        if (_indicators[i] != null)
          _charts[i].removeDecoration(_indicators[i]);
      }
    }
    if (!b) {
      _indicators = null;
    } else {
      _indicators = createIndicators();
      for (int i = 0; i < _charts.length; ++i) {
        if (_indicators[i] != null)
          _charts[i].addDecoration(_indicators[i]);
      }
    }
  }

  protected DataIndicator[] createIndicators() {
    int count = _charts.length;
    DataIndicator[] indicators = new DataIndicator[count];
    for (int i = 0; i < count; ++i) {
      if (_t.getAxis().getType() == Axis.X_AXIS)
        indicators[i] = new DataIndicator(-1, _t.getZoomRange(), null);
      else
        indicators[i] = new DataIndicator(0, _t.getZoomRange(), null);
      PlotStyle style = new PlotStyle(new BasicStroke(2.f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND),
              ColorEx.setAlpha(Color.black, .6f), ColorEx.setAlpha(new java.awt.Color(173, 216, 230), .2f));
      indicators[i].setStyle(style);
      indicators[i].setDrawOrder(Chart.DRAW_ABOVE);
    }
    return indicators;
  }

  /**
   * Invoked when the visible range of the associated axis changes.
   */
  @Override
  public void axisRangeChanged(AxisRangeEvent ev) {
  }

  /**
   * Invoked when a change occurs on the associated axis. This method handles
   * <code>AxisChangeEvent.TRANSFORMER_CHANGE</code> events to update the scale
   * annotations and the data indicator.
   * 
   * @see #getStartAnnotation
   * @see #getEndAnnotation
   */
  @Override
  public void axisChanged(AxisChangeEvent evt) {
    if (evt.getType() == AxisChangeEvent.TRANSFORMER_CHANGE) {
      DataInterval zw = _t.getZoomRange();
      if (_startAnnotation != null)
        _startAnnotation.setValue(zw.getMin());
      if (_endAnnotation != null)
        _endAnnotation.setValue(zw.getMax());
      if (_indicators != null) {
        for (int i = 0; i < _indicators.length; ++i) {
          if (_indicators[i] != null)
            _indicators[i].setRange(zw);
        }
      }
    }
  }

  /**
   * Associates the given chart and transformer through a
   * <code>LocalZoomHandler</code>.
   * 
   * @return The created <code>LocalZoomHandler</code>.
   */
  public static LocalZoomHandler set(Chart chart, Chart[] auxCharts, LocalZoomAxisTransformer t) {
    LocalZoomHandler handler = new LocalZoomHandler(chart, auxCharts, t, false, true);

    chart.putClientProperty(new Key(t.getAxis()), handler);
    return handler;
  }

  /**
   * Creates an <code>LocalZoomAxisTransformer</code> and associates it with
   * the given chart through a <code>LocalZoomHandler</code>.
   * 
   * @param chart
   *          The chart on which the transformer will be set.
   * @param axisIdx
   *          The index of the transformer axis. This parameter value should be
   *          <code>-1</code> to reference the x-axis, or a valid y-axis index
   *          value.
   * @param useScaleAnnotation
   *          A Boolean value indicating whether annotations should be set on
   *          the scales to display the values of the local zoom interval.
   * @param useIndicator
   *          A Boolean value indicating whether a data indicator should be used
   *          to display the zoom window.
   * @param zoomFactor
   *          The transformer zoom factor.
   * @param continuous
   *          The transformer <i>continuous</i> property.
   * @param min
   *          The minimum of the zoom interval.
   * @param max
   *          The maximum of the zoom interval.
   */
  public static LocalZoomHandler set(Chart chart, int axisIdx, boolean useScaleAnnotation, boolean useIndicator,
      double zoomFactor, boolean continuous, double min, double max) {
      Axis axis = (axisIdx == -1) ? chart.getXAxis() : chart.getYAxis(axisIdx);
    LocalZoomAxisTransformer t = LocalZoomAxisTransformer.create(axis, min, max, zoomFactor, continuous);
    axis.setTransformer(t);
    LocalZoomHandler handler = new LocalZoomHandler(chart, null, t, useScaleAnnotation, useIndicator);
    chart.putClientProperty(new Key(axis), handler);
    return handler;
  }

  /**
   * Returns the <code>LocalZoomHandler</code> associated with the specified
   * chart and the given axis.
   */
  public static LocalZoomHandler get(Chart chart, int axisIdx) {
    Axis axis = (axisIdx == -1) ? chart.getXAxis() : chart.getYAxis(axisIdx);
    return (LocalZoomHandler) chart.getClientProperty(new Key(axis));
  }

  /**
   * Removes the <code>LocalZoomHandler</code> associated with the specified
   * chart and the given axis.
   */
  public static void unset(Chart chart, int axisIdx) {
    LocalZoomHandler h = get(chart, axisIdx);
    if (h != null) {
      h.detach(chart);
    }
  }

  private void detach(Chart chart) {
    Axis axis = _t.getAxis();
    Scale scale = chart.getScale(axis);
    axis.setTransformer(null);
    if (_startAnnotation != null)
      scale.removeAnnotation(_startAnnotation);
    if (_endAnnotation != null)
      scale.removeAnnotation(_endAnnotation);
    for (int i = 0; i < _charts.length; ++i) {
      if (_indicators[i] != null)
        _charts[i].removeDecoration(_indicators[i]);
    }
    chart.putClientProperty(new Key(axis), null);
  }

  // --------------------------------------------------------------------------
  /**
   * A key to associate an <code>LocalZoomHandler</code> with a chart and an
   * axis.
   */
  private static class Key {
    private final Axis axis;

    public Key(Axis axis) {
      this.axis = axis;
    }

    @Override
    public int hashCode() {
      return axis.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
      if (!(obj instanceof Key key))
        return false;
        return key.axis == this.axis;
    }
  }
}

