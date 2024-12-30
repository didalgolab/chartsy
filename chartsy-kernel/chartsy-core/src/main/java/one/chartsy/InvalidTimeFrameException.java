/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy;

public class InvalidTimeFrameException extends RuntimeException {

    public InvalidTimeFrameException() {}

    public InvalidTimeFrameException(String message) {
        super(message);
    }
}
