package one.chartsy.charting.financial;

import one.chartsy.charting.Axis;
import one.chartsy.charting.Chart;
import one.chartsy.charting.ColorEx;
import one.chartsy.charting.DataInterval;
import one.chartsy.charting.DisplayPoint;
import one.chartsy.charting.LabelRenderer;
import one.chartsy.charting.PlotStyle;
import one.chartsy.charting.Scale;
import one.chartsy.charting.ScaleAnnotation;
import one.chartsy.charting.ValueGradientPaint;
import one.chartsy.charting.event.AxisChangeEvent;
import one.chartsy.charting.event.AxisListener;
import one.chartsy.charting.event.AxisRangeEvent;
import one.chartsy.charting.graphic.DataIndicator;
import one.chartsy.charting.graphic.DataRenderingHint;

import java.awt.Color;
import java.awt.Paint;
import java.util.HashMap;
import java.util.Map;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

/**
 * Implements a set of two threshold lines.
 */
public class ThresholdLines implements AxisListener, ChangeListener, DataRenderingHint {
  protected int axisIdx;
  protected DataInterval thresholds = new DataInterval();
  protected Color foreground;
  protected Color background;
  protected Color lowerColor;
  protected Color midColor;
  protected Color upperColor;

  private final Map<PlotStyle, PlotStyle> upPlotStyles = new HashMap<>();
  private final Map<PlotStyle, PlotStyle> lowPlotStyles = new HashMap<>();
  private final Map<PlotStyle, PlotStyle> midPlotStyles = new HashMap<>();

  private Chart chart;
  private DataIndicator[] indicators;
  private ScaleAnnotation[] annotations;

  private JSlider rangeSlider;

  public ThresholdLines(Color foreground, Color background, Color lowerColor, Color midColor, Color upperColor) {
    this.foreground = foreground;
    this.background = background;
    this.lowerColor = lowerColor;
    this.midColor = midColor;
    this.upperColor = upperColor;
  }

  /** Returns the axis with which the filter is associated. */
  public Axis getAxis() {
    return (axisIdx == -1) ? chart.getXAxis() : chart.getYAxis(axisIdx);
  }

  public void addChangeListener(javax.swing.event.ChangeListener lst) {
    rangeSlider.addChangeListener(lst);
  }

  /** Connects the threshold to the specified chart. */
  protected synchronized void setChart(Chart chart, int axisIdx) {
    if (this.chart == chart)
      return;
    if (this.chart != null && chart != null)
      throw new IllegalArgumentException("ThresholdLines already connected to a chart.");
    if (chart != null) {
      this.chart = chart;
      this.axisIdx = axisIdx;
      Scale scale = getChart().getScale(getAxis());
      getAxis().addAxisListener(this);
      indicators = createIndicators();
      annotations = createAnnotations();
      if (getLowerIndicator() != null)
        getChart().addDecoration(getLowerIndicator());
      if (getUpperIndicator() != null)
        getChart().addDecoration(getUpperIndicator());
      if (getLowerAnnotation() != null)
        scale.addAnnotation(getLowerAnnotation());
      if (getUpperAnnotation() != null)
        scale.addAnnotation(getUpperAnnotation());
      chart.putClientProperty(new Key(getAxis()), this);
      // adjustSlider(true);
      update();
    } else {
      Scale scale = getChart().getScale(getAxis());
      if (getLowerIndicator() != null)
        getChart().removeDecoration(getLowerIndicator());
      if (getUpperIndicator() != null)
        getChart().removeDecoration(getUpperIndicator());
      if (getLowerAnnotation() != null)
        scale.removeAnnotation(getLowerAnnotation());
      if (getUpperAnnotation() != null)
        scale.removeAnnotation(getUpperAnnotation());
      getAxis().removeAxisListener(this);
      getChart().putClientProperty(new Key(getAxis()), null);
      this.chart = chart;
      this.axisIdx = axisIdx;
    }
  }

