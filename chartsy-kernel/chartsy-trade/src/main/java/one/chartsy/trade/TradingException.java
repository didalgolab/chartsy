/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.trade;

public class TradingException extends RuntimeException {

    public TradingException(String message) {
        super(message);
    }
}
