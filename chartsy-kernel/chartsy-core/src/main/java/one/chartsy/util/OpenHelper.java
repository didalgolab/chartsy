/* Copyright 2024 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.util;

import one.chartsy.base.OpenDisposable;

/**
 * Utility class for safely opening Manageable resources.
 * This class provides methods to open resources, propagating any exceptions that may occur during the opening process.
 * <p>
 * Usage example:
 * <pre>
 * {@code
 * try {
 *     Manageable resource1 = new SomeManageableResource();
 *     Manageable resource2 = new AnotherManageableResource();
 *     OpenHelper.open(resource1, resource2);
 *     // Use the opened resources
 * } catch (Exception e) {
 *     // Handle exception
 * }
 * }
 * </pre>
 */
public final class OpenHelper {

    private OpenHelper() {
        // Private constructor to prevent instantiation
    }

    /**
     * Opens one or more {@code Manageable} resources, propagating any runtime exceptions.
     *
     * @param resources the {@code Manageable} resources to open
     */
    public static void open(OpenDisposable... resources) {
        if (resources != null) {
            for (OpenDisposable resource : resources) {
                open(resource);
            }
        }
    }

    /**
     * Opens a single {@code Manageable} resource, propagating any runtime exceptions.
     *
     * @param resource the {@code Manageable} resource to open, may be null
     */
    public static void open(OpenDisposable resource) {
        if (resource != null) {
            resource.open();
        }
    }
}