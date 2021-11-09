package one.chartsy.data.provider.file;

import one.chartsy.data.SimpleCandle;
import one.chartsy.time.Chronological;
import one.chartsy.util.Pair;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.file.LineMapper;
import org.springframework.batch.item.file.transform.FlatFileFormatException;

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
        private final boolean hasOpen, hasHighAndLow;
        private final DateTimeFormatter dateFormat;
        private final DateTimeFormatter timeFormat;
        private final DateTimeFormatter dateTimeFormat;


        public Type(char delimiter, List<String> fields) {
            this(delimiter, fields, ISO_LOCAL_DATE);
        }

        public Type(char delimiter, List<String> fields, DateTimeFormatter dateFormat) {
            this(delimiter, fields, dateFormat, ISO_LOCAL_TIME, ISO_LOCAL_DATE_TIME);
        }

        public Type(char delimiter, List<String> fields, DateTimeFormatter dateFormat, DateTimeFormatter timeFormat, DateTimeFormatter dateTimeFormat) {
            this.delimiter = delimiter;
            this.fields = new ArrayList<>(fields);
            this.fields.replaceAll(String::toUpperCase);
            this.dateFormat = dateFormat;
            this.timeFormat = timeFormat;
            this.dateTimeFormat = dateTimeFormat;
            this.hasOpen = this.fields.contains("OPEN");
            this.hasHighAndLow = this.fields.contains("HIGH");
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
            if (!fields.contains("DATE") && !fields.contains("DATE_TIME"))
                throw new FlatFileFormatException("Required fields missing: DATE or DATE_TIME");
            if (!fields.contains("CLOSE"))
                throw new FlatFileFormatException("Required fields missing: CLOSE");
            if (fields.contains("HIGH") != fields.contains("LOW"))
                throw new FlatFileFormatException("Required fields missing: neither or both are allowed: HIGH,LOW");
        }

        @Override
        public LineMapper<SimpleCandle> createLineMapper(ExecutionContext context) {
            return new SimpleCandleLineMapper(this);
        }
    }

    private final Type type;
    private Pair<String, LocalDate> cachedLastDateParsed;


    public SimpleCandleLineMapper(Type type) {
        this.type = type;
    }

    public final Type getType() {
        return type;
    }

    protected String[] tokenize(String line, char delimiter) {
        int tokenCount = 1;
        for (int i = 0, j; (j = line.indexOf(delimiter, i)) >= 0; i = j+1)
            tokenCount++;

        String[] tokens = new String[tokenCount];
        int index = 0;
        for (int i = 0, j; (j = line.indexOf(delimiter, i)) >= 0; i = j+1)
            tokens[index++] = line.substring(i, j);

        return tokens;
    }

    @Override
    public SimpleCandle mapLine(String line, int lineNumber) {
        String[] tokens = tokenize(line, type.delimiter);

        double open = 0.0, high = 0.0, low = 0.0, close = 0.0, volume = 0.0;
        int count = 0;
        LocalDate date = null;
        LocalTime time = LocalTime.MIN;
        LocalDateTime dateTime = null;
        for (int i = 0, fieldCount = type.fields.size(); i < fieldCount; i++) {
            String field = type.fields.get(i);
            String token = tokens[i];
            switch (field) {
                case "DATE" -> date = readDate(token);
                case "DATE_TIME" -> dateTime = readDateTime(token);
                case "TIME" -> time = readTime(token);
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

        return SimpleCandle.of(Chronological.toEpochMicros(dateTime), open, high, low, close, volume, count);
    }

    protected LocalDate readDate(String token) {
        Pair<String, LocalDate> cachedDate = this.cachedLastDateParsed;
        if (cachedDate != null && cachedDate.getLeft().equals(token))
            return cachedDate.getRight();

        LocalDate result = LocalDate.parse(token, type.dateFormat);
        cachedLastDateParsed = Pair.of(token, result);
        return result;
    }

    protected LocalDateTime readDateTime(CharSequence token) {
        return LocalDateTime.parse(token, type.dateTimeFormat);
    }

    protected LocalTime readTime(CharSequence token) {
        return LocalTime.parse(token, type.timeFormat);
    }

    protected double readDouble(CharSequence s) {
        return Double.parseDouble(s.toString());
    }

    protected int readInt(CharSequence s) {
        return Integer.parseInt(s, 0, s.length(), 10);
    }
}
