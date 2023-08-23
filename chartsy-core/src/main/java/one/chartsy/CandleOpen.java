/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy;

public interface CandleOpen {

    double open();

    long openTime();

    boolean isOpenTimed();

    record NonTimed(double open, long openTime) implements CandleOpen {
        @Override
        public boolean isOpenTimed() {
            return false;
        }
    }
}
