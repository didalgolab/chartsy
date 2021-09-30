package one.chartsy.core;

@FunctionalInterface
public interface NamedPluginQuery<R, T extends NamedPlugin<T>> {

    R queryFrom(NamedPlugin<? extends T> plugin);
}
