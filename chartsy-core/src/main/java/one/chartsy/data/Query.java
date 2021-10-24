package one.chartsy.data;

/**
 * Strategy for querying objects.
 *
 * @param <T> type of objects being queried
 * @param <R> query result type
 */
@FunctionalInterface
public interface Query<T, R> {

    R queryFrom(T obj);
}
