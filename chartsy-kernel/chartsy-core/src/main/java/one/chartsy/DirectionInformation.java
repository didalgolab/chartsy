/* Copyright 2025 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy;

/**
 * A generalized interface providing insight for a directional trading state.
 * <p>
 * Implementations encapsulate positional attributes (e.g. long, short, flat) 
 * and offer detailed inspection and analytical properties for directional decisions.
 * This abstraction promotes loose coupling, versatility, and generalized directionality 
 * semantics applicable across diverse trading algorithms and market strategies.
 *
 * @author Mariusz Bernacki
 *
 */
public interface DirectionInformation {

    /**
     * Verifies whether the directional state is neutral or off-market.
     * 
     * @return {@code true} if the direction is flat; {@code false} if it is either long or short
     */
    default boolean isFlat() {
        return !isLong() && !isShort();
    }

    /**
     * Indicates whether the direction is positively biased.
     *
     * @return {@code true} if the direction is long; {@code false} otherwise.
     */
    boolean isLong();

    /**
     * Indicates whether the direction is negatively biased.
     *
     * @return {@code true} if the direction is short; {@code false} otherwise.
     */
    boolean isShort();

    /**
     * Unique numeric tag identifying the direction.
     *
     * @return the direction tag
     */
    int intValue();

    /**
     * Returns the inversed (or mirrored) directional state.
     *
     * @return the inverted direction
     */
    DirectionInformation reversed();

}
