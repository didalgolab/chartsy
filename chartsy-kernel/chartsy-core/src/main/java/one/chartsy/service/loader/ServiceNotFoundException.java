/* Copyright 2025 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.service.loader;

/**
 * Unchecked exception thrown when a requested service cannot be found.
 * Typically, indicates a configuration or lookup error (e.g., missing bean,
 * unknown service ID, or mistyped name).
 *
 * @author Mariusz Bernacki
 */
public class ServiceNotFoundException extends RuntimeException {
    public ServiceNotFoundException(String message) {
        super(message);
    }
}
