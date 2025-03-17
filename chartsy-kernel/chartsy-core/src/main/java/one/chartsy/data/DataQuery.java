/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.data;

import lombok.Builder;
import lombok.Getter;
import lombok.experimental.Accessors;
import one.chartsy.Currency;
import one.chartsy.SymbolResource;
import one.chartsy.time.Chronological;

import java.time.LocalDateTime;

@Builder(builderClassName = "Builder")
@Getter @Accessors(fluent = true)
public class DataQuery<T> {
    private final SymbolResource<T> resource;
    private final LocalDateTime startTime;
    private final LocalDateTime endTime;
    private final Currency currency;
    private final int limit;
    private final Chronological.ChronoOrder order;

    public static <T> DataQuery.Builder<T> resource(SymbolResource<T> resource) {
        return DataQuery.<T>builder().resource(resource);
    }

    public static <T> DataQuery<T> of(SymbolResource<T> resource) {
        return resource(resource).build();
    }
}