  public DataInterval getThresholds() {
    return new DataInterval(thresholds);
  }

  public void setThresholds(DataInterval itv) {
    if (rangeSlider != null) {
      int min = rangeSlider.getMinimum();
      int max = rangeSlider.getMaximum();
      int value = (int) Math.max(min, itv.getMin());
      int extent = (int) Math.min(max, itv.getMax()) - value;
      rangeSlider.setValue(value);
      rangeSlider.getModel().setRangeProperties(value, extent, min, max, false);
    } else
      _setThresholds(itv);
  }

  private void _setThresholds(DataInterval itv) {
    thresholds = new DataInterval(itv);
    update();
  }

  /** Invoked when the range of the associated axis changes. */
  @Override
  public void axisRangeChanged(AxisRangeEvent evt) {
    if (evt.isChangedEvent() && !evt.isAdjusting() && !evt.isVisibleRangeEvent()) {
      if (rangeSlider != null)
        adjustSlider(false);
      //// else if (evt.getNewMin() != evt.getOldMin() || evt.getNewMax() !=
      //// evt.getOldMax()) {
      //// adjustRange(Math.floor(evt.getNewMin()),
      //// Math.floor(evt.getNewMax()));
      // }
    }
  }

  /** Invoked when the associated axis changes. */
  @Override
  public void axisChanged(AxisChangeEvent evt) {
    if (evt.getType() == AxisChangeEvent.ADJUSTMENT_CHANGE)
      chart.setAntiAliasing(!evt.isAdjusting());
  }

  /** Invoked when the slider changes. */
  @Override
  public void stateChanged(ChangeEvent e) {
    boolean adjusting = rangeSlider.getValueIsAdjusting();
    if (adjusting)
      getAxis().setAdjusting(true);
    adjustRange(rangeSlider.getValue(), rangeSlider.getValue() + rangeSlider.getExtent());
    if (!adjusting)
      getAxis().setAdjusting(false);
  }

  private void adjustRange(double min, double max) {
    _setThresholds(new DataInterval(min, max));
  }

  private void adjustSlider(boolean reset) {
    if (rangeSlider == null)
      return;
    DataInterval range = null;
    Axis axis = getAxis();
    if (axis.isBounded())
      range = axis.getDataRange();
    else
      range = axis.getVisibleRange();
    int min = (int) Math.floor(range.getMin());
    int max = (int) Math.ceil(range.getMax());
    int val = min;
    if (!reset && rangeSlider.getValue() <= max)
      val = Math.max(rangeSlider.getValue(), min);
    int extent = reset ? (max - min) : Math.min(rangeSlider.getExtent(), max - val);
    rangeSlider.getModel().setRangeProperties(val, extent, min, max, false);
    // System.out.println(rangeSlider.getModel());
  }

  public final synchronized JSlider getRangeSlider() {
    if (rangeSlider == null) {
      rangeSlider = new JSlider(JSlider.HORIZONTAL, 0, 100, 0);
      rangeSlider.setExtent(100);
      rangeSlider.addChangeListener(this);
      rangeSlider.setToolTipText("Adjust Threshold Limits");
      adjustSlider(true);
    }
    return rangeSlider;
  }

  /**
   * Returns the chart associated with this object.
   */
  public Chart getChart() {
    return chart;
  }

  /**
   * Returns the graphical indicator of the lower threshold value.
   */
  public DataIndicator getLowerIndicator() {
    return (indicators == null) ? null : indicators[0];
  }

  /**
   * Returns the graphical indicator of the upper threshold value.
   */
  public DataIndicator getUpperIndicator() {
    return (indicators == null) ? null : indicators[1];
  }

  /**
   * Returns the scale annotation of the lower threshold value.
   */
  public ScaleAnnotation getLowerAnnotation() {
    return (annotations == null) ? null : annotations[0];
  }

