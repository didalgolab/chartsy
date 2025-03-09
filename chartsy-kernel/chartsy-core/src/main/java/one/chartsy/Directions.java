/* Copyright 2025 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy;

/**
 * Defines possible directions for trading decisions: LONG, SHORT, ALL, NONE.
 * <p>
 * Each constant can verify if a given {@link DirectionInformation} matches its directional scope via
 * {@link #isAllowed(DirectionInformation)}.
 */
public enum Directions {
    LONG, SHORT, ALL, NONE;

    /**
     * Returns reversed directional state.
     *
     * @return reversed direction.
     */
    public Directions reversed() {
        return switch (this) {
            case LONG -> SHORT;
            case SHORT -> LONG;
            case ALL -> NONE;
            case NONE -> ALL;
        };
    }

    /**
     * Determines whether the given directional state is permissible under this directional scope.
     *
     * @param direction the directional state to check
     * @return {@code true} if allowed; {@code false} otherwise
     */
    public boolean isAllowed(DirectionInformation direction) {
        if (direction.isFlat() || this == ALL)
            return true;

        var isLong = (this == LONG);
        return isLong == direction.isLong() && !isLong == direction.isShort();
    }
}
