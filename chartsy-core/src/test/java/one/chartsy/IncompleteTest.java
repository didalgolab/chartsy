/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class IncompleteTest {

    @Test void empty_gives_always_empty_Incomplete() {
        Incomplete<?> empty = Incomplete.empty();
        assertFalse(empty.isPresent(), "isPresent");
        assertNull(empty.get(), "get should give null");
    }
}