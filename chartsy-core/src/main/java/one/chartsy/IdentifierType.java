/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy;

/**
 * A type of unique security identifier used to distinguish between the numerous and varied securities issuers and
 * securities issues.
 *
 * @implSpec Custom implementation of this interface must be thread-safe and immutable.
 *
 * @see StandardIdentifierType
 */
public interface IdentifierType {

    String name();

    /**
     * Standard security identifier types.
     */
    enum Standard implements IdentifierType {
        TICKER,
        CUSIP,
        ISIN,
        MIC,
        RIC,
        SEDOL
    }

    /**
     * Custom security identifier type barring the given name.
     */
    record Of(String name) implements IdentifierType { }

}
