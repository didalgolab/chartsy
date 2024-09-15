/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.samples;

import one.chartsy.*;
import one.chartsy.core.services.DefaultTimeFrameServices;
import one.chartsy.data.CandleSeries;
import one.chartsy.data.SimpleCandle;
import one.chartsy.data.file.SymbolResourceFiles;
import one.chartsy.data.market.Tick;
import one.chartsy.time.Chronological;

import java.io.IOException;
import java.nio.file.Path;
import java.time.*;
import java.util.*;

import static one.chartsy.TimeFrameHelper.*;

public class TimeFrameAggregationVerificationSample {


    public static void main(String[] args) throws IOException {
        CandleSeries series = SymbolResourceFiles.newCandleSeries(SymbolResource.of("EURUSD", TimeFrame.Period.H1), Path.of("C:/Work/Data/Forex/EURUSD.COMP.gz"));
        for (int i = 1; i <= 10; i++) {
            System.out.println(series.get(series.length() - i));
            if (i % 5 == 4)
                System.out.println();
        }
        System.out.println("...");
        System.out.println(series.getLast());

        int r = 0;
        long totalTimeBF = 0, totalTimeReq = 0;
        for (TimeFrame timeFrame : timeFrames) {
            long startTime;
            List<Candle> candles = new ArrayList<>();

            startTime = System.nanoTime();
            TimeFrame.TemporallyRegular tbf = (TimeFrame.TemporallyRegular) timeFrame;
            TimeFrameAggregator<Candle, Tick> aggregator = tbf.getAggregator(new DefaultTimeFrameServices());
//            if (tbf.getDuration() instanceof Period)
//                aggregator = new PeriodCandleAggregator<>(new SimpleCandleBuilder(), (Period)tbf.getDuration(), new PeriodCandleAlignment(tbf.getDailyAlignmentTimeZone(), tbf.getDailyAlignment(), (TemporalAdjuster) tbf.getCandleAlignment().orElse(null)));
//            else if (tbf.getDuration() instanceof Months)
//                aggregator = new PeriodCandleAggregator<>(new SimpleCandleBuilder(), ((Months)tbf.getDuration()).toPeriod(), new PeriodCandleAlignment(tbf.getDailyAlignmentTimeZone(), tbf.getDailyAlignment(), (TemporalAdjuster) tbf.getCandleAlignment().orElse(null)));
//            else
//                aggregator = new TimeCandleAggregator<>(new SimpleCandleBuilder(), (Duration) ((TimeBasedTimeFrame) timeFrame).getDuration(), new TimeCandleAlignment(timeFrame.getDailyAlignmentTimeZone(), timeFrame.getDailyAlignment()));
            Incomplete<Candle> last = null;
            for (int i = series.length() - 1; i >= 0; i--)
                last = aggregator.addCandle(series.get(i), candles::add);
            if (last.isPresent())
                candles.add(last.get());
            long elapsedTime2 = System.nanoTime() - startTime;
            r += candles.size();

            startTime = System.nanoTime();
            CandleSeries result = bruteForceTimeFrameCompress(timeFrame, series);
            long elapsedTimeBF = System.nanoTime() - startTime;
            r += result.length();

            System.out.println("TIME INFO - BF: " + elapsedTimeBF/1000_000L + " ms - SPI: " + elapsedTime2/1000_000L + " ms - " + timeFrame + "." + aggregator.getClass().getSimpleName());
            totalTimeBF += elapsedTimeBF;
            totalTimeReq += elapsedTime2;

            Collections.reverse(candles);
            if (result.length() != candles.size())
                throw new RuntimeException(timeFrame + ": old.size=" + result.length() + ", new.size=" + candles.size() +
                        "\n\t  Last old.candle: " + result.get(0) +
                        "\n\t  Last new.candle: " + candles.get(0) +
                        "\n\t First old.candle: " + result.get(result.length() - 1) +
                        "\n\t First new.candle: " + candles.get(candles.size() - 1)
                );
            for (int i = Math.max(result.length(), candles.size()) - 1; i >= 0; i--) {
                Candle c1 = candles.get(i);
                Candle c2 = SimpleCandle.from(result.get(i));
                if ((timeFrame == TimeFrame.Period.DAILY || timeFrame == TimeFrame.Period.WEEKLY || timeFrame == TimeFrame.Period.MONTHLY || timeFrame == TimeFrame.Period.QUARTERLY || timeFrame == TimeFrame.Period.YEARLY) && c1.getTime() % 86_400_000_000L == 0) {
                    c2 = Candle.of((c2.getTime() + 1), c2.open(), c2.high(), c2.low(), c2.close(), c2.volume(), c2.turnover(), c2.trades());
                }
                if (!c1.equals(c2))
                    throw new RuntimeException(timeFrame + ": " + c1 + " vs " + c2);
            }
            System.out.println("OK");

            //Runtime.getRuntime().gc();
//            try {
//                Thread.sleep(100L);
//            } catch (InterruptedException e) {
//                e.printStackTrace();
//            }
//            int k = n;
//            while (k % 10 == 0)
//                k /= 10;
//            if (k == 1 || k == 2 || k == 5)
//                System.out.println("\t" + n + ": " + elapsedTime/1000_000L + " ms, " + elapsedTime/series.length() + " ns/bar");
        }

        System.out.println("Total Time INFO - BF: " + totalTimeBF/1000_000L + "ms - REG: " + totalTimeReq/1000_000L + "ms");

//        System.out.println(r);
//        System.out.println(candles.get(0));
//        System.out.println(candles.get(1));
//        //System.out.println(candles.get(candles.size() - 1));
    }

