/* Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.core;

import one.chartsy.core.commons.AbstractHandleableCloseable;

public abstract class NamedPlugin<T extends NamedPlugin<T>> extends AbstractHandleableCloseable<T> implements Named {

    private final String name;

    protected NamedPlugin(String name) {
        if (name == null)
            throw new IllegalArgumentException("name is NULL");
        this.name = name;
    }

    @Override
    public String getName() {
        return name;
    }

    public <R> R query(NamedPluginQuery<R, T> query) {
        return query.queryFrom(this);
    }
}
