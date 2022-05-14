/* Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.trade.strategy;

import java.util.function.Supplier;

public abstract class AbstractTradingAlgorithmFactory<T extends TradingAlgorithm>
        implements TradingAlgorithmFactory<T> {



    public static <T extends TradingAlgorithm> TradingAlgorithmFactory<T> from(Supplier<T> supp) {
        if (supp instanceof TradingAlgorithmFactory)
            return (TradingAlgorithmFactory<T>) supp;
        else {
            return new AbstractTradingAlgorithmFactory<T>() {
                @Override
                public T create(TradingAlgorithmContext context) {
                    return supp.get();
                }
            };
        }
    }
}