    private static final TimeFrame[] timeFrames = new TimeFrame[] {
            TimeFrame.Period.DAILY,
            TimeFrame.Period.WEEKLY,
            TimeFrame.Period.MONTHLY,
            TimeFrame.Period.QUARTERLY,
            TimeFrame.Period.YEARLY,
            TimeFrame.Period.M1,
            TimeFrame.Period.M2,
            TimeFrame.Period.M3,
            TimeFrame.Period.M4,
            TimeFrame.Period.M5,
            TimeFrame.Period.M6,
            TimeFrame.Period.M10,
            TimeFrame.Period.M12,
            TimeFrame.Period.M15,
            TimeFrame.Period.M20,
            TimeFrame.Period.M30,
            TimeFrame.Period.M45,
            TimeFrame.Period.H1,
            TimeFrame.Period.M90,
            TimeFrame.Period.H2,
            TimeFrame.Period.H3,
            TimeFrame.Period.H4,
            TimeFrame.Period.H6,
            TimeFrame.Period.H8,
            TimeFrame.Period.H12,
    };

    /** The number of nanoseconds elapsed since Monday, January 1st, 2001. */
    private static final long reftime2 = Chronological.toEpochNanos(OffsetDateTime.of(LocalDateTime.of(2001, 1, 1, 0, 0), ZoneOffset.UTC));

