/* Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.data;

public class ChronologicalIteratorContext {
    private final Integer id;

    public ChronologicalIteratorContext(int id) {
        this.id = id;
    }

    public final Integer getId() {
        return id;
    }
}
