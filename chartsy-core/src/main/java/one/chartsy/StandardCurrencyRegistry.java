package one.chartsy;

import java.util.*;

public class StandardCurrencyRegistry implements CurrencyRegistry {

    private final Map<String, Currency> currencies;
    private final List<Currency> currencyList;

    public StandardCurrencyRegistry(List<Currency> currencyList) {
        var currencies = new HashMap<String, Currency>();
        for (Currency currency : loadAllCurrencies(currencyList))
            currencies.put(currency.currencyCode(), currency);

        if (currencies.isEmpty())
            throw new IllegalArgumentException("currency list is empty");

        this.currencies = currencies;
        this.currencyList = List.copyOf(currencyList);
    }

    protected List<Currency> loadAllCurrencies(List<Currency> candidates) {
        return candidates;
    }

    @Override
    public Currency getCurrency(String currencyCode) {
        return currencies.get(currencyCode);
    }

    @Override
    public List<Currency> getCurrencies() {
        return currencyList;
    }

    public static abstract class EnumEnclosed extends StandardCurrencyRegistry {

        protected EnumEnclosed() {
            super(new ArrayList<>());
        }

        protected <T extends Enum<T> & Currency> EnumEnclosed(Class<T> currencies) {
            super(Arrays.asList(currencies.getEnumConstants()));
        }

        @Override
        protected List<Currency> loadAllCurrencies(List<Currency> seed) {
            if (seed.isEmpty() && seed instanceof ArrayList)
                Collections.addAll(seed, findEnclosingEnum().getEnumConstants());

            return super.loadAllCurrencies(seed);
        }

        @SuppressWarnings("unchecked")
        protected <T extends Enum<T> & Currency> Class<T> findEnclosingEnum() {
            var enclosingType = getClass().getEnclosingClass();
            if (enclosingType == null)
                throw new UnsupportedOperationException("Enclosing enum not found for registry " + getClass().getName());
            if (!enclosingType.isEnum())
                throw new UnsupportedOperationException("Enclosing " + getClass() + " is not an enum");
            if (!Currency.class.isAssignableFrom(enclosingType))
                throw new UnsupportedOperationException("Enclosing " + getClass() + " is not a currency");

            return (Class<T>) enclosingType;
        }
    }
}