    public static CandleSeries bruteForceTimeFrameCompress(TimeFrame targetTF, CandleSeries quotes) {
        TimeFrame frame = quotes.getTimeFrame();
        // check if required time frame is already provided
        //if (toSecondsExact(targetTF) == toSecondsExact(frame)) && toMonthsExact(targetTF)) == frame.months())
        //    return quotes;
        // check if time frame frequency compression is possible
        ////if (!targetTF.isAssignableFrom(frame))
        ////    throw new IllegalArgumentException("TimeFrame's mismatch: " + frame + " Time Frame is not compressible to " + targetTF);

        int seconds = toSeconds(targetTF).orElse(0);
        if (seconds == 0 && toMonths(targetTF).orElse(0) > 0) {
            // need to compress to monthly or higher target
            // compress to daily first to speed up performance
            if (TimeFrameHelper.isIntraday(frame) && isAssignableFrom(TimeFrame.Period.DAILY, frame))
                quotes = bruteForceTimeFrameCompress(TimeFrame.Period.DAILY, quotes);
            return bruteForceTimeFrameCompressLarge(targetTF, quotes, TimeFrameHelper.toMonthsExact(targetTF));
        }
        long micros = seconds * 1000_000L;
        ArrayList<Candle> buffer = new ArrayList<>();
        boolean compressingFromIntraToDaily = (TimeFrameHelper.isIntraday(frame) && !TimeFrameHelper.isIntraday(targetTF));
        double close = 0, open = 0, low = 0, high = 0, volume = 0;
        int openInterest = 0;
        long time = 0;
        int size = quotes.length();
        int off = TimeFrameHelper.isIntraday(frame)? 1 : 0; // extra intraday time offset
        for (int barNo = size-1; barNo >= 0; barNo--) {
            Candle q = quotes.get(barNo);
            long t = q.getTime();
            if (t < time && time != 0)
                continue;
            if ((time - reftime2 - off)/micros != (t - reftime2 - off)/micros || time == 0) {
                if (time != 0) {
                    if (compressingFromIntraToDaily && (time - reftime2) % 86400000000L == 0)
                        time--;
                    buffer.add(Candle.of(time, open, high, low, close, volume, openInterest));
                }
                open = q.open();
                high = q.high();
                low = q.low();
                close = q.close();
                volume = q.volume();
                openInterest = q.trades();
                time = t;
            } else {
                double h = q.high();
                if (h > high)
                    high = h;
                double l = q.low();
                if (l < low)
                    low = l;
                close = q.close();
                volume += q.volume();
                openInterest = q.trades();
                time = t;
            }
        }
        buffer.add(Candle.of(time, open, high, low, close, volume, openInterest));
        Candle[] result = new Candle[buffer.size()];
        result = buffer.toArray(result);
        for (int i=0, mid=result.length>>1, j=result.length-1; i<mid; i++, j--) {
            Candle q = result[i];
            result[i] = result[j];
            result[j] = q;
        }
        return CandleSeries.of(quotes.getResource().withTimeFrame(targetTF), Arrays.asList(result));
    }

    private static boolean sameBar(Calendar t1, Calendar t2, int months) {
        final int REFERENCE_YEAR = 2001;
        int elapsedMonths1 = 12*(t1.get(Calendar.YEAR) - REFERENCE_YEAR) + t1.get(Calendar.MONTH);
        int elapsedMonths2 = 12*(t2.get(Calendar.YEAR) - REFERENCE_YEAR) + t2.get(Calendar.MONTH);

        return elapsedMonths1 / months == elapsedMonths2 / months;
    }

    private static CandleSeries bruteForceTimeFrameCompressLarge(TimeFrame targetTF, CandleSeries quotes, int months) {
        List<Candle> buffer = new ArrayList<>();
        double close = 0, open = 0, low = 0, high = 0, volume = 0;
        int openInterest = 0;
        long time = 0;
        int size = quotes.length();
        Calendar cal1 = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
        Calendar cal2 = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
        for (int barNo = size-1; barNo >= 0; barNo--) {
            Candle q = quotes.get(barNo);
            long t = q.getTime();
            if (t < time && time != 0)
                continue;
            cal2.setTimeInMillis(t / 1000);
            if (time == 0 || !sameBar(cal1, cal2, months)) {
                if (time != 0)
                    buffer.add(Candle.of(time, open, high, low, close, volume, openInterest));
                open = q.open();
                high = q.high();
                low = q.low();
                close = q.close();
                volume = q.volume();
                openInterest = q.trades();
            } else {
                double h = q.high();
                if (h > high)
                    high = h;
                double l = q.low();
                if (l < low)
                    low = l;
                close = q.close();
                volume += q.volume();
                openInterest = q.trades();
            }
            cal1.setTimeInMillis((time = t) / 1000);
        }
        buffer.add(Candle.of(time, open, high, low, close, volume, openInterest));
        Collections.reverse(buffer);
        return CandleSeries.of(quotes.getResource().withTimeFrame(targetTF), buffer);
    }

    private static boolean isAssignableFrom(TimeFrame a, TimeFrame b) {
        if (a == TimeFrame.Period.DAILY) {
            return (b instanceof TimeFrame.Period && TimeFrameHelper.isIntraday(b));
        }
        else throw new UnsupportedOperationException();
    }
}
