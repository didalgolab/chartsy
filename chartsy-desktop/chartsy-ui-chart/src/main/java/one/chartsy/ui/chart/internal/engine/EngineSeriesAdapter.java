package one.chartsy.ui.chart.internal.engine;

import one.chartsy.TimeFrame;
import one.chartsy.base.DoubleDataset;
import one.chartsy.charting.TimeUnit;
import one.chartsy.charting.data.DefaultDataSet;
import one.chartsy.core.Range;
import one.chartsy.data.CandleSeries;
import one.chartsy.time.Chronological;
import one.chartsy.ui.chart.ChartContext;
import one.chartsy.ui.chart.ChartData;
import one.chartsy.ui.chart.PixelPerfectCandleGeometry;

import java.util.Arrays;
import java.util.Date;

final class EngineSeriesAdapter {
    private EngineSeriesAdapter() {
    }

    record OhlcData(DefaultDataSet high, DefaultDataSet low, DefaultDataSet open, DefaultDataSet close, int length) {
        DefaultDataSet[] asArray() {
            return new DefaultDataSet[] { high, low, open, close };
        }
    }

    record SplitSeries(DefaultDataSet positive, DefaultDataSet negative, int length) {
    }

    static OhlcData adapt(String name, CandleSeries candles) {
        int length = candles.length();
        double[] xValues = xValues(0, length);
        double[] highs = new double[length];
        double[] lows = new double[length];
        double[] opens = new double[length];
        double[] closes = new double[length];
        for (int slot = 0; slot < length; slot++) {
            int seriesIndex = length - slot - 1;
            var candle = candles.get(seriesIndex);
            highs[slot] = candle.high();
            lows[slot] = candle.low();
            opens[slot] = candle.open();
            closes[slot] = candle.close();
        }
        return new OhlcData(
                new DefaultDataSet(name + " High", xValues, highs, false),
                new DefaultDataSet(name + " Low", xValues, lows, false),
                new DefaultDataSet(name + " Open", xValues, opens, false),
                new DefaultDataSet(name + " Close", xValues, closes, false),
                length
        );
    }

    static DefaultDataSet adapt(String name, DoubleDataset values, int historicalSlots) {
        int length = values.length();
        int startSlot = alignedStartSlot(length, historicalSlots);
        double[] xValues = xValues(startSlot, length);
        double[] yValues = new double[length];
        for (int slot = 0; slot < length; slot++)
            yValues[slot] = values.get(length - slot - 1);
        return new DefaultDataSet(name, xValues, yValues, false);
    }

    static DefaultDataSet constant(String name, int totalSlots, double value) {
        if (totalSlots <= 0)
            totalSlots = 1;
        double[] xValues = new double[] { 0.0, totalSlots - 1.0 };
        double[] yValues = new double[] { value, value };
        return new DefaultDataSet(name, xValues, yValues, false);
    }

    static SplitSeries splitBySign(String name, DoubleDataset values, int historicalSlots) {
        int length = values.length();
        int startSlot = alignedStartSlot(length, historicalSlots);
        double[] xValues = xValues(startSlot, length);
        double[] positives = new double[length];
        double[] negatives = new double[length];
        for (int slot = 0; slot < length; slot++) {
            double value = values.get(length - slot - 1);
            positives[slot] = (value > 0.0) ? value : Double.NaN;
            negatives[slot] = (value < 0.0) ? value : Double.NaN;
        }
        return new SplitSeries(
                new DefaultDataSet(name + " Positive", xValues, positives, false),
                new DefaultDataSet(name + " Negative", xValues, negatives, false),
                length
        );
    }

    static Date[] dates(ChartData data) {
        CandleSeries series = data.getDataset();
        if (series == null || series.length() == 0)
            return new Date[0];

        int length = series.length();
        Date[] dates = new Date[length];
        for (int slot = 0; slot < length; slot++) {
            int seriesIndex = length - slot - 1;
            dates[slot] = Date.from(Chronological.toInstant(series.get(seriesIndex).time()));
        }
        return dates;
    }

    static TimeUnit timeUnit(TimeFrame timeFrame) {
        if (timeFrame == null)
            return TimeUnit.DAY;
        return timeFrame.getAsMonths()
                .map(months -> TimeUnit.MONTH)
                .orElseGet(() -> timeFrame.getAsSeconds()
                        .map(seconds -> exactTimeUnit(Math.abs(seconds.getAmount())))
                        .orElse(TimeUnit.DAY));
    }

