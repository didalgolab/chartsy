package one.chartsy;

import java.time.temporal.TemporalAmount;

public interface TimeBasedTimeFrame extends TimeFrame {

    TemporalAmount getDuration();

}
