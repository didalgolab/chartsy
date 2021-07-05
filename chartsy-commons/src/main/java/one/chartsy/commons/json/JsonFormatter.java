package one.chartsy.commons.json;

public interface JsonFormatter {
    String toJson(Object src);
    <T> T fromJson(String json, Class<T> resultType);
}