  /**
   * Returns the scale annotation of the upper threshold value.
   */
  public ScaleAnnotation getUpperAnnotation() {
    return (annotations == null) ? null : annotations[1];
  }

  /** Creates the threshold line indicators. */
  protected DataIndicator[] createIndicators() {
    DataIndicator[] indicators = new DataIndicator[2];
    // BasicStroke stroke = new BasicStroke(1.f, BasicStroke.CAP_BUTT,
    // BasicStroke.JOIN_MITER, 10.f, new float[] { 4.f },
    // .0f);
    PlotStyle style = new PlotStyle(PlotStyle.DEFAULT_STROKE, foreground, ColorEx.setAlpha(lowerColor, .2f));
    // indicators[0] = new DataIndicator(axisIdx, new DataInterval(),
    // null);
    indicators[0] = new DataIndicator(axisIdx, 0, null);
    indicators[0].setStyle(style);
    indicators[0].setDrawOrder(-1);
    style = new PlotStyle(PlotStyle.DEFAULT_STROKE, foreground, ColorEx.setAlpha(upperColor, .2f));
    // indicators[1] = new DataIndicator(axisIdx, new DataInterval(),
    // null);
    indicators[1] = new DataIndicator(axisIdx, 0, null);
    indicators[1].setStyle(style);
    indicators[1].setDrawOrder(-1);
    return indicators;
  }

  /** Creates the threshold line scale annotations. */
  protected ScaleAnnotation[] createAnnotations() {
    if (getChart().getScale(getAxis()) == null)
      return null;
    ScaleAnnotation[] annotations = new ScaleAnnotation[2];
    annotations[0] = new ScaleAnnotation(0, null);
    configureRenderer(annotations[0].getLabelRenderer(), lowerColor);
    annotations[1] = new ScaleAnnotation(0, null);
    configureRenderer(annotations[1].getLabelRenderer(), upperColor);
    return annotations;
  }

