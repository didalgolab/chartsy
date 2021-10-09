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
    /** The certain price change using renko bars. */
    PRICE_RENKO ("Price Renko", false, true)
    ;

    StandardTimeFrameUnit(String name, boolean eventBased, boolean priceBased) {
        this.displayName = name;
        this.eventBased = eventBased;
        this.priceBased = priceBased;
    }

    private final String displayName;
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
        return displayName;
    }
}
