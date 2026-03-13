package one.chartsy.ui.chart;

import one.chartsy.Candle;
import one.chartsy.SymbolIdentity;
import one.chartsy.SymbolResource;
import one.chartsy.TimeFrame;
import one.chartsy.TimeFrameHelper;
import one.chartsy.data.CandleSeries;
import one.chartsy.data.provider.DataProvider;
import one.chartsy.ui.chart.type.CandlestickChart;
import org.junit.jupiter.api.Test;

import java.awt.Dimension;
import java.awt.Rectangle;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChartFrameIntradayContinuityTest {

    @Test
    void intradayWeekendGapsKeepEveryCandleReachableOnTheChartFrame() {
        List<Candle> candles = fixtureCandles();
        ChartFrame chartFrame = ChartExporter.createChartFrame(
                DataProvider.EMPTY,
                CandleSeries.of(
                        SymbolResource.of(SymbolIdentity.of("NVDA-H1-WEEKEND"), TimeFrame.Period.H1).withDataType(Candle.class),
                        candles
                ),
                simpleTemplate(),
                new Dimension(1280, 800)
        );
        ChartData chartData = chartFrame.getChartData();
        Rectangle plotBounds = chartFrame.getMainPanel().getChartPanel().getRenderBounds();

        assertThat(chartData.getHistoricalSlotCount()).isEqualTo(candles.size());
        assertThat(plotBounds.width).isPositive();
        assertThat(plotBounds.height).isPositive();

        for (int slot = 0; slot < candles.size(); slot++) {
            Candle expected = candles.get(slot);
            double x = chartData.getSlotCenterX(slot, plotBounds);

            assertThat(chartData.getSlotAtX(x, plotBounds)).isEqualTo(slot);
            assertThat(chartData.getSlotTime(slot)).isEqualTo(expected.time());
            assertThat(chartData.getCandleAtSlot(slot)).isEqualTo(expected);

            chartFrame.getDateAxisFooter().setHoverSlot(slot);
            var hover = chartFrame.getDateAxisFooter().snapshot().hoverLabel();
            assertThat(hover).isNotNull();
            assertThat(hover.slot()).isEqualTo(slot);
            assertThat(hover.epochMicros()).isEqualTo(expected.time());
            assertThat(hover.label()).isEqualTo(TimeFrameHelper.formatDate(TimeFrame.Period.H1, expected.time()));
        }
    }

    private static ChartTemplate simpleTemplate() {
        ChartTemplate template = new ChartTemplate("Intraday Continuity");
        template.setChartProperties(new ChartProperties());
        template.setChart(new CandlestickChart());
        return template;
    }

    private static List<Candle> fixtureCandles() {
        return List.of(
                candle(2026, 1, 30, 16, 100.0),
                candle(2026, 1, 30, 17, 101.0),
                candle(2026, 1, 30, 18, 102.0),
                candle(2026, 1, 30, 19, 103.0),
                candle(2026, 2, 2, 16, 104.0),
                candle(2026, 2, 2, 17, 105.0),
                candle(2026, 2, 2, 18, 106.0),
                candle(2026, 2, 2, 19, 107.0)
        );
    }

    private static Candle candle(int year, int month, int day, int hour, double close) {
        double open = close - 0.4;
        double high = close + 0.8;
        double low = close - 1.0;
        return Candle.of(LocalDateTime.of(year, month, day, hour, 0), open, high, low, close, 1_000.0 + close);
    }
}
