package one.chartsy.core.json;

public interface JsonFormatter {
    String toJson(Object src);
    <T> T fromJson(String json, Class<T> resultType);
}
