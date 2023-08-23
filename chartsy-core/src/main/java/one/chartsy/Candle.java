/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy;

import one.chartsy.data.SimpleCandle;
import one.chartsy.time.Chronological;

import java.time.LocalDateTime;
import java.time.temporal.ChronoField;

/**
 * A symbol's price record also known as "bar" in a particular time frame. Both
 * the {@code TimeFrame} and the {@code Symbol} definitions are not part of the
 * {@code Candle} instance, but instead they are defined in an object wrapping an
 * array of candles - the {@code CandleSeries} object - representing a continuous,
 * chronologically ordered series of price changes.
 *
 * <p>
 * Each price record specifies a set of symbol's price values ({@link #open},
 * {@link #high}, {@link #low}, {@link #close}) and an optional {@link #volume}.
 * Every price record has an associated
 * {@link #getTime() timestamp} representing the moment in time when the price bar
 * occurred, down to microsecond accuracy. If the time frame used is not a
 * <i>tick</i> but represents some continuous time span (e.g. 15 minutes), the
 * {@link #getTime() time} reflects the <b>end time</b> of each time frame period (i.e. the
 * ending time of forming the particular bar). If time frame period is a
 * multiple of {@link TimeFrame.Period#DAILY daily period}, then a time
 * component is irrelevant and might be missing in the {@link #getTime() time} field.
 *
 * <p>
 * It should be emphasized that the {@link Candle#getTime} is an internal
 * representation of time down to microsecond accuracy, and as such is not an
 * equivalent of a Unix timestamp or a Java millis-from-the-epoch. Use
 * {@link Chronological#toDateTime(long)} and
 * {@link Chronological#toEpochMicros(LocalDateTime)} to convert between internal
 * representation and Java date/time objects.
 *
 * @author Mariusz Bernacki
 */
public interface Candle extends Chronological {
    double open();
    double high();
    double low();
    double close();
    double volume();
    int count();

    default int get(ChronoField field) {
        return getDateTime().get(field);
    }

    default boolean isBullish() {
        return open() < close();
    }

    default boolean isBearish() {
        return open() > close();
    }

    default boolean isDoji() {
        return open() == close();
    }

    default double weightedClose() {
        return (2*close() + high() + low()) / 4;
    }

    default double averagePrice() {
        return (open() + high() + low() + close()) / 4;
    }

    default double typicalPrice() {
        return (close() + high() + low()) / 3;
    }

    default double medianPrice() {
        return (high() + low()) / 2;
    }

    default double range() {
        return high() - low();
    }


    static Candle of(long time, double price) {
        return of(time, price, price, price, price);
    }

    static Candle of(long time, double open, double high, double low, double close) {
        return of(time, open, high, low, close, 0.0, 0);
    }

    static Candle of(long time, double open, double high, double low, double close, double volume) {
        return of(time, open, high, low, close, volume, 0);
    }

    static Candle of(long time, double open, double high, double low, double close, double volume, int count) {
        return SimpleCandle.of(time, open, high, low, close, volume, count);
    }

    static int direction(Candle c) {
        return (c.close() > c.open())? 1 : 0;
    }
}
