/* Copyright 2021 by Mariusz Bernacki. PROPRIETARY and CONFIDENTIAL content.
 * Unauthorized copying of this file, via any medium is strictly prohibited.
 * See the file "LICENSE.txt" for the full license governing this code. */
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
