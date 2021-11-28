package one.chartsy.ui.chart;

public enum SysParams {
    AUTOCOMPLETE_DELAY(500.0, "The delay in milliseconds between a keystroke and when the autocompleter widget displays the suggestion popup"),
    AUTOCOMPLETE_VISIBLE_ROW_COUNT(30.0, "The maximum number of rows visible in the autocompleter widget"),
    AUTOCOMPLETE_MAXIMUM_ROW_COUNT(1000.0, "The maximum number of rows possible to store in the scrollable list"),
    AUTOCOMPLETE_ACCEPT_CLICK_COUNT(1.0, "The number of click counts required to fire the accept action for a list item"),
    ANNOTATION_HANDLE_SIZE(6);

    SysParams(Object value) {
        this(value, "");
    }

    SysParams(Object value, String description) {
        this.value = value;
    }

    private final Object value;

    public int intValue() {
        return ((Number) value).intValue();
    }
}
