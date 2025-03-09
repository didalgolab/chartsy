/* Copyright 2025 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.data.stream;

/**
 * An unchecked exception indicating that an error occurred within a MessageChannel.
 * <p>
 * This exception wraps underlying exceptions such as I/O errors to simplify error handling
 * when working with message channels, allowing callers to handle channel-specific issues distinctly.
 * </p>
 */
public class MessageChannelException extends RuntimeException {

    /**
     * Constructs a new MessageChannelException with the specified detail message and cause.
     *
     * @param message the detail message providing context about the error
     * @param cause   the underlying cause of this exception
     */
    public MessageChannelException(String message, Throwable cause) {
        super(message, cause);
    }
}
