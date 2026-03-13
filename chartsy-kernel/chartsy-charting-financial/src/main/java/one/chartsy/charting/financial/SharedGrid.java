package one.chartsy.charting.financial;

import one.chartsy.charting.Grid;
import one.chartsy.charting.Scale;
import one.chartsy.charting.util.DoubleArray;

import java.awt.Graphics;
import java.awt.Paint;

/**
 * A grid that displays grid lines computed according to a specified scale.
 * <p>
 * This class is designed to be used by charts whose x- or y-axis is
 * synchronized with another chart, and which do not define any scale.
 */
public class SharedGrid extends Grid {
  /** The referenced scale. */
  private final Scale scale;

  /**
   * Initializes a new grid.
   * 
   * @param scale
   *          The reference scale.
   */
  public SharedGrid(Scale scale) {
    this.scale = scale;
  }

  /**
   * Initializes a new grid.
   * 
   * @param scale
   *          The reference scale.
   * @param majPaint
   *          The paint for major grid lines.
   */
  public SharedGrid(Scale scale, Paint majPaint) {
    this(scale);
    setMajorPaint(majPaint);
  }

  /** Draws the grid. */
  @Override
  public void draw(Graphics g) {
    if (getChart() == null || scale == null || scale.getChart() == null)
      return;
    if (isMajorLineVisible()) {
      DoubleArray values = scale.getStepValues();
      if (values.size() > 0)
        draw(g, values, true);
    }
    if (isMinorLineVisible()) {
      DoubleArray values = scale.getSubStepValues();
      if (values.size() > 0)
        draw(g, values, false);
    }
  }
}

