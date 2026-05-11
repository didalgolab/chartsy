package one.chartsy.charting.interactors;

import java.awt.AWTEvent;
import java.awt.event.KeyEvent;

import one.chartsy.charting.Axis;
import one.chartsy.charting.CategoryStepsDefinition;
import one.chartsy.charting.ChartInteractor;
import one.chartsy.charting.DefaultStepsDefinition;
import one.chartsy.charting.Scale;
import one.chartsy.charting.StepsDefinition;
import one.chartsy.charting.TimeStepsDefinition;

/// Base class for keyboard-driven scrolling along one chart axis.
///
/// Subclasses supply the concrete axis through [#getAxis()] and map a logical direction constant
/// to a chart scroll operation in [#scroll(int)]. Every matching key press scrolls by one delta and
/// marks both axes as adjusting until the corresponding key release arrives. That lets renderers
/// and listeners defer expensive work while the user is holding an auto-repeating key.
///
/// [#getDelta()] derives that step from the active [Scale] when possible:
/// - [DefaultStepsDefinition] uses one numeric step unit per configured [#getStep()]
/// - [TimeStepsDefinition] uses one time-unit duration per configured [#getStep()]
/// - [CategoryStepsDefinition] uses raw category counts
/// - all other scales fall back to a percentage of the current visible range length
///
/// Instances are mutable UI objects and are not thread-safe.
public abstract class ChartScrollInteractor extends ChartInteractor {
    public static final int POSITIVE_DIR = 1;
    public static final int NEGATIVE_DIR = 2;

    private int positiveDirectionKey;
    private int negativeDirectionKey;
    private int step;

    /// Creates a scroll interactor for the primary y-axis slot.
    ///
    /// @param negativeDirectionKey key code that should scroll in [#NEGATIVE_DIR]
    /// @param positiveDirectionKey key code that should scroll in [#POSITIVE_DIR]
    /// @param step logical step size to apply per key press
    public ChartScrollInteractor(int negativeDirectionKey, int positiveDirectionKey, int step) {
        this(0, negativeDirectionKey, positiveDirectionKey, step);
    }

    /// Creates a scroll interactor for one y-axis slot.
    ///
    /// @param yAxisIndex the y-axis slot whose coordinate system should be scrolled
    /// @param negativeDirectionKey key code that should scroll in [#NEGATIVE_DIR]
    /// @param positiveDirectionKey key code that should scroll in [#POSITIVE_DIR]
    /// @param step logical step size to apply per key press
    public ChartScrollInteractor(int yAxisIndex, int negativeDirectionKey, int positiveDirectionKey,
            int step) {
        super(yAxisIndex, 0);
        this.negativeDirectionKey = negativeDirectionKey;
        this.positiveDirectionKey = positiveDirectionKey;
        this.step = step;
        enableEvents(AWTEvent.KEY_EVENT_MASK);
        setXORGhost(false);
    }

    /// Returns the axis this interactor scrolls.
    protected abstract Axis getAxis();

    /// Computes the chart-space distance scrolled by one key press.
    ///
    /// The exact meaning of [#getStep()] depends on the current scale's steps definition.
    ///
    /// @return the signed-magnitude delta that subclasses should apply for one scroll increment
    protected double getDelta() {
        double delta = getAxis().getVisibleRange().getLength() * getStep() / 100.0;
        Scale scale = getChart().getScale(getAxis());
        if (scale == null) {
            return delta;
        }

        StepsDefinition stepsDefinition = scale.getStepsDefinition();
        if (stepsDefinition instanceof DefaultStepsDefinition defaultStepsDefinition) {
            return defaultStepsDefinition.getStepUnit() * getStep();
        }
        if (stepsDefinition instanceof TimeStepsDefinition timeStepsDefinition) {
            return timeStepsDefinition.getUnit().getMillis() * getStep();
        }
        if (stepsDefinition instanceof CategoryStepsDefinition) {
            return getStep();
        }
        return delta;
    }

    /// Returns the key code that scrolls in [#NEGATIVE_DIR].
    public final int getNegativeDirectionKey() {
        return negativeDirectionKey;
    }

    /// Returns the key code that scrolls in [#POSITIVE_DIR].
    public final int getPositiveDirectionKey() {
        return positiveDirectionKey;
    }

    /// Returns the logical step size applied per key press.
    public final int getStep() {
        return step;
    }

    @Override
    public void processKeyEvent(KeyEvent event) {
        int keyCode = event.getKeyCode();
        if (keyCode != negativeDirectionKey && keyCode != positiveDirectionKey) {
            return;
        }

        switch (event.getID()) {
            case KeyEvent.KEY_PRESSED -> {
                getXAxis().setAdjusting(true);
                getYAxis().setAdjusting(true);
                scroll((keyCode != getNegativeDirectionKey()) ? POSITIVE_DIR : NEGATIVE_DIR);
                if (isConsumeEvents()) {
                    event.consume();
                }
            }
            case KeyEvent.KEY_RELEASED -> {
                getXAxis().setAdjusting(false);
                getYAxis().setAdjusting(false);
                if (isConsumeEvents()) {
                    event.consume();
                }
            }
            default -> {
            }
        }
    }

    /// Applies one scroll increment in the requested logical direction.
    ///
    /// @param direction either [#POSITIVE_DIR] or [#NEGATIVE_DIR]
    protected abstract void scroll(int direction);

    /// Replaces the key code that scrolls in [#NEGATIVE_DIR].
    ///
    /// @param negativeDirectionKey new key code for the negative direction
    public final void setNegativeDirectionKey(int negativeDirectionKey) {
        this.negativeDirectionKey = negativeDirectionKey;
    }

    /// Replaces the key code that scrolls in [#POSITIVE_DIR].
    ///
    /// @param positiveDirectionKey new key code for the positive direction
    public final void setPositiveDirectionKey(int positiveDirectionKey) {
        this.positiveDirectionKey = positiveDirectionKey;
    }

    /// Replaces the logical step size applied per key press.
    ///
    /// @param step new logical step size
    public final void setStep(int step) {
        this.step = step;
    }
}
