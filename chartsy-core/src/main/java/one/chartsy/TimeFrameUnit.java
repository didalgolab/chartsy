package one.chartsy;

public interface TimeFrameUnit {

    boolean isEventBased();

    boolean isPriceBased();

    @Override
    String toString();
}
