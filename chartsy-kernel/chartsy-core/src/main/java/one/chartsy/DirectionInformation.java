/* Copyright 2025 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy;

import one.chartsy.context.Directional;

/**
 * A specialized interface providing detailed insights about a directional trading state.
 * <p>
 * Extending {@link Directional}, implementations enrich numeric direction encoding
 * with methods allowing explicit directional checks and the ability to reverse direction.
 * This facilitates expressive, clear, and maintainable trading logic in algorithms and analytics.
 *
 * @author Mariusz Bernacki
 */
public interface DirectionInformation extends Directional {

    /**
     * Checks if the current directional state is neutral or off-market.
     *
     * @return {@code true} if direction is flat; {@code false} if biased (long or short)
     */
    default boolean isFlat() {
        return !isLong() && !isShort();
    }

    /**
     * Checks if the direction is positively biased (e.g., long).
     *
     * @return {@code true} if the direction is long; {@code false} otherwise.
     */
    boolean isLong();

    /**
     * Checks if the direction is negatively biased (e.g., short).
     *
     * @return {@code true} if the direction is short; {@code false} otherwise.
     */
    boolean isShort();

    /**
     * Returns a new directional instance representing the opposite state.
     * <p>
     * For example, long becomes short, short becomes long, and flat remains flat.
     *
     * @return the inverted directional state
     */
    DirectionInformation reversed();
}
