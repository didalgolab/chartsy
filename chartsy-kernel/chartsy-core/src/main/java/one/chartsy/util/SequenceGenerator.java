/* Copyright 2025 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.util;

import one.chartsy.text.CharSequenceCounter;

import java.time.Instant;

/**
 * A generator for unique sequence numbers.
 */
public interface SequenceGenerator {

    String next();

    static SequenceGenerator create() {
        return create("");
    }

    static SequenceGenerator create(String prefix) {
        var instant = Instant.now();
        var initial = Math.multiplyExact(instant.getEpochSecond(), 1_000_000) + instant.getNano() / 1_000;
        return new CharSequenceCounter(prefix + initial)::incrementAndGet;
    }
}
