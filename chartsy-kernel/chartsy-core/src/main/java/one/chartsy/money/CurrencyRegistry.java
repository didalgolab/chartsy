/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.money;

import one.chartsy.Currency;

import java.util.List;
import java.util.Optional;

public interface CurrencyRegistry {

    List<Currency> getCurrencies();

    Optional<Currency> getCurrency(String currencyCode);
}
