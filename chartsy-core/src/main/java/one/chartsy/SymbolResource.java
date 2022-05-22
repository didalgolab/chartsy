/* Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy;

import one.chartsy.data.SimpleCandle;
import one.chartsy.data.SymbolResourceFactory;
import org.openide.util.Lookup;

import java.util.Map;

public interface SymbolResource<E> {
    SymbolIdentity symbol();
    TimeFrame timeFrame();
    Class<? extends E> dataType();

    default SymbolResource<E> withTimeFrame(TimeFrame newTimeFrame) {
        return of(symbol(), newTimeFrame, dataType());
    }

    default SymbolResource<E> withSymbol(SymbolIdentity newSymbol) {
        return of(newSymbol, timeFrame(), dataType());
    }

    static SymbolResource<Candle> of(String name, TimeFrame timeFrame) {
        return of(SymbolIdentity.of(name), timeFrame);
    }

    static SymbolResource<Candle> of(String name, TimeFrame timeFrame, AssetClass type) {
        return of(SymbolIdentity.of(name, type), timeFrame);
    }

    static SymbolResource<Candle> of(SymbolIdentity symbol, TimeFrame timeFrame) {
        return of(symbol, timeFrame, SimpleCandle.class);
    }

    static <E> SymbolResource<E> of(SymbolIdentity symbol, TimeFrame timeFrame, Class<? extends E> dataType) {
        return Lookup.getDefault().lookup(SymbolResourceFactory.class).create(symbol, timeFrame, dataType);
    }

    static <E> SymbolResource<E> of(SymbolIdentity symbol, TimeFrame timeFrame, Class<? extends E> dataType, Map<String, ?> meta) {
        return Lookup.getDefault().lookup(SymbolResourceFactory.class).create(symbol, timeFrame, dataType, meta);
    }
}
