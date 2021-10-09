package one.chartsy.incubator;

// alt name: PricingComponent
public interface PriceDomain {

    String name();

    enum Component implements PriceDomain {
        BID, ASK, MIDPOINT, LAST_TRADE
    }
}
