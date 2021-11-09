package one.chartsy.data;

public class UnsupportedDataQueryException extends RuntimeException {

    private final DataQuery<?> unsupportedQuery;

    public UnsupportedDataQueryException(DataQuery<?> unsupportedQuery, String message) {
        super(message);
        this.unsupportedQuery = unsupportedQuery;
    }

    public DataQuery<?> getUnsupportedQuery() {
        return unsupportedQuery;
    }
}
