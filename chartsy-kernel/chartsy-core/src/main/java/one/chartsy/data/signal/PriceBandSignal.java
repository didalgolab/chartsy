package one.chartsy.data.signal;

import one.chartsy.Candle;

import java.util.Objects;
import java.util.function.DoubleConsumer;

/**
 * A signal device that triggers separate actions when a market price
 * hits or crosses predefined upper or lower price bands, designed for market simulations.
 *
 * <p><b>Core Behavior:</b></p>
 * <ul>
 *   <li>Starts in an "idle" (disarmed) state.</li>
 *   <li>Must be explicitly "armed" by providing upper and lower price bands using {@link #arm(double, double)}.</li>
 *   <li>Once armed, it monitors high/low prices provided via {@link #poll(Candle)} (typically from a market {@code Bar}).</li>
 *   <li>When the high price meets or exceeds the upper band, the {@code upperBandAction} is executed with the effective trigger price.</li>
 *   <li>When the low price meets or falls below the lower band, the {@code lowerBandAction} is executed with the effective trigger price.</li>
 *   <li><b>Triggering Logic:</b> Each action (upper/lower) is executed <b>only once</b> per arming cycle.</li>
 *   <li><b>Gap Handling:</b> The price passed to the action accounts for potential price gaps relative to the band level (e.g., if price bar opens below the lower band, the action receives the bar's open as the effective exit price for that band) ensuring the trigger price reflects the earliest possible execution within the bar.</li>
 *   <li><b>Configuration:</b> Can be configured (via Builder) to either allow both bands to trigger independently or to automatically disarm the entire signal once the first band (either upper or lower) is hit.</li>
 *   <li>Can be explicitly disarmed using {@link #disarm()} or reset using {@link #reset()} (which clears trigger flags but keeps it armed if it was armed).</li>
 * </ul>
 *
 * <p><b>Effective Trigger Price Calculation:</b></p>
 * <ul>
 *     <li>If {@code high >= upperBand}: Effective Price = {@code Math.max(upperBand, open)}</li>
 *     <li>If {@code low <= lowerBand}: Effective Price = {@code Math.min(lowerBand, open)}</li>
 * </ul>
 *
 * <p><b>Example Usage (Builder):</b></p>
 * <pre>{@code
 * // Define actions that accept the trigger price
 * DoubleConsumer stopLossAction = price -> {
 *     System.out.println("Stop Loss Hit at effective price: " + price);
 *     // closePosition(price);
 * };
 * DoubleConsumer takeProfitAction = price -> {
 *     System.out.println("Take Profit Hit at effective price: " + price);
 *     // closePosition(price);
 * };
 *
 * // --- Scenario 1: Stop Loss OR Take Profit (first hit disarms) ---
 * PriceBandSignal slOrTpSignal = PriceBandSignal.builder()
 *                                               .lowerBandAction(stopLossAction)
 *                                               .upperBandAction(takeProfitAction)
 *                                               .triggerOnce() // Disarms after first hit
 *                                               .build();
 *
 * // --- Scenario 2: Independent OCO-like (One-Cancels-Other handled externally) ---
 * // Or for tracking independent band hits. Both can trigger if price swings wildly.
 * PriceBandSignal independentBandsSignal = PriceBandSignal.builder()
 *                                                         .lowerBandAction(stopLossAction)
 *                                                         .upperBandAction(takeProfitAction)
 *                                                         .triggerIndependently() // Default, but explicit
 *                                                         .build();
 *
 * // --- Inside the simulation ---
 * void setupTrade(double entryPrice, double stopDistance, double profitDistance) {
 *     double lowerBand = entryPrice - stopDistance;
 *     double upperBand = entryPrice + profitDistance;
 *     slOrTpSignal.arm(lowerBand, upperBand); // Arm the signal with specific bands
 *     // independentBandsSignal.arm(lowerBand, upperBand); // Arm the other signal if used
 * }
 *
 * void onBar(Candle bar) {
 *     // ... other logic ...
 *
 *     if (slOrTpSignal.poll(bar)) {
 *         System.out.println("SL/TP signal triggered on bar: " + bar.getTime());
 *         // The specific action already executed inside poll()
 *         // Since it's triggerOnce, it's now disarmed automatically.
 *     }
 *
 *     // If using independentBandsSignal:
 *     // if (independentBandsSignal.poll(bar)) {
 *     //     System.out.println("Independent signal triggered on bar: " + bar.getTime());
 *     //     // Check which band was hit if needed:
 *     //     if (independentBandsSignal.isLowerBandTriggeredThisPoll()) { ... } // Requires adding transient state if needed
 *     //     if (independentBandsSignal.isUpperBandTriggeredThisPoll()) { ... }
 *     // }
 *
 *     // Manually disarm if position closed for other reasons
 *     if (isPositionClosedExternally()) {
 *          slOrTpSignal.disarm();
 *          // independentBandsSignal.disarm();
 *     }
 * }
 * }</pre>
 *
 * <p>This class is <b>NOT</b> thread-safe so external synchronization is required if used
 * concurrently.</p>
 */
