package one.chartsy.ui.chart;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import one.chartsy.core.Named;

import java.io.IOException;
import java.util.Objects;
import java.util.function.Function;

public final class NamedResolverTypeAdapter<T extends Named> extends TypeAdapter<T> {
    private final Function<String, T> resolver;

    public NamedResolverTypeAdapter(Function<String, T> resolver) {
        this.resolver = Objects.requireNonNull(resolver, "resolver");
    }

    @Override
    public void write(JsonWriter out, T value) throws IOException {
        if (value == null)
            out.nullValue();
        else
            out.value(value.getName());
    }

    @Override
    public T read(JsonReader in) throws IOException {
        if (in.peek() == JsonToken.NULL) {
            in.nextNull();
            return null;
        }
        return resolver.apply(in.nextString());
    }
}
