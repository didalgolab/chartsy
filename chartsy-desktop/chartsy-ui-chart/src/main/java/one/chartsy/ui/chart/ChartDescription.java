/* Copyright 2026 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.ui.chart;

import one.chartsy.Candle;
import one.chartsy.TimeFrame;
import one.chartsy.data.CandleSeries;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ChartDescription {

    private static final int DEFAULT_BAR_COUNT = 60;

    private final BarSeries dailyBars;
    private final BarSeries weeklyBars;
    private final BarSeries monthlyBars;

    private ChartDescription(BarSeries dailyBars, BarSeries weeklyBars, BarSeries monthlyBars) {
        if (dailyBars == null)
            throw new IllegalArgumentException("dailyBars is null");
        if (weeklyBars == null)
            throw new IllegalArgumentException("weeklyBars is null");
        if (monthlyBars == null)
            throw new IllegalArgumentException("monthlyBars is null");

        this.dailyBars = dailyBars;
        this.weeklyBars = weeklyBars;
        this.monthlyBars = monthlyBars;
    }

    public static ChartDescription from(CandleSeries dataset) {
        return from(dataset, DEFAULT_BAR_COUNT);
    }

    public static ChartDescription from(CandleSeries dataset, int barCount) {
        if (dataset == null)
            throw new IllegalArgumentException("dataset is null");
        if (barCount <= 0)
            throw new IllegalArgumentException("barCount must be > 0");

        var daily = aggregateCandles(dataset, TimeFrame.Period.DAILY);
        var weekly = aggregateCandles(dataset, TimeFrame.Period.WEEKLY);
        var monthly = aggregateCandles(dataset, TimeFrame.Period.MONTHLY);

        return new ChartDescription(
                BarSeries.fromCandles(daily, barCount),
                BarSeries.fromCandles(weekly, barCount),
                BarSeries.fromCandles(monthly, barCount)
        );
    }

    public BarSeries dailyBars() {
        return dailyBars;
    }

    public BarSeries weeklyBars() {
        return weeklyBars;
    }

    public BarSeries monthlyBars() {
        return monthlyBars;
    }

    public String toYamlString() {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("daily_bars", dailyBars.toYamlMap());
        root.put("weekly_bars", weeklyBars.toYamlMap());
        root.put("monthly_bars", monthlyBars.toYamlMap());

        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setDefaultScalarStyle(DumperOptions.ScalarStyle.PLAIN);
        options.setPrettyFlow(true);
        options.setIndent(2);
        options.setIndicatorIndent(1);
        options.setLineBreak(DumperOptions.LineBreak.UNIX);

        Yaml yaml = new Yaml(options);
        return yaml.dump(root).stripTrailing();
    }

    private static List<Candle> aggregateCandles(CandleSeries series, TimeFrame timeFrame) {
        var aggregator = timeFrame.getAggregator();
        var chronological = new ArrayList<Candle>(series.length());
        for (Candle candle : series) {
            chronological.add(candle);
        }
        return aggregator.aggregate(chronological, true);
    }

    public record BarSeries(List<Bar> bars) {

        public BarSeries {
            if (bars == null)
                throw new IllegalArgumentException("bars is null");
        }

        public static BarSeries fromCandles(List<Candle> candles, int count) {
            int startIndex = Math.max(0, candles.size() - count);
            var bars = new ArrayList<Bar>(candles.size() - startIndex);
            for (int i = candles.size() - 1; i >= startIndex; i--) {
                Candle candle = candles.get(i);
                bars.add(new Bar(candle.getDate(), Ohlcv.from(candle)));
            }
            return new BarSeries(List.copyOf(bars));
        }

        public Map<String, Map<String, BigDecimal>> toYamlMap() {
            var mapped = new LinkedHashMap<String, Map<String, BigDecimal>>(bars.size());
            for (Bar bar : bars) {
                mapped.put(bar.date().toString(), bar.ohlcv().toYamlMap());
            }
            return Collections.unmodifiableMap(mapped);
        }
    }

    public record Bar(LocalDate date, Ohlcv ohlcv) {
        public Bar {
            if (date == null)
                throw new IllegalArgumentException("date is null");
            if (ohlcv == null)
                throw new IllegalArgumentException("ohlcv is null");
        }
    }

    public record Ohlcv(BigDecimal open, BigDecimal high, BigDecimal low, BigDecimal close, BigDecimal volume) {
        public Ohlcv {
            if (open == null || high == null || low == null || close == null || volume == null)
                throw new IllegalArgumentException("ohlcv value is null");
        }

        public static Ohlcv from(Candle candle) {
            return new Ohlcv(
                    toDecimal(candle.open()),
                    toDecimal(candle.high()),
                    toDecimal(candle.low()),
                    toDecimal(candle.close()),
                    toDecimal(candle.volume())
            );
        }

        public Map<String, BigDecimal> toYamlMap() {
            var values = new LinkedHashMap<String, BigDecimal>(5);
            values.put("open", open);
            values.put("high", high);
            values.put("low", low);
            values.put("close", close);
            values.put("volume", volume);
            return Collections.unmodifiableMap(values);
        }
    }

    private static BigDecimal toDecimal(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }
}
