package one.chartsy.data;

import lombok.Builder;
import lombok.Getter;
import lombok.experimental.Accessors;
import one.chartsy.SymbolResource;
import one.chartsy.time.Chronological;

import java.time.LocalDateTime;

@Builder(builderClassName = "Builder")
@Getter @Accessors(fluent = true)
public class DataQuery<T> {
    private final SymbolResource<T> resource;
    private final LocalDateTime startTime;
    private final LocalDateTime endTime;
    private final int limit;
    private final Chronological.Order order;
}
