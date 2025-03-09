/* Copyright 2025 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy;

/**
 * Interface for objects that need awareness of their associated {@link SymbolData}.
 */
@FunctionalInterface
public interface SymbolDataAware<T> {

    /**
     * Sets the associated {@code SymbolData}.
     *
     * @param data the associated symbol data
     */
    void setSymbolData(SymbolData<T> data);
}
