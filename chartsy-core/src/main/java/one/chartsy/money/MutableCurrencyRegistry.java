/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.money;

import one.chartsy.Currency;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class MutableCurrencyRegistry extends AbstractCurrencyRegistry {

    public MutableCurrencyRegistry() {
        this(new ConcurrentHashMap<>());
    }

    public MutableCurrencyRegistry(ConcurrentMap<String, Currency> initial) {
        super(initial);
    }

    public Currency registerIfNotExists(Currency newCurrency) {
        Currency existing = currencies.putIfAbsent(newCurrency.currencyCode(), newCurrency);
        return (existing != null)? existing : newCurrency;
    }
}
