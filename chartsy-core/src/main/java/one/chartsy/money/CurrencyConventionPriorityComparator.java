package one.chartsy.money;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public class CurrencyConventionPriorityComparator implements Comparator<String> {
    public static final List<String> DEFAULT_ORDERING = List.of(
            "BTC", "XAU", "XAG", "EUR", "GBP", "AUD", "NZD", "USD", "CAD", "CHF", "JPY", "DKK", "NOK");

    private final List<String> ordering;

    public CurrencyConventionPriorityComparator() {
        this(DEFAULT_ORDERING);
    }

    public CurrencyConventionPriorityComparator(List<String> ordering) {
        this.ordering = Objects.requireNonNull(ordering);
    }

    @Override
    public int compare(String base, String counter) {
        int baseOrder = ordering.indexOf(base);
        int counterOrder = ordering.indexOf(counter);

        if (baseOrder == counterOrder)
            return base.compareTo(counter);
        else if (baseOrder < 0 || counterOrder < 0)
            return counterOrder - baseOrder;
        else
            return baseOrder - counterOrder;
    }
}
