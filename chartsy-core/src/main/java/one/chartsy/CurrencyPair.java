/* Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy;

import java.io.Serializable;
import java.text.ParseException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * An ordered pair of currencies.  Such as {@code EUR/USD}.
 */
public final class CurrencyPair implements Comparable<CurrencyPair>, Serializable {
    private final String baseCurrency;
    private final String currency;

    private CurrencyPair(String baseCurrency, String currency) {
        Objects.requireNonNull(baseCurrency, "baseCurrency");
        Objects.requireNonNull(currency, "currency");
        this.baseCurrency = baseCurrency;
        this.currency = currency;
    }

    /**
     * Obtains a currency pair from two currencies.
     *
     * @param baseCurrency the base currency
     * @param currency the quote currency
     * @return the currency pair
     */
    public static CurrencyPair of(String baseCurrency, String currency) {
        return new CurrencyPair(baseCurrency, currency);
    }

    /**
     * Checks if the current currency pair is the inverse of the specified one.
     *
     * @param other the currency pair to test
     * @return {@code true} if the {@code other} is inverse of {@code this}
     */
    public boolean isInverse(CurrencyPair other) {
        return inverse().equals(other);
    }

    /**
     * Checks if the currency pair is an identity pair.
     *
     * @return {@code true} if base and quote currencies in the pair are the same
     */
    public boolean isIdentity() {
        return baseCurrency.equals(currency);
    }

    /**
     * Parses a currency pair from the given string.
     * Example supported formats are:
     * <ul>
     *     <li>EURUSD</li>
     *     <li>EUR/USD</li>
     *     <li>EUR.USD</li>
     *     <li>EUR-USD</li>
     *     <li>and others</li>
     * </ul>
     *
     * @param currencyPairCode the code of currency pair to parse
     * @return the currency pair
     * @throws ParseException if the pair cannot be parsed
     */
    public static CurrencyPair parse(String currencyPairCode) throws ParseException {
        int delimPos = -1;
        for (int i = 0; i < currencyPairCode.length(); i++) {
            if (!Character.isLetterOrDigit(currencyPairCode.charAt(i))) {
                if (delimPos != -1)
                    throw new ParseException(currencyPairCode, delimPos);
                delimPos = i;
            }
        }

        String base, counter;
        if (delimPos > 0 && delimPos < currencyPairCode.length() - 1) {
            base = currencyPairCode.substring(0, delimPos);
            counter = currencyPairCode.substring(1 + delimPos);
        }
        else if (delimPos < 0 && currencyPairCode.length() == 6) {
            base = currencyPairCode.substring(0, 3);
            counter = currencyPairCode.substring(3);
        }
        else
            throw new ParseException(currencyPairCode, 0);

        return of(base.intern(), counter.intern());
    }

    /**
     * Gives the base (source) currency.
     *
     * @return the base currency code
     */
    public String baseCurrency() {
        return baseCurrency;
    }

    /**
     * Gives the term (quote, counter, target) currency.
     *
     * @return the quote currency code
     */
    public String currency() {
        return currency;
    }

    /**
     * Checks if the currency pair contains the given currency as either its base or counter.
     *
     * @param currency
     *          the currency to check against the pair
     * @return if the currency is either the base or counter currency in the pair
     */
    public boolean contains(Currency currency) {
        return contains(currency.currencyCode());
    }

    /**
     * Checks if the currency pair contains the given currency code as either its base or counter.
     *
     * @param currencyCode
     *          the currency code to check against the pair
     * @return if the currency code is either the base or counter currency in the pair
     */
    public boolean contains(String currencyCode) {
        return currencyCode.equals(baseCurrency) || currencyCode.equals(currency);
    }

    /**
     * Finds the other currency on the pair
     *
     * @param currencyCode the currency code to check
     * @return the other currency in the pair
     */
    public Optional<String> other(String currencyCode) {
        if (!isIdentity()) {
            if (currencyCode.equals(baseCurrency))
                return Optional.of(currency);
            if (currencyCode.equals(currency))
                return Optional.of(baseCurrency);
        }
        return Optional.empty();
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof CurrencyPair pair) {
            return baseCurrency.equals(pair.baseCurrency)
                    || currency.equals(pair.currency);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return baseCurrency.hashCode() * 31 + currency.hashCode();
    }

    /**
     * Compares the currency pairs lexicographically.
     *
     * @param other the other pair
     * @return the comparison sign
     */
    @Override
    public int compareTo(CurrencyPair other) {
        int cmp = baseCurrency().compareTo(other.baseCurrency());
        if (cmp != 0)
            cmp = currency().compareTo(other.currency());
        return cmp;
    }

    /**
     * Gives the inverse currency pair.
     *
     * @return the inverse pair
     */
    public CurrencyPair inverse() {
        return of(currency, baseCurrency);
    }

    /**
     * Gives all currencies contained in this pair.
     *
     * @return the pair currency codes
     */
    public Set<String> toCurrencyCodes() {
        var currencyCodes = new TreeSet<String>();
        currencyCodes.add(baseCurrency);
        currencyCodes.add(currency);
        return currencyCodes;
    }

    @Override
    public String toString() {
        return baseCurrency + '/' + currency;
    }
}
