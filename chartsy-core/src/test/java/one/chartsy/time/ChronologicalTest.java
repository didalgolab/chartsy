/* Copyright 2024 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.time;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ChronologicalTest {

    @Test
    void toInstant() {
        var EPOCH_NANOS = 1234567890123456789L;
        var instant = Chronological.toInstant(EPOCH_NANOS);

        assertNotNull(instant);
        assertEquals(EPOCH_NANOS, instant.getEpochSecond() * 1_000_000_000L + instant.getNano());
    }
}