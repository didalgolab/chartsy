/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy;

import one.chartsy.money.CurrencyLookup;
import one.chartsy.money.CurrencyNotFoundException;

public class Currencies {

    public static Currency getCurrency(String currencyCode) {
        return CurrencyLookup.getDefault().getCurrency(currencyCode)
                .orElseThrow(() -> new CurrencyNotFoundException(currencyCode));
    }
}
