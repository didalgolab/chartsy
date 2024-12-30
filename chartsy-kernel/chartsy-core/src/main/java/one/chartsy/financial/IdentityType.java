/* Copyright 2024 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.financial;

/**
 * Represents the type of financial instrument or market entity.
 * This interface separates the concept of tradable instruments from non-tradable
 * market entities, allowing for precise categorization in the trading system.
 *
 * @author Mariusz Bernacki
 * @see InstrumentType
 * @see MarketEntityType
 */
public interface IdentityType {

    /**
     * Returns the name of the identity type.
     *
     * @return the identity type name
     */
    String name();

    /**
     * Indicates whether the identified symbol is tradable in the market.
     *
     * @return {@code true} if the identity type is tradable, {@code false} otherwise
     */
    boolean isTradable();
}