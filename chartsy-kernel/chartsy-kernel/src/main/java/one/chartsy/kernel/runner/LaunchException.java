/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.kernel.runner;

public class LaunchException extends Exception {

    public LaunchException(String message) {
        super(message);
    }

    public LaunchException(String message, Throwable cause) {
        super(message, cause);
    }
}
