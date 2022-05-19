/*
 * Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.trade.strategy;

import one.chartsy.core.ThreadContext;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public class TradingAlgorithmSupplierAdapter<T extends TradingAlgorithm> extends AbstractTradingAlgorithmFactory<T> {

    private final Supplier<T> supplier;

    public TradingAlgorithmSupplierAdapter(Supplier<T> supplier) {
        this.supplier = supplier;
    }

    public Supplier<T> getSupplier() {
        return supplier;
    }

    @Override
    public T create(TradingAlgorithmContext context) {
        Map<String, Object> ctxMap = new HashMap<>();
        ctxMap.put("context", context);

        return ThreadContext.of(ctxMap).execute(getSupplier()::get);
    }
}
