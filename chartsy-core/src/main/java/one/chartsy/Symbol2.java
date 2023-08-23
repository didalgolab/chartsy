/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy;

public interface Symbol2 {

    String vendorKey();

    SymbolIdentity symbol();

    Currency baseCurrency();

    Currency termCurrency();

    Currency minOrderSizeCurrency();

    Currency commissionCurrency();

    double minOrderSize();

    double orderSizePrecision();

    double orderPricePrecision();

    double makerCommission();

    double takerCommission();

    String vendorFeedAsset();

    String vendorTradingAsset();

    String attributes();
}
