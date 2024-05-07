package one.chartsy.data;

import one.chartsy.Candle;
import one.chartsy.SymbolResource;
import one.chartsy.TimeFrame;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CandleSeriesSupportTest {

	static final SymbolResource<Candle> TEST_SYMBOL = SymbolResource.of("TEST_SYMBOL", TimeFrame.Period.DAILY);

	@Test
	void synchronizeTimelines() {
		// Create sample candle series with different timelines
		Series<Candle> series1 = seriesOf(
				candle(LocalDate.of(2023, 11, 1)),
				candle(LocalDate.of(2023, 11, 2)),
				candle(LocalDate.of(2023, 11, 4))
		);
		Series<Candle> series2 = seriesOf(
				candle(LocalDate.of(2023, 11, 2)),
				candle(LocalDate.of(2023, 11, 3))
		);

		// Synchronize the timelines
		List<Series<Candle>> resultSeries = CandleSeriesSupport.synchronizeTimelines(List.of(series1, series2));

		// Verify the results
		assertEquals(2, resultSeries.size());

		// Check series 1
		Series<Candle> syncSeries1 = resultSeries.getFirst();
		assertEquals(4, syncSeries1.length());
		assertEquals(candle(LocalDate.of(2023, 11, 1)), syncSeries1.get(3));
		assertEquals(candle(LocalDate.of(2023, 11, 2)), syncSeries1.get(2));
		assertEquals(Candle.of(LocalDateTime.of(2023, 11, 3, 0, 0), series1.get(1).close()), syncSeries1.get(1)); // Filled candle
		assertEquals(candle(LocalDate.of(2023, 11, 4)), syncSeries1.get(0));

		// Check series 2
		Series<Candle> syncSeries2 = resultSeries.get(1);
		assertEquals(3, syncSeries2.length());
		assertEquals(candle(LocalDate.of(2023, 11, 2)), syncSeries2.get(2));
		assertEquals(candle(LocalDate.of(2023, 11, 3)), syncSeries2.get(1));
		assertEquals(Candle.of(LocalDateTime.of(2023, 11, 4, 0, 0), series2.get(0).close()), syncSeries2.get(0)); // Filled candle

		// Verify that the series share the same Timeline
		assertSame(syncSeries1.getTimeline(), syncSeries2.getTimeline());
	}

	static Series<Candle> seriesOf(Candle... cs) {
		return CandleSeries.of(TEST_SYMBOL, List.of(cs));
	}

	static Candle candle(LocalDate dateTime) {
		var close = dateTime.getDayOfMonth();
		return Candle.of(dateTime.atStartOfDay(), 10, 31, 1, close, 100);
	}
}