  private void configureRenderer(LabelRenderer labelRenderer, Color bg) {
    labelRenderer.setColor(foreground);
    labelRenderer.setBackground(bg);
    labelRenderer.setOpaque(true);
    labelRenderer.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(foreground),
        BorderFactory.createEmptyBorder(0, 2, 0, 2)));
  }

  @Override
  public PlotStyle getStyle(DisplayPoint dp, PlotStyle style) {

    if (dp.getYData() >= thresholds.getMax()) {
      PlotStyle s = upPlotStyles.get(style);
      if (s != null)
        return s;
      if (style.isFillOn()) {
        Color c = style.getFillColor();
        if (c == null)
          return style;
        c = ColorEx.setAlpha(upperColor, c.getAlpha() / 255.f);
        if (style.isStrokeOn())
          s = new PlotStyle(upperColor, c);
        else
          s = style.setFillPaint(c);
      } else {
        s = style.setStrokePaint(upperColor);
      }
      upPlotStyles.put(style, s);
      return s;
    } else if (dp.getYData() <= thresholds.getMin()) {
      PlotStyle s = lowPlotStyles.get(style);
      if (s != null)
        return s;
      if (style.isFillOn()) {
        Color c = style.getFillColor();
        if (c == null)
          return style;
        c = ColorEx.setAlpha(lowerColor, c.getAlpha() / 255.f);
        if (style.isStrokeOn())
          s = new PlotStyle(lowerColor, c);
        else
          s = style.setFillPaint(c);
      } else {
        s = style.setStrokePaint(lowerColor);
      }
      lowPlotStyles.put(style, s);
      return s;
    } else {
      PlotStyle s = midPlotStyles.get(style);
      if (s != null)
        return s;
      if (style.isFillOn()) {
        Color c = style.getFillColor();
        if (c == null)
          return style;
        c = ColorEx.setAlpha(midColor, c.getAlpha() / 255.f);
        if (style.isStrokeOn())
          s = new PlotStyle(midColor, c);
        else
          s = style.setFillPaint(c);
      } else {
        s = style.setStrokePaint(midColor);
      }
      midPlotStyles.put(style, s);
      return s;
    }
  }

  /**
   * Invoked to update the scale annotations and the data indicators.
   */
  private void update() {
    Scale scale = getChart().getScale(getAxis());
    if (thresholds.isEmpty()) {
      if (getLowerIndicator() != null)
        getLowerIndicator().setVisible(false);
      if (getUpperIndicator() != null)
        getUpperIndicator().setVisible(false);
      if (getLowerAnnotation() != null)
        scale.removeAnnotation(getLowerAnnotation());
      if (getUpperAnnotation() != null)
        scale.removeAnnotation(getUpperAnnotation());
    } else {
      DataIndicator indic = getLowerIndicator();
      if (indic != null) {
        if (indic.getType() == DataIndicator.X_VALUE || indic.getType() == DataIndicator.Y_VALUE)
          indic.setValue(thresholds.getMin());
        else
          indic.setRange(new DataInterval(-Double.MAX_VALUE, thresholds.getMin()));
        if (!indic.isVisible())
          indic.setVisible(true);
      }
      indic = getUpperIndicator();
      if (indic != null) {
        if (indic.getType() == DataIndicator.X_VALUE || indic.getType() == DataIndicator.Y_VALUE)
          indic.setValue(thresholds.getMax());
        else
          indic.setRange(new DataInterval(thresholds.getMax(), Double.MAX_VALUE));
        if (!indic.isVisible())
          indic.setVisible(true);
      }
      ScaleAnnotation anno = getLowerAnnotation();
      if (anno != null) {
        anno.setValue(thresholds.getMin());
        if (anno.getScale() == null)
          scale.addAnnotation(anno);
      }
      anno = getUpperAnnotation();
      if (anno != null) {
        anno.setValue(thresholds.getMax());
        if (anno.getScale() == null)
          scale.addAnnotation(anno);
      }
    }
    getChart().getChartArea().repaint();
  }

  Paint getGradientPaint(float alpha) {
    double[] vals = { getAxis().getDataMin(), thresholds.getMin(), thresholds.getMin(), thresholds.getMax(),
        thresholds.getMax(), getAxis().getDataMax() };
    Color[] colors = { ColorEx.setAlpha(lowerColor, alpha), ColorEx.setAlpha(lowerColor, alpha),
        ColorEx.setAlpha(midColor, alpha), ColorEx.setAlpha(midColor, alpha), ColorEx.setAlpha(upperColor, alpha),
        ColorEx.setAlpha(upperColor, alpha) };
    return new ValueGradientPaint(chart, 0, vals, colors);
  }

  /**
   * Adds threshold lines to the specified chart axis.
   * 
   * @param t
   *          The threshold lines.
   * @param chart
   *          The considered chart.
   * @param axisIdx
   *          The index of the considered axis.
   */
  public static void set(ThresholdLines t, Chart chart, int axisIdx) {
    t.setChart(chart, axisIdx);
  }

  /**
   * Removes the <code>ThresholdLines</code> associated with the specified chart
   * axis.
   */
  public static ThresholdLines remove(Chart chart, int axisIdx) {
    ThresholdLines t = get(chart, axisIdx);
    if (t != null)
      t.setChart(null, -1);
    return t;
  }

  /**
   * Returns the <code>ThresholdLines</code> associated with the specified chart
   * and the given axis.
   */
  public static ThresholdLines get(Chart chart, int axisIdx) {
    Axis axis = (axisIdx == -1) ? chart.getXAxis() : chart.getYAxis(axisIdx);
    return (ThresholdLines) chart.getClientProperty(new Key(axis));
  }

  // --------------------------------------------------------------------------
  /**
   * A key to associate an <code>ThresholdLines</code> with a chart and an axis.
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

