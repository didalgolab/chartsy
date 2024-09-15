/* Copyright 2024 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.util;

/**
 * Represents a handler for exceptions. Implementations of this interface define
 * how to handle exceptions that occur during the execution of a program or service.
 */
public interface ExceptionHandler {

    /**
     * Handles an exception. Implementations should define the actions to be taken
     * when an exception is encountered, such as logging the error or performing
     * specific recovery actions.
     *
     * @param e the exception to handle
     */
    void onException(Throwable e);
}