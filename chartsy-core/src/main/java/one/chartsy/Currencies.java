package one.chartsy;

import org.openide.util.Lookup;

public class Currencies {

    public static Currency getCurrency(String currencyCode) {
        Currency currency;
        for (CurrencyRegistry registry : Lookup.getDefault().lookupAll(CurrencyRegistry.class))
            if ((currency = registry.getCurrency(currencyCode)) != null)
                return currency;

        return createOtherCurrency(currencyCode);
    }

    protected static Currency.Unit createOtherCurrency(String currencyCode) {
        return new Currency.Unit(currencyCode, currencyCode, 0);
    }
}
