/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.data.provider.file;

import one.chartsy.Candle;
import one.chartsy.TimeFrame;
import one.chartsy.TimeFrameHelper;
import one.chartsy.context.ExecutionContext;
import one.chartsy.data.SimpleCandle;
import one.chartsy.text.FromString;
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

        private static final class FieldSpec {
            final String name;
            final boolean optional;

            FieldSpec(String raw) {
                String s = raw.trim();
                boolean opt = false;

                if (s.endsWith("?")) {
                    s = s.substring(0, s.length() - 1);
                    opt = true;
                }
                this.name = s.toUpperCase();
                this.optional = opt;
            }
        }

        private final char delimiter;
        /** The original field descriptors as passed to the constructor, kept to preserve optional markers. */
        private final List<String> rawFields;

        /** Upper-cased field names without optional markers, kept for compatibility and quick membership tests. */
        private final List<String> fields;

        /** Parsed field specs. */
        private final List<FieldSpec> specs;

        private final boolean hasOpenDeclared;
        private final boolean hasHighDeclared;
        private final boolean hasLowDeclared;
        private final boolean hasTimeAtOpenDeclared;

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

            List<String> rawCopy = new ArrayList<>(fields);
            this.rawFields = List.copyOf(rawCopy);

            // Parse to specs and to plain upper-cased names list
            List<FieldSpec> parsed = new ArrayList<>(rawCopy.size());
            List<String> upperNames = new ArrayList<>(rawCopy.size());
            for (String f : rawCopy) {
                FieldSpec fs = new FieldSpec(f);
                parsed.add(fs);
                upperNames.add(fs.name);
            }
            this.specs = List.copyOf(parsed);
            this.fields = List.copyOf(upperNames);

            this.dateFormat = dateFormat;
            this.timeFormat = timeFormat;
            this.dateTimeFormat = dateTimeFormat;

            this.hasOpenDeclared = this.fields.contains("OPEN");
            this.hasHighDeclared = this.fields.contains("HIGH");
            this.hasLowDeclared  = this.fields.contains("LOW");
            this.hasTimeAtOpenDeclared = this.fields.contains("OPEN_TIME") || this.fields.contains("OPEN_DATE_TIME");

            checkRequiredFieldsPresence(this.specs);

            // Static configuration sanity checks
            if (this.hasHighDeclared != this.hasLowDeclared) {
                throw new FlatFileFormatException("Required fields missing: neither or both are allowed: HIGH,LOW");
            }
        }

        public Type withDateFormat(DateTimeFormatter dateFormat) {
            return new Type(delimiter, rawFields, dateFormat, timeFormat, dateTimeFormat);
        }

        public Type withTimeFormat(DateTimeFormatter timeFormat) {
            return new Type(delimiter, rawFields, dateFormat, timeFormat, dateTimeFormat);
        }

        public Type withDateTimeFormat(DateTimeFormatter dateTimeFormat) {
            return new Type(delimiter, rawFields, dateFormat, timeFormat, dateTimeFormat);
        }

        public Type withDateAndTimeFormat(DateTimeFormatter dateFormat, DateTimeFormatter timeFormat) {
            return new Type(delimiter, rawFields, dateFormat, timeFormat, dateTimeFormat);
        }

        private static void checkRequiredFieldsPresence(List<FieldSpec> specs) {
            boolean hasDate     = false;
            boolean hasDateTime = false;
            boolean hasOpenDT   = false;

            for (FieldSpec fs : specs) {
                if ("DATE".equals(fs.name)) hasDate = true;
                if ("DATE_TIME".equals(fs.name)) hasDateTime = true;
                if ("OPEN_DATE_TIME".equals(fs.name)) hasOpenDT = true;
            }
            if (!hasDate && !hasDateTime && !hasOpenDT) {
                throw new FlatFileFormatException("Required fields missing: DATE, DATE_TIME or OPEN_DATE_TIME");
            }

            boolean hasClose = false;
            for (FieldSpec fs : specs) {
                if ("CLOSE".equals(fs.name)) {
                    hasClose = true;
                    break;
                }
            }
            if (!hasClose)
                throw new FlatFileFormatException("Required fields missing: CLOSE");
        }

        @Override
        public LineMapper<SimpleCandle> createLineMapper(ExecutionContext context) {
            return new SimpleCandleLineMapper(this, context);
        }
    }

    private final Type type;
    private final long configuredOpenTimeShiftMicros;
    private final boolean isIntraday;
    private final ExecutionContext context;
    private Candle last;

    public SimpleCandleLineMapper(Type type, ExecutionContext context) {
        var timeFrame = (TimeFrame) context.get("TimeFrame");
        this.type = type;
        this.isIntraday = TimeFrameHelper.isIntraday(timeFrame);
        this.configuredOpenTimeShiftMicros = type.hasTimeAtOpenDeclared ? getCandleTimeShift(timeFrame) : 0L;
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

    /**
     * Split line by a single-character delimiter, preserving empty tokens and trailing empties.
     */
    private static List<CharSequence> splitLine(String line, char delimiter) {
        List<CharSequence> tokens = new ArrayList<>();
        int start = 0;
        for (int i = 0, n = line.length(); i < n; i++) {
            if (line.charAt(i) == delimiter) {
                tokens.add(line.substring(start, i));
                start = i + 1;
            }
        }
        tokens.add(line.substring(start));
        return tokens;
    }

    @Override
    public SimpleCandle mapLine(String line, int lineNumber) {
        final List<CharSequence> tokens = splitLine(line, type.delimiter);
        final int tokenCount = tokens.size();

        double open = 0.0, high = 0.0, low = 0.0, close = 0.0, volume = 0.0;
        int count = 0;

        boolean presentOpen = false;
        boolean presentHigh = false;
        boolean presentLow  = false;
        boolean presentClose = false;

        LocalDate date = null;
        LocalTime time = LocalTime.MIN;
        LocalDateTime dateTime = null;
        long timeShiftMicrosForThisLine = 0L;

        // Precompute "required remaining" suffix counts to decide when to consume tokens for optional specs.
        final int n = type.specs.size();
        final int[] requiredRemainingAfter = new int[n + 1];
        requiredRemainingAfter[n] = 0;
        for (int i = n - 1; i >= 0; i--) {
            requiredRemainingAfter[i] = requiredRemainingAfter[i + 1] + (type.specs.get(i).optional ? 0 : 1);
        }

        int j = 0; // token index
        for (int i = 0; i < n; i++) {
            if (j > tokenCount) break; // safety

            Type.FieldSpec fs = type.specs.get(i);
            int tokensLeft = tokenCount - j;

            if (fs.optional) {
                // Consume this optional token only if we have more tokens left than required specs remaining after i.
                if (tokensLeft <= requiredRemainingAfter[i + 1]) {
                    // Not enough tokens to spend on this optional; skip without consuming.
                    continue;
                }
            } else {
                // Required field must have a token available now.
                if (tokensLeft == 0) {
                    throw new FlatFileParseException(
                            "Missing required field: " + fs.name, line);
                }
            }

            // Consume one token for this spec.
            CharSequence token = tokens.get(j++);
            switch (fs.name) {
                case "DATE" -> date = readDate(token);
                case "DATE_TIME" -> {
                    dateTime = readDateTime(token);
                    timeShiftMicrosForThisLine = 0;
                }
                case "OPEN_DATE_TIME" -> {
                    if (dateTime == null) {
                        dateTime = readDateTime(token);
                        timeShiftMicrosForThisLine = configuredOpenTimeShiftMicros;
                    }
                }
                case "TIME" -> {
                    time = readTime(token);
                    timeShiftMicrosForThisLine = 0;
                }
                case "OPEN_TIME" -> {
                    if (time == LocalTime.MIN) {
                        time = readTime(token);
                        timeShiftMicrosForThisLine = configuredOpenTimeShiftMicros;
                    }
                }
                case "OPEN" -> { open = readDouble(token); presentOpen = true; }
                case "HIGH" -> { high = readDouble(token); presentHigh = true; }
                case "LOW"  -> { low  = readDouble(token); presentLow  = true; }
                case "CLOSE" -> { close = readDouble(token); presentClose = true; }
                case "VOLUME" -> volume = readDouble(token);
                case "COUNT"  -> count = readInt(token);
                case "SKIP"   -> { /* deliberately ignore */ }
                default -> throw new FlatFileFormatException("Unsupported field: " + fs.name);
            }
        }

        // Ignore any extra trailing tokens beyond declared specs for compatibility with previous behavior.

        if (!presentClose) {
            throw new FlatFileParseException("Missing required field: CLOSE", line);
        }

        if (dateTime == null) {
            if (date == null) {
                throw new FlatFileParseException(
                        "Missing date information: expected DATE or DATE_TIME or OPEN_DATE_TIME", line);
            }
            dateTime = LocalDateTime.of(date, time);
        }

        if (!isIntraday) {
            dateTime = dateTime.plusDays(1);
        }

        if (!presentOpen) {
            open = close;
        }
        if (presentHigh ^ presentLow) {
            throw new FlatFileParseException("Invalid line: exactly one of HIGH or LOW present; both or none are required", line);
        }
        if (!presentHigh && !presentLow) {
            high = close;
            low  = close;
        }

        long candleTime = Chronological.toEpochNanos(dateTime) + timeShiftMicrosForThisLine;
        if (last != null && candleTime <= last.time())
            throw new FlatFileParseException(String.format("Invalid candle order at line %s following candle %s", lineNumber, last), line);

        SimpleCandle candle = SimpleCandle.of(candleTime, open, high, low, close, volume);
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
