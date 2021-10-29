package one.chartsy.scheduling;

public interface EventScheduler {

    void schedule(long triggerTime, Runnable event);
}
