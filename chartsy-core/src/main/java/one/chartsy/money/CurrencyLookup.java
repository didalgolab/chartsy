package one.chartsy.money;

import one.chartsy.Currency;
import org.openide.util.Lookup;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class CurrencyLookup implements CurrencyRegistry {

    protected final MutableCurrencyRegistry otherCurrencies = new MutableCurrencyRegistry();

    @Override
    public List<Currency> getCurrencies() {
        List<Currency> currencies = new ArrayList<>();
        for (var registry : Lookup.getDefault().lookupAll(CurrencyRegistry.class))
            if (registry != this)
                currencies.addAll(registry.getCurrencies());

        return currencies;
    }

    @Override
    public Optional<Currency> getCurrency(String currencyCode) {
        Optional<Currency> candidate;
        for (var registry : Lookup.getDefault().lookupAll(CurrencyRegistry.class))
            if (registry != this && (candidate = registry.getCurrency(currencyCode)).isPresent())
                return candidate;

        return Optional.of(createOtherCurrency(currencyCode));
    }

    protected Currency createOtherCurrency(String currencyCode) {
        var newCurrency = new Currency.Unit(currencyCode, currencyCode);
        return otherCurrencies.registerIfNotExists(newCurrency);
    }

    public static CurrencyLookup getDefault() {
        var lookup = Lookup.getDefault().lookup(CurrencyLookup.class);
        return (lookup == null)? LazyHolder.INSTANCE : lookup;
    }

    private static final class LazyHolder {
        private static final CurrencyLookup INSTANCE = new CurrencyLookup();
    }
}
