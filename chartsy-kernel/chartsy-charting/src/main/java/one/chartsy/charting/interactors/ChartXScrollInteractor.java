package one.chartsy.charting.interactors;

import java.awt.event.KeyEvent;

import one.chartsy.charting.Axis;

/// Keyboard scroll interactor that translates the chart along the shared x axis.
///
/// The default binding uses the left and right arrow keys and moves by two logical steps per key
/// press. The exact chart-space distance of one step is inherited from [ChartScrollInteractor] and
/// depends on the active x-axis scale.
public class ChartXScrollInteractor extends ChartScrollInteractor {

    /// Creates an x-axis scroll interactor for the primary y-axis slot.
    public ChartXScrollInteractor() {
        this(0, KeyEvent.VK_LEFT, KeyEvent.VK_RIGHT, 2);
    }

    /// Creates an x-axis scroll interactor for one y-axis slot.
    ///
    /// @param yAxisIndex the y-axis slot whose coordinate system should be scrolled
    /// @param negativeDirectionKey key code that should scroll left in data space
    /// @param positiveDirectionKey key code that should scroll right in data space
    /// @param step logical step size to apply per key press
    public ChartXScrollInteractor(int yAxisIndex, int negativeDirectionKey, int positiveDirectionKey,
            int step) {
        super(yAxisIndex, negativeDirectionKey, positiveDirectionKey, step);
    }

    /// Returns the chart's shared x axis.
    @Override
    protected Axis getAxis() {
        return getXAxis();
    }

    /// Scrolls the x axis by one computed delta in the requested direction.
    @Override
    protected void scroll(int direction) {
        double xOffset = (direction == NEGATIVE_DIR) ? -getDelta() : getDelta();
        getChart().scroll(xOffset, 0.0, getYAxisIndex());
    }
}
