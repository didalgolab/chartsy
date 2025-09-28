/* Copyright 2025 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.util;

import one.chartsy.text.CharSequenceCounter;

/**
 * A generator for unique sequence numbers.
 */
public interface SequenceGenerator {

    String next();

    static SequenceGenerator create() {
        return create("");
    }

    static SequenceGenerator create(String prefix) {
        var sequence = new CharSequenceCounter("000000");
        return () -> prefix + sequence.incrementAndGet();
    }
}
