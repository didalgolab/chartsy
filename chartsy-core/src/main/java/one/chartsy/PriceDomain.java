package one.chartsy;

public interface PriceDomain {

    String name();

    record Of(String name) implements PriceDomain {
        public static final PriceDomain MIDPOINT = new Of("MIDPOINT");
        public static final PriceDomain LAST_TRADE = new Of("LAST_TRADE");
    }
}
