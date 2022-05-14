/* Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.data;

import one.chartsy.SymbolResource;

public interface SymbolResourceData<E,D> {

    SymbolResource<E> getResource();

    D getData();
}
