package one.chartsy.data.provider.file;

import one.chartsy.data.SimpleCandle;
import one.chartsy.time.Chronological;
import one.chartsy.util.Pair;
import org.springframework.batch.item.file.LineMapper;
import org.springframework.batch.item.file.transform.FlatFileFormatException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class SimpleCandleLineMapper implements LineMapper<SimpleCandle> {

    private final char delimiter;
    private final List<String> fields;
    private final boolean hasOpen, hasHighLow;
    private DateTimeFormatter dateFormat = DateTimeFormatter.ISO_LOCAL_DATE;
    private DateTimeFormatter timeFormat = DateTimeFormatter.ISO_LOCAL_TIME;
    private DateTimeFormatter dateTimeFormat = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private Pair<String, LocalDate> cachedLastDateParsed;

    public SimpleCandleLineMapper(char delimiter, List<String> fields) {
        this.delimiter = delimiter;
        this.fields = new ArrayList<>(fields);
        this.fields.replaceAll(String::toUpperCase);
        this.hasOpen = this.fields.contains("OPEN");
        this.hasHighLow = this.fields.contains("HIGH");
        checkRequiredFieldsPresence(this.fields);
    }

    private void checkRequiredFieldsPresence(List<String> fields) {
        if (!fields.contains("DATE") && !fields.contains("DATE_TIME"))
            throw new FlatFileFormatException("Required fields missing: DATE or DATE_TIME");
        if (!fields.contains("CLOSE"))
            throw new FlatFileFormatException("Required fields missing: CLOSE");
        if (fields.contains("HIGH") != fields.contains("LOW"))
            throw new FlatFileFormatException("Required fields missing: neither or both are allowed: HIGH,LOW");
    }

    public void setDateFormat(DateTimeFormatter dateFormat) {
        this.dateFormat = dateFormat;
    }

    public void setTimeFormat(DateTimeFormatter timeFormat) {
        this.timeFormat = timeFormat;
    }

    public void setDateTimeFormat(DateTimeFormatter dateTimeFormat) {
        this.dateTimeFormat = dateTimeFormat;
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
    public SimpleCandle mapLine(String line, int lineNumber) throws Exception {
        String[] tokens = tokenize(line, delimiter);

        double open = 0.0, high = 0.0, low = 0.0, close = 0.0, volume = 0.0;
        int count = 0;
        LocalDate date = null;
        LocalTime time = LocalTime.MIN;
        LocalDateTime dateTime = null;
        for (int i = 0, fieldCount = fields.size(); i < fieldCount; i++) {
            String field = fields.get(i);
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
        if (!hasOpen)
            open = close;
        if (!hasHighLow)
            high = low = close;

        return SimpleCandle.of(Chronological.toEpochMicros(dateTime), open, high, low, close, volume, count);
    }

    private LocalDate readDate(String token) {
        Pair<String, LocalDate> cachedDate = this.cachedLastDateParsed;
        if (cachedDate != null && cachedDate.getLeft().equals(token))
            return cachedDate.getRight();

        LocalDate result = LocalDate.parse(token, dateFormat);
        cachedLastDateParsed = Pair.of(token, result);
        return result;
    }

    private LocalDateTime readDateTime(CharSequence token) {
        return LocalDateTime.parse(token, dateTimeFormat);
    }

    private LocalTime readTime(CharSequence token) {
        return LocalTime.parse(token, timeFormat);
    }

    private double readDouble(CharSequence s) {
        return Double.parseDouble(s.toString());
    }

    private int readInt(CharSequence s) {
        return Integer.parseInt(s, 0, s.length(), 10);
    }
}
