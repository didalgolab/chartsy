/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.samples.chartfx.service;

public class TickDataFinishedException extends Exception {
    private static final long serialVersionUID = 5241232871349317846L;

    public TickDataFinishedException(String message) {
        super(message);
    }
}