public final class PriceBandSignal {
    /** Static instance representing a no-operation action. */
    public static final DoubleConsumer NO_ACTION = value -> { /* do nothing */ };

    private final DoubleConsumer lowerBandAction;
    private final DoubleConsumer upperBandAction;
    private final boolean disarmOnFirstTrigger;

    private double upperBand = Double.POSITIVE_INFINITY;
    private double lowerBand = Double.NEGATIVE_INFINITY;
    private boolean isArmed;
    private boolean upperBandTriggered;
    private boolean lowerBandTriggered;

    /**
     * Private constructor.
     */
    private PriceBandSignal(Builder builder) {
        this.lowerBandAction = builder.lowerBandAction;
        this.upperBandAction = builder.upperBandAction;
        this.disarmOnFirstTrigger = builder.disarmOnFirstTrigger;
    }

    // --- Factory Methods ---

    /**
     * Creates a Builder to configure and create a {@code PriceBandSignal}.
     *
     * @return a new {@link Builder} instance
     * @throws NullPointerException if either action is {@code null}
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates a new disarmed {@code PriceBandSignal} with default settings:
     * <ul>
     *     <li>Both {@code lowerBandAction} and {@code upperBandAction} are set to {@link #NO_ACTION}.</li>
     *     <li>The signal is configured to {@link Builder#triggerIndependently()}.</li>
     * </ul>
     * For custom configurations use {@link #builder()} method instead.
     *
     * @return a new {@code PriceBandSignal} instance with default configuration
     * @see #builder()
     */
    public static PriceBandSignal create() {
        return builder().build();
    }

    /**
     * Builder for configuring {@link PriceBandSignal}.
     */
    public static class Builder {
        private DoubleConsumer lowerBandAction = NO_ACTION;
        private DoubleConsumer upperBandAction = NO_ACTION;
        private boolean disarmOnFirstTrigger;

        private Builder() { }

        /**
         * Sets the action to be executed when the lower price band is triggered.
         *
         * @param lowerBandAction the action to perform, accepting the trigger price
         * @return this Builder instance for chaining
         * @throws NullPointerException if lowerBandAction is null
         */
        public Builder lowerBandAction(DoubleConsumer lowerBandAction) {
            this.lowerBandAction = Objects.requireNonNull(lowerBandAction, "lowerBandAction cannot be null");
            return this;
        }

        /**
         * Sets the action to be executed when the upper price band is triggered.
         *
         * @param upperBandAction the action to perform, accepting the trigger price
         * @return this Builder instance for chaining
         * @throws NullPointerException if upperBandAction is null
         */
        public Builder upperBandAction(DoubleConsumer upperBandAction) {
            this.upperBandAction = Objects.requireNonNull(upperBandAction, "upperBandAction cannot be null");
            return this;
        }

