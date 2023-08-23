/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.samples.chartfx.service.order;

public class InternalPositionIdGenerator {
    private static final ThreadLocal<Integer> generator = ThreadLocal.withInitial(() -> 0);

    public static Integer generateId() {
        generator.set(generator.get() + 1);
        return generator.get();
    }
}
