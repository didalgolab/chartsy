/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy;

import one.chartsy.misc.Ephemeral;
import one.chartsy.time.Chronological;

public interface When extends Ephemeral {

    SymbolResource<?> getResource();

    /**
     * Gives the unique identifier of this iterator.
     */
    Integer getId();

    int index();

    Chronological current();

    boolean hasNext();

    default SymbolIdentity getSymbol() {
        return getResource().symbol();
    }
}