        /**
         * Configures the signal to automatically disarm itself after the <i>first</i> band
         * (either upper or lower) is triggered. Useful for Stop-Loss/Take-Profit scenarios
         * where the first hit resolves the trade.
         *
         * @return this Builder instance for chaining
         */
        public Builder triggerOnce() {
            this.disarmOnFirstTrigger = true;
            return this;
        }

        /**
         * Configures the signal to allow both the upper and lower bands to trigger
         * independently within the same arming cycle (if price action allows).
         * This is the default behavior.
         *
         * @return this Builder instance for chaining
         */
        public Builder triggerIndependently() {
            this.disarmOnFirstTrigger = false;
            return this;
        }

        /**
         * Builds the configured {@link PriceBandSignal} instance.
         * The signal starts in a disarmed state.
         *
         * @return a new {@code PriceBandSignal}
         */
        public PriceBandSignal build() {
            return new PriceBandSignal(this);
        }
    }

    // --- State Management ---

    /**
     * Arms the signal with new price bands.
     * If the signal was already armed, this effectively replaces the old bands.
     * This action also implicitly calls {@link #reset()}, clearing any previously triggered flags.
     * The signal becomes active and ready to be polled.
     *
     * @param newLowerBand the lower price band threshold
     * @param newUpperBand the upper price band threshold
     * @throws IllegalArgumentException if {@code newUpperBand <= newLowerBand}
     */
    public void arm(double newLowerBand, double newUpperBand) {
        if (newUpperBand <= newLowerBand) {
            throw new IllegalArgumentException("Upper band (" + newUpperBand + ") must be strictly greater than lower band (" + newLowerBand + ").");
        }
        this.lowerBand = newLowerBand;
        this.upperBand = newUpperBand;
        this.isArmed = true;
        resetTriggerFlags();
    }

    /**
     * Convenience method to arm the signal with bands calculated relative to an entry price.
     * This is typically used for setting a stop-loss below the entry and a take-profit above the entry.
     * <p>
     * Calculates:
     * <ul>
     * <li>{@code lowerBand = entryPrice - lowerDistance}</li>
     * <li>{@code upperBand = entryPrice + upperDistance}</li>
     * </ul>
     * Then calls {@link #arm(double, double)} with the calculated bands.
     *
     * @param entryPrice the reference entry price
     * @param lowerDistance the positive distance below the entry price for the lower band (e.g. stop-loss)
     * @param upperDistance the positive distance above the entry price for the upper band (e.g. take-profit)
     * @throws IllegalArgumentException if {@code lowerDistance <= 0} or {@code upperDistance <= 0},
     * or if the resulting upper band is not strictly greater than the lower band (which
     * shouldn't happen if distances are positive).
     */
    public void armAroundEntry(double entryPrice, double lowerDistance, double upperDistance) {
        if (lowerDistance <= 0)
            throw new IllegalArgumentException("lowerDistance must be positive, but was: " + lowerDistance);
        if (upperDistance <= 0)
            throw new IllegalArgumentException("upperDistance must be positive, but was: " + upperDistance);

        arm(entryPrice - lowerDistance, entryPrice + upperDistance);
    }

    /**
     * Convenience method to arm the signal with symmetric bands around an entry price.
     * Both lower and upper distances are set to {@code entryDistance}.
     *
     * <p>Equivalent to: {@code armAroundEntry(entryPrice, entryDistance, entryDistance)}
     *
     * @param entryPrice the reference entry price
     * @param entryDistance the positive distance above and below the entry price for the bands
     * @throws IllegalArgumentException if {@code entryDistance <= 0}
     */
    public void armAroundEntry(double entryPrice, double entryDistance) {
        armAroundEntry(entryPrice, entryDistance, entryDistance);
    }

    /**
     * Disarms the signal, making it inactive.
     * {@link #poll(Candle)} will have no effect while disarmed.
     * Triggered flags are NOT reset by this method.
     */
    public void disarm() {
        this.isArmed = false;
    }

