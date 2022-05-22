/* Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy;

/**
 * The type of financial instrument.
 *
 * @see AssetType
 */
public interface AssetClass {

    /**
     * The financial asset type identifier.
     */
    String name();

    /**
     * Yields {@code true} if the referred instrument type is tradable, and yields {@code false} otherwise.
     */
    boolean isTradable();
}
