package one.chartsy;

public interface TypedKey<T> {

    Class<T> type();

    String name();
}
