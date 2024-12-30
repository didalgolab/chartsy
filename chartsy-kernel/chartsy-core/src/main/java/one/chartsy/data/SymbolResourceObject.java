/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.data;

import one.chartsy.SymbolIdentity;
import one.chartsy.SymbolResource;
import one.chartsy.TimeFrame;

public record SymbolResourceObject<E>(
        SymbolIdentity symbol,
        TimeFrame timeFrame,
        Class<? extends E> dataType
) implements SymbolResource<E> {
}
