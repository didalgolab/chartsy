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

    /** The current band side. */
    protected @Getter BandSide lastSide = BandSide.NEUTRAL;

    /** The previous band side. */
    protected @Getter BandSide previousSide = BandSide.NEUTRAL;

    /** The last non-neutral band side (either BULLISH or BEARISH). */
    protected @Getter BandSide lastNonNeutralSide = BandSide.NEUTRAL;

    /** The last non-neutral band side changed (i.e. BULLISH to BEARISH or vice versa). */
    protected @Getter boolean nonNeutralSideChanged;

    /**
     * Updates the band positional side (bullish, bearish, neutral) based on a given price.
     * The method calculates and sets:
     * <ul>
     *   <li>the most recent side: {@link #getLastSide()},</li>
     *   <li>the previous side: {@link #getPreviousSide()},</li>
     *   <li>the last non-neutral side: {@link #getLastNonNeutralSide()}.</li>
     * </ul>
     *
     * @param price the current price to evaluate against the upper and lower band values
     */
    protected void updateBandSide(double price) {
        this.previousSide = lastSide;

        if (price > getUpperBand()) {
            lastSide = BandSide.BULLISH;
        } else if (price < getLowerBand()) {
            lastSide = BandSide.BEARISH;
        } else {
            lastSide = BandSide.NEUTRAL;
        }

        if (lastSide != BandSide.NEUTRAL) {
            nonNeutralSideChanged = (lastSide != lastNonNeutralSide);
            lastNonNeutralSide = lastSide;
        }
    }
}
