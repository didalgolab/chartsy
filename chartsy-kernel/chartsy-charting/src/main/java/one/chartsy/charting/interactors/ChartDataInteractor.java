package one.chartsy.charting.interactors;

import java.awt.event.MouseEvent;

import one.chartsy.charting.ChartDataPicker;
import one.chartsy.charting.ChartInteractor;
import one.chartsy.charting.ChartRenderer;
import one.chartsy.charting.DefaultChartDataPicker;
import one.chartsy.charting.DisplayPoint;

/// Base class for interactors that resolve one [DisplayPoint] from a mouse location.
///
/// The class centralizes the picker setup shared by point-oriented interactors such as highlighting,
/// picking, and point editing. It always starts from the current mouse position, limits picks to
/// renderers attached to this interactor's y-axis slot, and then delegates the actual lookup to one
/// of the chart's built-in picking strategies selected by [#getPickingMode()].
///
/// ### API Note
///
/// Subclasses typically customize picking in one of two ways:
/// - override [#createDataPicker(MouseEvent)] to keep the same mode but narrow the eligible
///   renderers or change the distance metric
/// - switch [#setPickingMode(int)] between first-hit item picking, nearest-point picking, and
///   nearest-item picking
public abstract class ChartDataInteractor extends ChartInteractor {
    static final int DEFAULT_PICK_DISTANCE = 5;

    /// Picking mode that returns the first acceptable display item reported by the chart.
    public static final int ITEM_PICKING = 1;

    /// Picking mode that returns the nearest acceptable display point in display space.
    public static final int NEARESTPOINT_PICKING = 2;

    /// Picking mode that returns the nearest acceptable display item using renderer-defined
    /// distance.
    public static final int NEARESTITEM_PICKING = 3;

    private int pickingMode;

    /// Axis-filtered picker anchored to one display-space location.
    ///
    /// This picker keeps the standard fixed-anchor behavior from [DefaultChartDataPicker] but only
    /// accepts renderers assigned to the same y-axis slot as the enclosing interactor.
    public class DataPicker extends DefaultChartDataPicker {
        /// Creates one picker for a single mouse location.
        ///
        /// @param pickX        display-space x coordinate of the pick anchor
        /// @param pickY        display-space y coordinate of the pick anchor
        /// @param pickDistance maximum accepted distance from the pick anchor
        public DataPicker(int pickX, int pickY, int pickDistance) {
            super(pickX, pickY, pickDistance);
        }

        /// Accepts only renderers bound to the enclosing interactor's y-axis slot.
        @Override
        public boolean accept(ChartRenderer renderer) {
            return getYAxisIndex() == renderer.getYAxisNumber();
        }
    }

    /// Creates a data-oriented interactor for one y-axis slot and modifier combination.
    ///
    /// The default picking mode is [#NEARESTPOINT_PICKING].
    ///
    /// @param yAxisIndex y-axis slot whose renderers should participate in picks
    /// @param eventMask  legacy mouse modifier mask required to start the gesture
    public ChartDataInteractor(int yAxisIndex, int eventMask) {
        super(yAxisIndex, eventMask);
        pickingMode = NEARESTPOINT_PICKING;
    }

    /// Creates the picker used for one mouse event.
    ///
    /// The default implementation uses the event location and the shared
    /// [#DEFAULT_PICK_DISTANCE] threshold.
    ///
    /// @param event mouse event providing the display-space pick anchor
    /// @return picker to use for the current lookup
    protected ChartDataPicker createDataPicker(MouseEvent event) {
        return new DataPicker(event.getX(), event.getY(), DEFAULT_PICK_DISTANCE);
    }

    /// Returns the chart picking strategy currently used by [#pickData(ChartDataPicker)].
    ///
    /// @return one of [#ITEM_PICKING], [#NEARESTPOINT_PICKING], or [#NEARESTITEM_PICKING]
    public final int getPickingMode() {
        return pickingMode;
    }

    /// Resolves one display point according to the current picking mode.
    ///
    /// @param picker picker describing the pick anchor, renderer filter, and distance rules
    /// @return the selected display point, or `null` when nothing qualifies
    protected DisplayPoint pickData(ChartDataPicker picker) {
        return switch (getPickingMode()) {
            case ITEM_PICKING -> getChart().getDisplayItem(picker);
            case NEARESTPOINT_PICKING -> getChart().getNearestPoint(picker);
            case NEARESTITEM_PICKING -> getChart().getNearestItem(picker, null);
            default -> null;
        };
    }

    /// Convenience overload that builds a picker from `event` first.
    ///
    /// @param event mouse event providing the pick anchor
    /// @return the selected display point, or `null` when nothing qualifies
    protected final DisplayPoint pickData(MouseEvent event) {
        return pickData(createDataPicker(event));
    }

    /// Sets the chart picking strategy used by this interactor.
    ///
    /// @param pickingMode one of [#ITEM_PICKING], [#NEARESTPOINT_PICKING], or
    ///                        [#NEARESTITEM_PICKING]
    /// @throws IllegalArgumentException if `pickingMode` is not one of the supported constants
    public void setPickingMode(int pickingMode) {
        switch (pickingMode) {
            case ITEM_PICKING, NEARESTPOINT_PICKING, NEARESTITEM_PICKING -> this.pickingMode = pickingMode;
            default -> throw new IllegalArgumentException("invalid mode: " + pickingMode);
        }
    }
}
