/* Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.data;

import one.chartsy.time.Chronological;

public interface IndexedSymbolResourceData<E extends Chronological> extends SymbolResourceData<E, Dataset<E>> {

    int length();

    E get(int index);

    E getFirst();

    E getLast();

    boolean isEmpty();
}
