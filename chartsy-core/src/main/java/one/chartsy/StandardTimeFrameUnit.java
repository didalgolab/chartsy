/* Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy;

public enum StandardTimeFrameUnit implements TimeFrameUnit {
    /** Represents the certain number of seconds, minutes, hours, days or weeks expressed as seconds. */
    SECONDS ("Seconds", true, false),
    /** The certain number of months. */
    MONTHS ("Months", true, false),
    /** The certain number of ticks. */
    TICKS ("Ticks", true, false),
    /** The certain volume of trades. */
    VOLUME ("Volume", false, false),
    /** The certain price change. */
    PRICE ("Price", false, true),
    /** The certain price change percentage. */
    PRICE_PERCENTAGE ("Price Percentage", false, true),
    /** The certain price change using line break charts. */
    PRICE_LINE_BREAK ("Price Line Break Lines", false, true),
    /** The certain price change using renko bars. */
    PRICE_RENKO ("Price Renko", false, true)
    ;

    StandardTimeFrameUnit(String name, boolean eventBased, boolean priceBased) {
        this.name = name;
        this.eventBased = eventBased;
        this.priceBased = priceBased;
    }

    private final String name;
    private final boolean eventBased;
    private final boolean priceBased;

    @Override
    public boolean isEventBased() {
        return eventBased;
    }

    @Override
    public boolean isPriceBased() {
        return priceBased;
    }

    @Override
    public String toString() {
        return name;
    }
}