    /**
     * Resets the triggered state for both bands <strong>without</strong> changing the armed state or the band levels.
     * If the signal is currently armed, this allows the actions to be triggered again by subsequent polls
     * hitting the <i>current</i> bands. If the signal is disarmed, this method only resets the internal flags
     * but keeps it disarmed.
     */
    public void reset() {
        resetTriggerFlags();
    }

    /** Internal helper to reset trigger flags. */
    private void resetTriggerFlags() {
        this.upperBandTriggered = false;
        this.lowerBandTriggered = false;
    }

    // --- Polling ---

    /**
     * Polls the signal with the {@code Candle} event.
     * Executes the corresponding action if an untriggered band is hit, respecting the
     * {@code disarmOnFirstTrigger} configuration and calculating the effective trigger price.
     *
     * @param bar the market bar event
     * @return {@code true} if either band's action was triggered <i>during this specific poll call</i>,
     *         {@code false} otherwise.
     * @throws NullPointerException if {@code bar} is null
     */
    public boolean poll(Candle bar) {
        double high = bar.high();
        double low = bar.low();
        boolean actionTriggered = false;

        // Check upper band first
        if (isArmed && !upperBandTriggered && high >= upperBand) {
            // Calculate effective price considering gap potential
            double effectivePrice = Math.max(upperBand, bar.open());
            try {
                upperBandAction.accept(effectivePrice);
            } finally {
                // IMPORTANT: Update state even if action throws exception
                upperBandTriggered = actionTriggered = true;
                if (disarmOnFirstTrigger)
                    isArmed = false;
            }
        }

        // Check lower band ONLY if still armed (might have been disarmed by upper band)
        // and lower band hasn't already been triggered in a previous poll.
        if (isArmed && !lowerBandTriggered && low <= lowerBand) {
            // Calculate effective price considering gap potential
            double effectivePrice = Math.min(lowerBand, bar.open());
            try {
                lowerBandAction.accept(effectivePrice);
            } finally {
                // IMPORTANT: Update state even if action throws exception
                lowerBandTriggered = actionTriggered = true;
                if (disarmOnFirstTrigger)
                    isArmed = false;
            }
        }

        return actionTriggered;
    }

    // --- State Query ---

    /**
     * Checks if the signal is currently armed and active.
     *
     * @return {@code true} if armed, {@code false} if disarmed (idle)
     */
    public boolean isArmed() {
        return isArmed;
    }

    /**
     * Checks if the upper band has been triggered at any point since the last arming or reset.
     * Note: This reflects historical state, not whether it triggered <i>in the last poll</i>.
     *
     * @return {@code true} if the upper band action has been executed, {@code false} otherwise
     */
    public boolean isUpperBandTriggered() {
        return upperBandTriggered;
    }

    /**
     * Checks if the lower band has been triggered at any point since the last arming or reset.
     * Note: This reflects historical state, not whether it triggered <i>in the last poll</i>.
     *
     * @return {@code true} if the lower band action has been executed, {@code false} otherwise
     */
    public boolean isLowerBandTriggered() {
        return lowerBandTriggered;
    }

    /**
     * Gets the configured upper band price level.
     * Returns {@code Double.POSITIVE_INFINITY} if the signal has never been armed.
     *
     * @return the upper band price, or positive infinity if not set
     */
    public double getUpperBand() {
        return upperBand;
    }

    /**
     * Gets the configured lower band price level.
     * Returns {@code Double.NEGATIVE_INFINITY} if the signal has never been armed.
     *
     * @return the lower band price, or negative infinity if not set
     */
    public double getLowerBand() {
        return lowerBand;
    }

    @Override
    public String toString() {
        return String.format("PriceBandSignal[armed=%b, lowerBand=%f, upperBand=%f, lowerTriggered=%b, upperTriggered=%b]",
                isArmed, lowerBand, upperBand, lowerBandTriggered, upperBandTriggered);
    }
}