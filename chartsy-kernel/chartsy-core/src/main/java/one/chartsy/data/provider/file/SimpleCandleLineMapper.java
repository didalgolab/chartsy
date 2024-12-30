/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.data.provider.file;

import one.chartsy.Candle;
import one.chartsy.TimeFrame;
import one.chartsy.context.ExecutionContext;
import one.chartsy.data.SimpleCandle;
import one.chartsy.text.FromString;
import one.chartsy.text.MutableStringFragmentIterator;
import one.chartsy.time.Chronological;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import static java.time.format.DateTimeFormatter.*;

public class SimpleCandleLineMapper implements LineMapper<SimpleCandle> {

    public static class Type implements LineMapperType<SimpleCandle> {
        private final char delimiter;
        private final List<String> fields;
        private final boolean hasOpen, hasHighAndLow, hasTimeAtOpen;
        private final DateTimeFormatter dateFormat;
        private final DateTimeFormatter timeFormat;
        private final DateTimeFormatter dateTimeFormat;

        public Type(char delimiter, List<String> fields) {
            this(delimiter, fields, ISO_LOCAL_DATE);
        }

        public Type(char delimiter, List<String> fields, DateTimeFormatter dateOrDatetimeFormat) {
            this(delimiter, fields, dateOrDatetimeFormat, ISO_LOCAL_TIME, dateOrDatetimeFormat);
        }

        public Type(char delimiter, List<String> fields, DateTimeFormatter dateFormat, DateTimeFormatter timeFormat) {
            this(delimiter, fields, dateFormat, timeFormat, ISO_LOCAL_DATE_TIME);
        }

        public Type(char delimiter, List<String> fields, DateTimeFormatter dateFormat, DateTimeFormatter timeFormat, DateTimeFormatter dateTimeFormat) {
            this.delimiter = delimiter;
            fields = new ArrayList<>(fields);
            fields.replaceAll(String::toUpperCase);
            this.fields = List.copyOf(fields);
            this.dateFormat = dateFormat;
            this.timeFormat = timeFormat;
            this.dateTimeFormat = dateTimeFormat;
            this.hasOpen = fields.contains("OPEN");
            this.hasHighAndLow = fields.contains("HIGH");
            this.hasTimeAtOpen = fields.contains("OPEN_TIME") || fields.contains("OPEN_DATE_TIME");
            checkRequiredFieldsPresence(this.fields);
        }

        public Type withDateFormat(DateTimeFormatter dateFormat) {
            return new Type(delimiter, fields, dateFormat, timeFormat, dateTimeFormat);
        }

        public Type withTimeFormat(DateTimeFormatter timeFormat) {
            return new Type(delimiter, fields, dateFormat, timeFormat, dateTimeFormat);
        }

        public Type withDateTimeFormat(DateTimeFormatter dateTimeFormat) {
            return new Type(delimiter, fields, dateFormat, timeFormat, dateTimeFormat);
        }

        public Type withDateAndTimeFormat(DateTimeFormatter dateFormat, DateTimeFormatter timeFormat) {
            return new Type(delimiter, fields, dateFormat, timeFormat, dateTimeFormat);
        }

        private static void checkRequiredFieldsPresence(List<String> fields) {
            if (!fields.contains("DATE") && !fields.contains("DATE_TIME") && !fields.contains("OPEN_DATE_TIME"))
                throw new FlatFileFormatException("Required fields missing: DATE, DATE_TIME or OPEN_DATE_TIME");
            if (!fields.contains("CLOSE"))
                throw new FlatFileFormatException("Required fields missing: CLOSE");
            if (fields.contains("HIGH") != fields.contains("LOW"))
                throw new FlatFileFormatException("Required fields missing: neither or both are allowed: HIGH,LOW");
        }

        @Override
        public LineMapper<SimpleCandle> createLineMapper(ExecutionContext context) {
            return new SimpleCandleLineMapper(this, context);
        }
    }

