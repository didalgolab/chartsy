package one.chartsy;

public record AttributeKey<T>(Class<T> type, String name) implements Comparable<AttributeKey<?>> {

    @Override
    public int compareTo(AttributeKey<?> o) {
        int cmp = name.compareTo(o.name);
        return (cmp != 0)? cmp: type.getName().compareTo(o.type.getName());
    }
}
