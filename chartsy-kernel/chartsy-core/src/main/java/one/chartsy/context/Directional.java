/* Copyright 2025 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.context;

/**
 * Represents a directional entity encoded numerically as an integer value.
 * <p>
 * Implementations encapsulate directional semantics (e.g., positive, negative, neutral)
 * via unique integer identifiers, allowing efficient and consistent directional encoding 
 * across algorithms and analytical contexts.
 *
 * @author Mariusz Bernacki
 */
public interface Directional {

    /**
     * Returns the numeric integer encoding associated with this direction.
     * <p>
     * The returned integer uniquely identifies a directional state, typically with:
     * <ul>
     *   <li>{@code 1} representing a positively biased direction (e.g., long),</li>
     *   <li>{@code -1} representing a negatively biased direction (e.g., short), and</li>
     *   <li>{@code 0} representing a neutral direction (e.g., flat).</li>
     * </ul>
     *
     * @return the numeric integer encoding of the direction
     */
    int intValue();
}