    private final Type type;
    private final long timeShift;
    private final ExecutionContext context;
    private Candle last;


    public SimpleCandleLineMapper(Type type, ExecutionContext context) {
        this.type = type;
        this.timeShift = type.hasTimeAtOpen ? getCandleTimeShift((TimeFrame) context.get("TimeFrame")) : 0;
        this.context = context;
    }

    protected static long getCandleTimeShift(TimeFrame timeFrame) {
        if (!(timeFrame instanceof TimeFrame.TemporallyRegular t))
            throw new UnsupportedOperationException(String.format("TimeFrame `%s` is not temporally regular", timeFrame));

        long nanosShift = Duration.from(t.getRegularity()).toNanos();
        if (nanosShift % 1000 != 0)
            throw new UnsupportedOperationException(String.format("TimeFrame `%s` has not supported duration: %s nanos", timeFrame, nanosShift));

        return nanosShift / 1000L;
    }

    public final Type getType() {
        return type;
    }

    protected MutableStringFragmentIterator tokenize(String line, char delimiter) {
        return MutableStringFragmentIterator.forSplit(line, delimiter);
    }

    @Override
    public SimpleCandle mapLine(String line, int lineNumber) {
        var iter = tokenize(line, type.delimiter);

        double open = 0.0, high = 0.0, low = 0.0, close = 0.0, volume = 0.0;
        int count = 0;
        LocalDate date = null;
        LocalTime time = LocalTime.MIN;
        LocalDateTime dateTime = null;
        long timeShift = 0;
        for (int i = 0, fieldCount = type.fields.size(); i < fieldCount; i++) {
            String field = type.fields.get(i);
            CharSequence token = iter.next();
            switch (field) {
                case "DATE" -> date = readDate(token);
                case "DATE_TIME" -> {
                    dateTime = readDateTime(token);
                    timeShift = 0;
                }
                case "OPEN_DATE_TIME" -> {
                    if (dateTime == null) {
                        dateTime = readDateTime(token);
                        timeShift = this.timeShift;
                    }
                }
                case "TIME" -> {
                    time = readTime(token);
                    timeShift = 0;
                }
                case "OPEN_TIME" -> {
                    if (time == LocalTime.MIN) {
                        time = readTime(token);
                        timeShift = this.timeShift;
                    }
                }
                case "OPEN" -> open = readDouble(token);
                case "HIGH" -> high = readDouble(token);
                case "LOW" -> low = readDouble(token);
                case "CLOSE" -> close = readDouble(token);
                case "VOLUME" -> volume = readDouble(token);
                case "COUNT" -> count = readInt(token);
                case "SKIP" -> {}
                default -> throw new FlatFileFormatException("Unsupported field: " + field);
            }
        }

        if (dateTime == null)
            dateTime = LocalDateTime.of(date, time);
        if (!type.hasOpen)
            open = close;
        if (!type.hasHighAndLow)
            high = low = close;

        var candleTime = Chronological.toEpochNanos(dateTime) + timeShift;
        if (last != null && candleTime <= last.getTime())
            throw new FlatFileParseException(String.format("Invalid candle order at line %s following candle %s", lineNumber, last), line);

        var candle = SimpleCandle.of(candleTime, open, high, low, close, volume, count);
        last = candle;
        return candle;
    }

    protected LocalDate readDate(CharSequence str) {
        return context.getCachedLocalDate(str, type.dateFormat);
    }

    protected LocalTime readTime(CharSequence str) {
        return context.getCachedLocalTime(str, type.timeFormat);
    }

    protected LocalDateTime readDateTime(CharSequence str) {
        return LocalDateTime.parse(str, type.dateTimeFormat);
    }

    protected double readDouble(CharSequence str) {
        return FromString.toDouble(str);
    }

    protected int readInt(CharSequence str) {
        return FromString.toInt(str);
    }
}
