package one.chartsy.data.provider.file;

import lombok.Getter;
import lombok.Setter;
import org.springframework.batch.item.file.FlatFileParseException;
import org.springframework.batch.item.file.LineMapper;
import org.springframework.core.io.InputStreamSource;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static java.util.Objects.requireNonNull;

@Getter
@Setter
public class FlatFileItemReader<T> {

    public static final String[] NO_COMMENT_PREFIXES = new String[0];

    private InputStreamSource inputStreamSource;
    private int linesToSkip;
    private Consumer<String> skippedLinesHandler;
    private boolean ignoreEmptyLines;
    private boolean stripLines;
    private String[] commentPrefixes = NO_COMMENT_PREFIXES;
    private String encoding = "UTF-8";
    private LineMapper<? extends T> lineMapper;

    private BufferedReader input;
    private int lineCount;

    public FlatFileItemReader() { }

    public FlatFileItemReader(FlatFileFormat fileFormat) {
        setLinesToSkip(fileFormat.getSkipFirstLines());
        setEncoding(fileFormat.getEncoding());
        setStripLines(fileFormat.isStripLines());
        setIgnoreEmptyLines(fileFormat.isIgnoreEmptyLines());
    }

    public void open() throws IOException {
        if (input != null)
            throw new IllegalStateException("FlatFileReader already open");
        if (lineMapper == null)
            throw new IllegalStateException("LineMapper not set");
        if (inputStreamSource == null)
            throw new IllegalStateException("InputStreamSource not set");

        lineCount = 0;
        input = createReader(inputStreamSource);
        for (int i = 0; i < linesToSkip; i++) {
            String line = readLine();
            if (skippedLinesHandler != null)
                skippedLinesHandler.accept(line);
        }
    }

    public boolean isOpen() {
        return input != null;
    }

    protected BufferedReader createReader(InputStreamSource iss) throws IOException {
        return new BufferedReader(new InputStreamReader(requireNonNull(iss.getInputStream()), encoding));
    }

    protected boolean isCommentLine(String line) {
        for (String prefix : commentPrefixes)
            if (line.startsWith(prefix))
                return true;

        return false;
    }

    protected String readLine() throws IOException {
        while (true) {
            String line = input.readLine();
            if (line == null)
                return null;

            lineCount++;
            if (stripLines)
                line = line.strip();
            if (isCommentLine(line) || ignoreEmptyLines && line.isEmpty())
                continue;
            return line;
        }
    }

    public T read() throws IOException {
        String line = input.readLine();
        if (line == null)
            return null;

        try {
            return lineMapper.mapLine(line, lineCount);
        } catch (FlatFileParseException e) {
            throw e;
        } catch (Exception e) {
            throw new FlatFileParseException("Unable to parse line", e, line, lineCount);
        }
    }

    public List<T> readAll() throws IOException {
        List<T> resultList = new ArrayList<>();
        T item;
        while ((item = read()) != null)
            resultList.add(item);

        return resultList;
    }

    public void close() {
        var input = this.input;
        if (input != null) {
            this.input = null;
            try {
                input.close();
            } catch (IOException e) { e.printStackTrace(); }
        }
    }
}
