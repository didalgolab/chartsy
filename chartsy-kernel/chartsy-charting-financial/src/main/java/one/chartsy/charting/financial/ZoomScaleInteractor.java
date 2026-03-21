package one.chartsy.charting.financial;

import one.chartsy.charting.ChartInteractor;
import one.chartsy.charting.DataInterval;
import one.chartsy.charting.PlotStyle;
import one.chartsy.charting.Scale;
import one.chartsy.charting.StepsDefinition;
import one.chartsy.charting.util.GraphicUtil;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;

/**
 * An interactor that zooms a scale, snapping on the ticks.
 */
public class ZoomScaleInteractor extends ChartInteractor {
  private boolean swap = false;
  protected PlotStyle style;
  protected int axisIdx;
  protected double start;
  protected double end;

  /**
   * Creates a new <code>ZoomScaleInteractor</code> associated with the
   * specified axis that zooms in on a <code>BUTTON1</code> event and zooms out
   * on <code>SHIFT+BUTTON1</code>.
   */
  public ZoomScaleInteractor(int axisIdx) {
    super(axisIdx != -1 ? axisIdx : 0, MouseEvent.BUTTON1_DOWN_MASK);
    this.axisIdx = axisIdx;
    enableEvents(AWTEvent.MOUSE_EVENT_MASK | AWTEvent.MOUSE_MOTION_EVENT_MASK | AWTEvent.KEY_EVENT_MASK);
    style = new PlotStyle(new BasicStroke(5), Color.magenta);
  }

  private Scale getScale() {
    return (getChart() != null) ? (axisIdx == -1 ? getChart().getXScale() : getChart().getYScale(axisIdx)) : null;
  }

  // Allocated only once.
  private final Rectangle scaleBounds = new Rectangle();

  /**
   * Returns <code>true</code> for events occurring on the scale.
   */
  @Override
  public boolean isHandling(int x, int y) {
    return getScale() != null && getScale().getBounds(scaleBounds).contains(x, y);
  }

  /**
   * Processes mouse events.
   */
  @Override
  public void processMouseEvent(MouseEvent evt) {
    if (getScale() == null)
      return;
    double value;
    switch (evt.getID()) {
    case MouseEvent.MOUSE_PRESSED:
      if (isInOperation())
        break;
      if (((evt.getModifiersEx() & getEventMaskEx()) == getEventMaskEx()) && ((evt.getModifiersEx() & ~getEventMaskEx()) == 0)) {
        startOperation(evt);
        value = getScale().toValue(evt.getX(), evt.getY());
        start = getScale().getStepsDefinition().previousStep(value);
        end = getScale().getStepsDefinition().incrementStep(start);
        drawGhost();
        evt.consume();
      }
      break;

    case MouseEvent.MOUSE_RELEASED:
      if (!isInOperation())
        break;
      value = getScale().toValue(evt.getX(), evt.getY());
      computeStartEnd(value);
      drawGhost();
      zoomScale();
      endOperation(evt);
      evt.consume();
      break;
    }
  }

  /**
   * Processes mouse motion events.
   */
  @Override
  public void processMouseMotionEvent(MouseEvent evt) {
    if (evt.getID() == MouseEvent.MOUSE_DRAGGED && isInOperation()) {
      drawGhost();
      double value = getScale().toValue(evt.getX(), evt.getY());
      computeStartEnd(value);
      drawGhost();
      evt.consume();
    }
  }

  /**
   * Processes key events.
   */
  @Override
  public void processKeyEvent(KeyEvent evt) {
    if (getScale() == null)
      return;
    if (evt.getID() == KeyEvent.KEY_PRESSED && evt.getKeyCode() == KeyEvent.VK_ESCAPE) {
      if (isInOperation())
        drawGhost();
      abort();
      evt.consume();
    }
  }

  private void computeStartEnd(double value) {
    StepsDefinition stepDef = getScale().getStepsDefinition();
    if (value < start) {
      if (!swap) {
        end = start;
        swap = true;
      }
      start = stepDef.previousStep(value);
    } else if (value > end) {
      if (swap) {
        start = end;
        swap = false;
      }
      end = stepDef.incrementStep(value);
    } else {
      if (swap)
        start = stepDef.previousStep(value);
      else {
        end = stepDef.previousStep(value);
        if (end != value)
          end = stepDef.incrementStep(end);
      }
    }
  }

  /**
   * Returns the ghost shape.
   */
  private Shape getGhostShape() {
    DataInterval inter = new DataInterval(start, end);
    Shape s = getChart().getProjector().getShape(getScale().getCrossingValue(), inter,
        getScale().getDualAxis().getType(), getChart().getProjectorRect(), getCoordinateSystem());
    return s;
  }

  /**
   * Returns the ghost bounds.
   */
  @Override
  protected Rectangle getGhostBounds() {
    // Take into account the stroke width.
    return GraphicUtil.toRectangle(style.getShapeBounds(getGhostShape()), null);
  }

  /**
   * Draws the ghost into the specified graphics context.
   */
  @Override
  protected void drawGhost(Graphics g) {
    /*
     * In order to have a ghost independent from the projector (a rectangle for
     * Cartesian and an arc for polar projectors) the shape is retrieved using
     * <code>ChartProjector.getShape()</code>.
     */
    Shape s = getGhostShape();
    // Render the shape via the rendering style.
    style.draw(g, s);
  }

  /**
   * Performs the zoom.
   */
  protected void zoomScale() {
    StockUtil.performAnimatedZoom(getChart(), getYAxisIndex(), new DataInterval(start, end),
        StockUtil.ANIMATION_STEPS);
  }

  /**
   * Called when the interaction has been aborted.
   */
  @Override
  protected void abort() {
    super.abort();
    swap = false;
    /* Disable ghost drawing operation. */
    setGhostDrawingAllowed(false);
  }

  /**
   * Called when the interaction ends.
   */
  @Override
  protected void endOperation(MouseEvent evt) {
    super.endOperation(evt);
    swap = false;
    /* Disable ghost drawing operation. */
    setGhostDrawingAllowed(false);
  }

  /**
   * Called when the interaction starts.
   */
  @Override
  protected void startOperation(MouseEvent evt) {
    super.startOperation(evt);
    /* Enable ghost drawing operation. */
    setGhostDrawingAllowed(true);
  }
}

