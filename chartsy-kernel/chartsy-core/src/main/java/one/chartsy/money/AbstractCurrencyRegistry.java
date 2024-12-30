/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.money;

import one.chartsy.Currency;

import java.util.*;

public abstract class AbstractCurrencyRegistry implements CurrencyRegistry {

    protected final Map<String, Currency> currencies;

    protected AbstractCurrencyRegistry(Map<String, Currency> currencies) {
        this.currencies = currencies;
    }

    public AbstractCurrencyRegistry(List<Currency> currencyList) {
        var currencies = new HashMap<String, Currency>();
        for (Currency currency : loadAllCurrencies(currencyList))
            currencies.put(currency.currencyCode(), currency);

        if (currencies.isEmpty())
            throw new IllegalArgumentException("currency list is empty");

        this.currencies = currencies;
    }

    protected List<Currency> loadAllCurrencies(List<Currency> candidates) {
        return candidates;
    }

    @Override
    public Optional<Currency> getCurrency(String currencyCode) {
        return Optional.ofNullable(currencies.get(currencyCode));
    }

    @Override
    public List<Currency> getCurrencies() {
        return List.copyOf(currencies.values());
    }

    public static abstract class EnumEnclosed extends AbstractCurrencyRegistry {

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