    static TimeUnit timeUnit(ChartData data) {
        TimeUnit declaredUnit = timeUnit((data == null) ? null : data.getTimeFrame());
        if (data == null)
            return declaredUnit;

        CandleSeries series = data.getDataset();
        if (series == null || series.length() < 2)
            return declaredUnit;

        TimeFrame timeFrame = data.getTimeFrame();
        if (timeFrame != null && timeFrame.getAsMonths().isPresent())
            return declaredUnit;

        long inferredMillis = inferObservedIntervalMillis(series);
        if (inferredMillis <= 0L)
            return declaredUnit;

        return exactTimeUnit(Math.toIntExact(Math.max(1L, inferredMillis / 1000L)));
    }

    static double widthPercent(ChartContext context) {
        return PixelPerfectCandleGeometry.fillPercent(context.getChartProperties().getBarWidth());
    }

    static boolean supportsLogarithmicScale(ChartContext context, Range range) {
        return context.getChartProperties().getAxisLogarithmicFlag()
                && range != null
                && !range.isEmpty()
                && Double.isFinite(range.min())
                && Double.isFinite(range.max())
                && range.min() > 0.0
                && range.max() > range.min()
                && range.max() / range.min() >= 2.5;
    }

    private static int alignedStartSlot(int length, int historicalSlots) {
        return Math.max(0, historicalSlots - length);
    }

    private static TimeUnit exactTimeUnit(int seconds) {
        if (seconds <= 1)
            return TimeUnit.SECOND;
        if (seconds < 86_400)
            return fixedDurationUnit(seconds, (seconds < 60) ? "HH:mm:ss" : "HH:mm");
        if (seconds < 7 * 86_400)
            return TimeUnit.DAY;
        return TimeUnit.WEEK;
    }

    private static TimeUnit fixedDurationUnit(int seconds, String formatPattern) {
        return switch (seconds) {
            case 1 -> TimeUnit.SECOND;
            case 60 -> TimeUnit.MINUTE;
            case 3_600 -> TimeUnit.HOUR;
            default -> new FixedDurationTimeUnit(seconds * 1000L, formatPattern);
        };
    }

    private static double[] xValues(int startSlot, int length) {
        double[] xValues = new double[length];
        for (int index = 0; index < length; index++)
            xValues[index] = startSlot + index;
        return xValues;
    }

    private static long inferObservedIntervalMillis(CandleSeries series) {
        int length = series.length();
        int deltasCount = Math.min(length - 1, 255);
        if (deltasCount <= 0)
            return -1L;

        long[] positiveDeltas = new long[deltasCount];
        int collected = 0;
        for (int newerIndex = 0; newerIndex < length - 1 && collected < deltasCount; newerIndex++) {
            long newer = Chronological.toInstant(series.get(newerIndex).time()).toEpochMilli();
            long older = Chronological.toInstant(series.get(newerIndex + 1).time()).toEpochMilli();
            long delta = newer - older;
            if (delta > 0L)
                positiveDeltas[collected++] = delta;
        }
        if (collected == 0)
            return -1L;

        Arrays.sort(positiveDeltas, 0, collected);
        return positiveDeltas[collected / 2];
    }

    private static final class FixedDurationTimeUnit extends TimeUnit {
        private final long durationMillis;
        private final String formatPattern;

        private FixedDurationTimeUnit(long durationMillis, String formatPattern) {
            this.durationMillis = durationMillis;
            this.formatPattern = formatPattern;
        }

        @Override
        public java.util.Calendar previousUnitTime(java.util.Calendar cal) {
            long instant = cal.getTimeInMillis();
            var dayStart = (java.util.Calendar) cal.clone();
            dayStart.set(java.util.Calendar.HOUR_OF_DAY, 0);
            dayStart.set(java.util.Calendar.MINUTE, 0);
            dayStart.set(java.util.Calendar.SECOND, 0);
            dayStart.set(java.util.Calendar.MILLISECOND, 0);
            long dayStartMillis = dayStart.getTimeInMillis();
            long floored = dayStartMillis + ((instant - dayStartMillis) / durationMillis) * durationMillis;
            cal.setTimeInMillis(floored);
            return cal;
        }

        @Override
        public java.util.Calendar incrementTime(java.util.Calendar cal) {
            cal.setTimeInMillis(cal.getTimeInMillis() + durationMillis);
            return cal;
        }

        @Override
        public double getMillis() {
            return durationMillis;
        }

        @Override
        public String getFormatString() {
            return formatPattern;
        }

        @Override
        public String getFormatString(java.util.Locale locale) {
            return formatPattern;
        }
    }
}
