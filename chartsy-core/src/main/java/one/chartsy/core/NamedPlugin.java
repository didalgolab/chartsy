package one.chartsy.core;

import one.chartsy.commons.AbstractHandleableCloseable;

public abstract class NamedPlugin<T extends NamedPlugin<T>> extends AbstractHandleableCloseable<T> {

    private final String name;

    protected NamedPlugin(String name) {
        if (name == null)
            throw new IllegalArgumentException("name is NULL");
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public <R> R query(NamedPluginQuery<R, T> query) {
        return query.queryFrom(this);
    }
}
