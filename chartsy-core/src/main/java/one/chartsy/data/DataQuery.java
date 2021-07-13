package one.chartsy.data;

import one.chartsy.SymbolIdentity;
import one.chartsy.TimeFrame;
import one.chartsy.time.Chronological;

import java.time.LocalDateTime;

public interface DataQuery<T> {
    SymbolIdentity symbol();
    TimeFrame timeFrame();
    LocalDateTime startTime();
    LocalDateTime endTime();
    int limit();
    Chronological.Order order();
}
