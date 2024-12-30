/*
 * Copyright 2024 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.data;

/**
 * Exception thrown when operations are attempted on vectors of differing dimensions.
 */
public class DimensionMismatchException extends IllegalArgumentException {
    public DimensionMismatchException(String message) {
        super(message);
    }
}