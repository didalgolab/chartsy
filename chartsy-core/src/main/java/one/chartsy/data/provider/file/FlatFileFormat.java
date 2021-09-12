package one.chartsy.data.provider.file;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class FlatFileFormat {
    private String encoding;
    private int linesToSkip;
    private boolean ignoreEmptyLines;
    private boolean stripLines;
    private List<String> fields;
    private char fieldDelimiter;

}
