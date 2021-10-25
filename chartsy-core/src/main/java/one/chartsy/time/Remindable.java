package one.chartsy.time;

import java.time.LocalDateTime;

@FunctionalInterface
public interface Remindable {

    void onReminder(LocalDateTime when, Object obj);
}
