package one.chartsy.core.text;

public abstract class StringSplitters {

    public static final String DEFAULT_SPLIT_CHARS = " \t\r\n,";
    public static final String DEFAULT_QUOTE_CHARS = "'\"";


    public static StringSplitter create() {
        return create(DEFAULT_SPLIT_CHARS);
    }

    public static StringSplitter create(String splitChars) {
        return create(splitChars, DEFAULT_QUOTE_CHARS);
    }

    public static StringSplitter create(String splitChars, String quoteChars) {
        return new StringSplitter(splitChars.toCharArray(), quoteChars.toCharArray());
    }
}
