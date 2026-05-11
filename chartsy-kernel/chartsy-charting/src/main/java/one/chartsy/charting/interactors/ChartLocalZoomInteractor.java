package one.chartsy.charting.interactors;

import java.awt.event.MouseEvent;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serial;

import one.chartsy.charting.AxisTransformer;
import one.chartsy.charting.DataInterval;
import one.chartsy.charting.DataWindow;
import one.chartsy.charting.LocalZoomAxisTransformer;

/// Zoom interactor that adjusts [LocalZoomAxisTransformer] lenses instead of the chart's global
/// visible ranges.
///
/// A drag may start only inside the current local-zoom range of the x transformer, the y
/// transformer, or both. The selected box is clamped to those zoom ranges and, on release,
/// converted into a new `zoomFactor` for each participating transformer. The chart's visible
/// ranges therefore stay unchanged; only the local lens magnification changes.
///
/// Axes without an active [LocalZoomAxisTransformer] are ignored. This class reuses
/// [ChartZoomInteractor]'s drag-box mechanics and cursor handling while swapping the final
/// whole-chart zoom for transformer updates.
public class ChartLocalZoomInteractor extends ChartZoomInteractor {
    private static final int ACTIVE_X_AXIS = 1;
    private static final int ACTIVE_Y_AXIS = 2;
    private static final int DEFAULT_ZOOM_IN_EVENT_MASK = 16;
    private static final int DEFAULT_ZOOM_OUT_EVENT_MASK = 17;

    private transient int activeAxesMask;

    /// Creates a local zoom interactor for the primary y-axis slot with the default zoom bindings.
    public ChartLocalZoomInteractor() {
        this(0, DEFAULT_ZOOM_IN_EVENT_MASK, DEFAULT_ZOOM_OUT_EVENT_MASK);
    }

    /// Creates a local zoom interactor for one y-axis slot and two drag gestures.
    ///
    /// @param yAxisIndex the y-axis slot whose local zoom transformers should be updated
    /// @param zoomInEventMask mouse modifier mask that should start zoom-in drags
    /// @param zoomOutEventMask mouse modifier mask that should start zoom-out drags
    public ChartLocalZoomInteractor(int yAxisIndex, int zoomInEventMask, int zoomOutEventMask) {
        super(yAxisIndex, zoomInEventMask, zoomOutEventMask);
        initTransientState();
        setXZoomAllowed(false);
    }

    private void initTransientState() {
        activeAxesMask = 0;
    }

    private void clampToZoomRange(DataInterval interval, LocalZoomAxisTransformer transformer) {
        if (transformer != null) {
            DataInterval zoomRange = transformer.getZoomRange();
            if (!zoomRange.isInside(interval.getMin())) {
                interval.setMin(zoomRange.getMin());
            }
            if (!zoomRange.isInside(interval.getMax())) {
                interval.setMax(zoomRange.getMax());
            }
        }
    }

    private void updateZoomFactor(DataInterval selectedRange, LocalZoomAxisTransformer transformer) {
        if (transformer == null) {
            return;
        }

        DataInterval zoomRange = transformer.getZoomRange();
        if (zoomRange.contains(selectedRange)) {
            double ratio = zoomRange.getLength() / selectedRange.getLength();
            double zoomFactor = !isZoomingOut()
                    ? transformer.getZoomFactor() * ratio
                    : transformer.getZoomFactor() / ratio;
            if (zoomFactor < 1.0) {
                zoomFactor = 1.0;
            }
            transformer.setZoomFactor(zoomFactor);
        }
    }

    @Override
    protected void abort() {
        super.abort();
        activeAxesMask = 0;
        setXZoomAllowed(false);
        setYZoomAllowed(false);
    }

    /// Applies the completed drag box as a new local zoom factor on every active axis.
    ///
    /// Zoom-in gestures multiply the current factor by the ratio between the full local range and
    /// the selected subrange. Zoom-out gestures divide by that same ratio. Factors never fall
    /// below `1.0`.
    @Override
    protected void doIt() {
        DataWindow draggedWindow = getDraggedWindow();
        updateZoomFactor(draggedWindow.xRange, getXTransformer());
        updateZoomFactor(draggedWindow.yRange, getYTransformer());
    }

    @Override
    protected void endOperation(MouseEvent event) {
        super.endOperation(event);
        activeAxesMask = 0;
        setXZoomAllowed(false);
        setYZoomAllowed(false);
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

    /// Returns whether a local zoom drag may start from the supplied data-space location.
    ///
    /// The method also records which axes should participate in the current gesture and enables
    /// their inherited x/y zoom flags for the duration of the drag.
    ///
    /// @param x data-space x coordinate of the press location
    /// @param y data-space y coordinate of the press location
    /// @return `true` when at least one local zoom range contains the start point
    @Override
    protected boolean isValidStartPoint(double x, double y) {
        activeAxesMask = 0;
        setXZoomAllowed(false);
        setYZoomAllowed(false);

        LocalZoomAxisTransformer xTransformer = getXTransformer();
        if (xTransformer != null && xTransformer.getZoomRange().isInside(x)) {
            activeAxesMask |= ACTIVE_X_AXIS;
            setXZoomAllowed(true);
        }

        LocalZoomAxisTransformer yTransformer = getYTransformer();
        if (yTransformer != null && yTransformer.getZoomRange().isInside(y)) {
            activeAxesMask |= ACTIVE_Y_AXIS;
            setYZoomAllowed(true);
        }

        return activeAxesMask != 0;
    }

    @Serial
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        initTransientState();
    }

    /// Clamps the inherited drag box to the currently active local zoom ranges.
    ///
    /// The inherited axis-lock logic has already run before this hook is invoked.
    ///
    /// @param window the mutable drag window about to be painted or applied
    @Override
    protected void validate(DataWindow window) {
        super.validate(window);
        if ((activeAxesMask & ACTIVE_X_AXIS) != 0) {
            clampToZoomRange(window.xRange, getXTransformer());
        }
        if ((activeAxesMask & ACTIVE_Y_AXIS) != 0) {
            clampToZoomRange(window.yRange, getYTransformer());
        }
    }
}
