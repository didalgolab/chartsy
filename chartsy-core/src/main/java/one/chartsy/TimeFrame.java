package one.chartsy;

public interface TimeFrame {

    enum Period implements TimeFrame {
        DAILY,
        H1, H3, H4, H6
    }

    default boolean isAssignableFrom(TimeFrame other) {
        return equals(other);
    }
}
