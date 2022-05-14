/* Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy;

public /*sealed*/ interface TimeFrameUnit /*permits StandardTimeFrameUnit, TimeFrameUnit.Custom*/ {

    boolean isEventBased();

    boolean isPriceBased();

    @Override
    String toString();

    /*non-sealed*/ interface Custom extends TimeFrameUnit {

    }
}
