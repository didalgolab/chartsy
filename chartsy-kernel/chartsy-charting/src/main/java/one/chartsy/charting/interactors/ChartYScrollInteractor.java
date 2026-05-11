package one.chartsy.charting.interactors;

import java.awt.event.KeyEvent;

import one.chartsy.charting.Axis;

/// Keyboard scroll interactor that translates one y axis of the active coordinate system.
///
/// The default binding uses the down and up arrow keys and moves by two logical steps per key
/// press. The computed step size comes from [ChartScrollInteractor] and is applied only to the
/// y axis selected by [#getYAxisIndex()], leaving the shared x axis unchanged.
public class ChartYScrollInteractor extends ChartScrollInteractor {

    /// Creates a y-axis scroll interactor for the primary y-axis slot.
    public ChartYScrollInteractor() {
        this(0, KeyEvent.VK_DOWN, KeyEvent.VK_UP, 2);
    }

    /// Creates a y-axis scroll interactor for one y-axis slot.
    ///
    /// @param yAxisIndex the y-axis slot whose coordinate system should be scrolled
    /// @param negativeDirectionKey key code that should scroll toward lower y values
    /// @param positiveDirectionKey key code that should scroll toward higher y values
    /// @param step logical step size to apply per key press
    public ChartYScrollInteractor(int yAxisIndex, int negativeDirectionKey, int positiveDirectionKey,
            int step) {
        super(yAxisIndex, negativeDirectionKey, positiveDirectionKey, step);
    }

    /// Returns the y axis selected by [#getYAxisIndex()].
    @Override
    protected Axis getAxis() {
        return getYAxis();
    }

    /// Scrolls the selected y axis by one computed delta in the requested direction.
    @Override
    protected void scroll(int direction) {
        double yOffset = (direction == NEGATIVE_DIR) ? -getDelta() : getDelta();
        getChart().scroll(0.0, yOffset, getYAxisIndex());
    }
}
