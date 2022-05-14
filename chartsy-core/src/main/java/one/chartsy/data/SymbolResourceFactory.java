/* Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.data;

import one.chartsy.SymbolIdentity;
import one.chartsy.SymbolResource;
import one.chartsy.TimeFrame;
import org.openide.util.lookup.ServiceProvider;

import java.util.Map;

@ServiceProvider(service = SymbolResourceFactory.class)
public class SymbolResourceFactory {

    public <E> SymbolResource<E> create(SymbolIdentity symbol, TimeFrame timeFrame, Class<? extends E> dataType) {
        return new SymbolResourceObject<>(symbol, timeFrame, dataType);
    }

    public <E> SymbolResource<E> create(SymbolIdentity symbol, TimeFrame timeFrame, Class<? extends E> dataType, Map<String,?> meta) {
        return create(symbol, timeFrame, dataType);
    }
}
