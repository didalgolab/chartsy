/* Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.trade;

/**
 * Indicates whether a position is long or short.
 * 
 * @author Mariusz Bernacki
 * 
 */
public enum Direction {
    /** Represents off the market position. */
    FLAT(0),
    /** Represents a long position. */
    LONG(1),
    /** Represents a short position. */
    SHORT(-1);
    
    /** Internally used unique tag number of the direction constant. */
    public final int tag;
    
    Direction(int tag) {
        this.tag = tag;
    }
    
    /**
     * Reverses the current {@code Direction}.
     * 
     * @return the reversed direction
     */
    public final Direction reverse() {
        if (tag == 0)
            return this;
        return (tag > 0)? SHORT : LONG;
    }
}
