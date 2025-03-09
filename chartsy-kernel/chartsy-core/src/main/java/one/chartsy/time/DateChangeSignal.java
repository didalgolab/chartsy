/* Copyright 2025 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.time;

import java.time.LocalDate;

/**
 * A device that signals when a new date (past midnight) is encountered.
 * This allows operations to be executed only once per day.
 *
 * <p>This class is <b>NOT</b> thread-safe.
 */
public class DateChangeSignal {
    /**
     * The last date for which the signal was triggered.
     * Initialized to yesterday so that the very first check signals today.
     */
    private LocalDate due = LocalDate.MIN;

    private DateChangeSignal() { }

    /**
     * Creates a {@code DateChangeSignal}.
     *
     * @return a new {@code DateChangeSignal} instance to inspect date changes
     */
    public static DateChangeSignal create() {
        return new DateChangeSignal();
    }

    /**
     * Checks if a new date has occurred (i.e. the current date is later than the stored date).
     * If a new date is detected, the internal state is updated to the current date.
     *
     * @return {@code true} if the current date is new and the signal is triggered,
     *         {@code false} otherwise.
     */
    public boolean poll(Chronological current) {
        LocalDate now = current.getDate();
        if (now.isAfter(due)) {
            due = now;
            return true;
        }
        return false;
    }
}
