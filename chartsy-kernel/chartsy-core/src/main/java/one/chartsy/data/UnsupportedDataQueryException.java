/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.data;

public class UnsupportedDataQueryException extends RuntimeException {

    private final DataQuery<?> unsupportedQuery;

    public UnsupportedDataQueryException(DataQuery<?> unsupportedQuery, String message) {
        super(message);
        this.unsupportedQuery = unsupportedQuery;
    }

    public DataQuery<?> getUnsupportedQuery() {
        return unsupportedQuery;
    }
}
