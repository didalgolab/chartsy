package one.chartsy.data;

import one.chartsy.SymbolIdentity;
import one.chartsy.SymbolResource;
import one.chartsy.TimeFrame;
import one.chartsy.data.market.Tick;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.assertj.core.api.Assertions.*;

class SeriesTest {
    static final SymbolResource<Tick> TEST_SYMBOL = SymbolResource.of(SymbolIdentity.of("TEST_SYMBOL"), TimeFrame.TICKS, Tick.class);
    final Series<Tick> series = Series.of(TEST_SYMBOL, List.of(
            Tick.of(2, 2.2),
            Tick.of(1, 1.1),
            Tick.of(0, 0.0)
    ));

    @Test void query_can_downcast_Series_to_specialized_Type() {
        TickSeries tickSeries = series.query(TickSeries::from);

        assertThat(series).isNotInstanceOf(TickSeries.class);
        assertThat(tickSeries).isInstanceOf(TickSeries.class);
    }
}