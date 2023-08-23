/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.samples.chartfx.service.order;

public class InternalOrderIdGenerator {
    private static final ThreadLocal<Integer> generator = new ThreadLocal<Integer>() {
        @Override
        protected Integer initialValue() {
            return 0;
        }
    };

    public static Integer generateId() {
        generator.set(generator.get() + 1);
        return generator.get();
    }
}
