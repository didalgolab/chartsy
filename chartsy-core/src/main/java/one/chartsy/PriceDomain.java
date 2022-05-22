/* Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy;

public interface PriceDomain {

    String name();

    record Of(String name) implements PriceDomain {
        /**
         * The average between bid and ask quote prices.
         */
        public static final PriceDomain MIDPOINT = new Of("MIDPOINT");
        /**
         * The historical trade prices.
         */
        public static final PriceDomain TRADE = new Of("TRADE");
        /**
         * The mark prices of the instrument.
         */
        public static final PriceDomain MARK = new Of("MARK");
        /**
         * Represents the underlying spot/index prices of the instrument.
         */
        public static final PriceDomain SPOT = new Of("SPOT");
    }
}
