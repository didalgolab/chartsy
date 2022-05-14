/* Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy;

public interface PriceDomain {

    String name();

    record Of(String name) implements PriceDomain {
        public static final PriceDomain MIDPOINT = new Of("MIDPOINT");
        public static final PriceDomain LAST_TRADE = new Of("LAST_TRADE");
    }
}
