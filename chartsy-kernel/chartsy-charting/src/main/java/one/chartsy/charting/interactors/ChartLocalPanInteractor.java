package one.chartsy.charting.interactors;

import java.awt.event.MouseEvent;

import one.chartsy.charting.Axis;
import one.chartsy.charting.AxisTransformer;
import one.chartsy.charting.AxisTransformerException;
import one.chartsy.charting.DataInterval;
import one.chartsy.charting.DoublePoints;
import one.chartsy.charting.LocalZoomAxisTransformer;

/// Pans the active [LocalZoomAxisTransformer] lens instead of the whole axis range.
///
/// The gesture can start only inside the current local-zoom range of the x axis, the y axis, or
/// both. During the drag the interactor converts pointer motion through the transformer itself, so
/// movement is measured in the transformed lens space rather than in raw source coordinates. That
/// keeps the lens aligned with what the user is actually dragging on screen.
///
/// When moving the lens would push its transformed window beyond the currently visible axis range
/// but still stay inside the underlying data range, this interactor first scrolls the owning chart
/// so the lens remains visible and then updates the transformer's zoom range. Axes without an
/// active [LocalZoomAxisTransformer] are ignored.
public class ChartLocalPanInteractor extends ChartPanInteractor {
    private static final int ACTIVE_X_AXIS = 1;
    private static final int ACTIVE_Y_AXIS = 2;

    private transient int activeAxesMask;

    /// Creates a local-pan interactor for the primary y-axis slot using the secondary mouse button.
    public ChartLocalPanInteractor() {
        this(0, MouseEvent.BUTTON3_DOWN_MASK);
    }

    /// Creates a local-pan interactor for one y-axis slot and modifier combination.
    ///
    /// @param yAxisIndex the y-axis slot whose local zoom state should be translated
    /// @param eventMask the mouse modifier mask required to start panning
    public ChartLocalPanInteractor(int yAxisIndex, int eventMask) {
        super(yAxisIndex, eventMask);
    }

    /// Returns the current x-axis transformer when it supports local zoom.
    ///
    /// @return the active x-axis local zoom transformer, or `null` when the x axis does not use
    ///     one
    protected LocalZoomAxisTransformer getXTransformer() {
        AxisTransformer transformer = getXAxis().getTransformer();
        return (transformer instanceof LocalZoomAxisTransformer localZoomTransformer)
                ? localZoomTransformer
                : null;
    }

    /// Returns the current y-axis transformer when it supports local zoom.
    ///
    /// @return the active y-axis local zoom transformer, or `null` when the y axis does not use
    ///     one
    protected LocalZoomAxisTransformer getYTransformer() {
        AxisTransformer transformer = getYAxis().getTransformer();
        return (transformer instanceof LocalZoomAxisTransformer localZoomTransformer)
                ? localZoomTransformer
                : null;
    }

    @Override
    protected boolean isValidStartPoint(double x, double y) {
        activeAxesMask = 0;

        LocalZoomAxisTransformer xTransformer = getXTransformer();
        if (xTransformer != null && xTransformer.getZoomRange().isInside(x)) {
            activeAxesMask |= ACTIVE_X_AXIS;
        }

        LocalZoomAxisTransformer yTransformer = getYTransformer();
        if (yTransformer != null && yTransformer.getZoomRange().isInside(y)) {
            activeAxesMask |= ACTIVE_Y_AXIS;
        }

        return activeAxesMask != 0;
    }

    /// Translates the active local-zoom range or ranges by one drag step.
    ///
    /// The input buffers must contain the previous and current pointer locations in data
    /// coordinates. Only axes selected during [#isValidStartPoint(double, double)] participate in
    /// the drag.
    ///
    /// @param previousDataPoint the previous data-space pointer location
    /// @param currentDataPoint the current data-space pointer location
    @Override
    protected void pan(DoublePoints previousDataPoint, DoublePoints currentDataPoint) {
        if ((activeAxesMask & ACTIVE_X_AXIS) != 0
                && !panAxis(getXTransformer(), getXAxis(), previousDataPoint.getX(0), currentDataPoint.getX(0), true)) {
            return;
        }
        if ((activeAxesMask & ACTIVE_Y_AXIS) != 0) {
            panAxis(getYTransformer(), getYAxis(), previousDataPoint.getY(0), currentDataPoint.getY(0), false);
        }
    }

    private boolean panAxis(LocalZoomAxisTransformer transformer, Axis axis, double previousValue,
            double currentValue, boolean xAxis) {
        if (transformer == null) {
            return true;
        }

        double currentTransformed;
        double previousTransformed;
        try {
            currentTransformed = transformer.apply(currentValue);
            previousTransformed = transformer.apply(previousValue);
        } catch (AxisTransformerException exception) {
            return false;
        }
        if (!Double.isFinite(currentTransformed) || !Double.isFinite(previousTransformed)) {
            return false;
        }

        DataInterval zoomRange = transformer.getZoomRange();
        double delta = currentTransformed - previousTransformed;
        double newMin = zoomRange.getMin() + delta;
        double newMax = zoomRange.getMax() + delta;
        DataInterval translatedZoomRange = new DataInterval(newMin, newMax);
        if (!isDraggingActiveRange(zoomRange, currentValue, delta)) {
            return true;
        }

        scrollToKeepZoomVisible(axis, translatedZoomRange, delta, xAxis);
        transformer.setZoomRange(newMin, newMax);
        return true;
    }

    private boolean isDraggingActiveRange(DataInterval zoomRange, double currentValue, double delta) {
        return zoomRange.isInside(currentValue)
                || (delta < 0.0 && zoomRange.getMin() > currentValue)
                || (delta > 0.0 && zoomRange.getMax() < currentValue);
    }

    private void scrollToKeepZoomVisible(Axis axis, DataInterval translatedZoomRange, double delta,
            boolean xAxis) {
        if (!axis.getDataRange().contains(translatedZoomRange)) {
            return;
        }

        if (delta < 0.0 && translatedZoomRange.getMin() < axis.getVisibleRange().getMin()) {
            double offset = translatedZoomRange.getMin() - axis.getVisibleRange().getMin();
            if (xAxis) {
                getChart().scroll(offset, 0.0, getYAxisIndex());
            } else {
                getChart().scroll(0.0, offset, getYAxisIndex());
            }
        } else if (delta > 0.0 && translatedZoomRange.getMax() > axis.getVisibleRange().getMax()) {
            double offset = translatedZoomRange.getMax() - axis.getVisibleRange().getMax();
            if (xAxis) {
                getChart().scroll(offset, 0.0, getYAxisIndex());
            } else {
                getChart().scroll(0.0, offset, getYAxisIndex());
            }
        }
    }
}
