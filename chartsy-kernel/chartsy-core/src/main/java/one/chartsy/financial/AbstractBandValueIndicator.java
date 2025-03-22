/* Copyright 2025 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.financial;

import lombok.Getter;
import one.chartsy.financial.BandValueIndicator.BandValues;

/**
 * Abstract base class for indicators calculating dynamic upper and lower band values.
 *
 * <p>This class provides foundational state management and functionality shared by
 * multiple band-based indicator implementations. It maintains internal state for
 * tracking sentiment or positional sides (bullish, bearish, neutral) derived from
 * calculated band values.</p>
 *
 * @param <V> the type representing the pair of upper and lower band values
 * @author Mariusz Bernacki
 */
public abstract class AbstractBandValueIndicator<V extends BandValues> implements BandValueIndicator<V> {

    /** The current neutral-aware band side. */
    protected @Getter BandSide lastMode = BandSide.NEUTRAL;

    /** The previous neutral-aware band side. */
    protected @Getter BandSide previousMode = BandSide.NEUTRAL;

    /** The last non-neutral band side (either BULLISH or BEARISH). */
    protected @Getter BandSide lastSide = BandSide.NEUTRAL;

    /** The last non-neutral band side changed (i.e. BULLISH to BEARISH or vice versa). */
    protected @Getter boolean sideChanged;

    /**
     * Updates the band positional side (bullish, bearish, neutral) based on a given price.
     * The method calculates and sets:
     * <ul>
     *   <li>the most recent neutral-aware side: {@link #getLastMode()},</li>
     *   <li>the previous neutral-aware side: {@link #getPreviousMode()},</li>
     *   <li>the last non-neutral side: {@link #getLastSide()}.</li>
     * </ul>
     *
     * @param price the current price to evaluate against the upper and lower band values
     */
    protected void updateBandSide(double price) {
        this.previousMode = lastMode;

        if (price > getUpperBand()) {
            lastMode = BandSide.BULLISH;
        } else if (price < getLowerBand()) {
            lastMode = BandSide.BEARISH;
        } else {
            lastMode = BandSide.NEUTRAL;
        }

        if (lastMode.isNonNeutral()) {
            sideChanged = (lastMode != lastSide);
            lastSide = lastMode;
        }
    }
}
