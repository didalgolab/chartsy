/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.money;

public class CurrencyNotFoundException extends RuntimeException {

    public CurrencyNotFoundException(String message) {
        super(message);
    }
}
