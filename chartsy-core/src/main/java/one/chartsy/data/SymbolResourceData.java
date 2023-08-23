/*
 * Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.data;

import one.chartsy.SymbolIdentity;
import one.chartsy.SymbolResource;
import one.chartsy.TimeFrame;

public interface SymbolResourceData<E,D> {

    SymbolResource<E> getResource();

    SymbolIdentity getSymbol();

    TimeFrame getTimeFrame();

    D getData();
}
