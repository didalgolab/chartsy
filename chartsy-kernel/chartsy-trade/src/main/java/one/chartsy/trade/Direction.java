/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.trade;

import one.chartsy.DirectionInformation;

/**
 * Indicates whether a position is long or short.
 * 
 * @author Mariusz Bernacki
 * 
 */
public enum Direction implements DirectionInformation {
    /** Represents off the market position. */
    FLAT(0),
    /** Represents a long position. */
    LONG(1),
    /** Represents a short position. */
    SHORT(-1);
    
    /** Internally used unique tag number of the direction constant. */
    private final int intValue;
    
    Direction(int intValue) {
        this.intValue = intValue;
    }

    @Override
    public boolean isLong() {
        return this == LONG;
    }

    @Override
    public boolean isShort() {
        return this == SHORT;
    }

    @Override
    public int intValue() {
        return intValue;
    }

    @Override
    public Direction reversed() {
        return switch (this) {
            case LONG -> SHORT;
            case SHORT -> LONG;
            default -> this;
        };
    }
}
