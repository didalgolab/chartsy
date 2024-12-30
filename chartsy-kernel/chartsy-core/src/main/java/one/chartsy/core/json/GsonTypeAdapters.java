/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.core.json;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

public abstract class GsonTypeAdapters {

    private static final class SupportedTypes {
        private static final List<Class<?>> ALL = Arrays.asList(
                Duration.class, LocalDate.class, LocalDateTime.class, LocalTime.class
        );
    }

    public static GsonBuilder installOn(GsonBuilder builder) {
        SupportedTypes.ALL.forEach(type -> builder.registerTypeAdapter(type, forType(type)));
        builder.registerTypeAdapterFactory(new OptionalTypeAdapterFactory());
        return builder;
    }

    @SuppressWarnings("unchecked")
    public static <T> TypeAdapter<T> forType(Class<T> type) {
        return (TypeAdapter<T>) switch (type.getName()) {
            case "java.time.Duration" -> new StringifyTypeAdapter<>(Duration::parse);
            case "java.time.LocalDate" -> new StringifyTypeAdapter<>(LocalDate::parse);
            case "java.time.LocalDateTime" -> new StringifyTypeAdapter<>(LocalDateTime::parse);
            case "java.time.LocalTime" -> new StringifyTypeAdapter<>(LocalTime::parse);
            default -> throw new IllegalArgumentException(type.getName());
        };
    }

    private static class StringifyTypeAdapter<T> extends TypeAdapter<T> {

        private final Function<String, T> fromString;

        protected StringifyTypeAdapter(Function<String, T> fromString) {
            this.fromString = fromString;
        }

        @Override
        public void write(JsonWriter out, T value) throws IOException {
            if (value == null) {
                out.nullValue();
            } else {
                out.value(value.toString());
            }
        }

        @Override
        public T read(JsonReader in) throws IOException {
            if (in.peek() == JsonToken.NULL) {
                in.nextNull();
                return null;
            }
            String value = in.nextString();
            try {
                return fromString.apply(value);
            } catch (Exception e) {
                throw new JsonParseException("Invalid value token: " + value + " at " + in.getPath(), e);
            }
        }
    }

    private static class OptionalTypeAdapterFactory implements TypeAdapterFactory {
        @Override
        public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
            if (type.getRawType() != Optional.class) {
                return null; // This factory only handles Optional types
            }

            var parameterizedType = (ParameterizedType) type.getType();
            var elementAdapter = gson.getAdapter(TypeToken.get(parameterizedType.getActualTypeArguments()[0]));
            @SuppressWarnings("unchecked")
            var typeAdapter = (TypeAdapter<T>) new OptionalTypeAdapter<>(elementAdapter);
            return typeAdapter;
        }
    }

    private static class OptionalTypeAdapter<E> extends TypeAdapter<Optional<E>> {
        private final TypeAdapter<E> elementAdapter;

        public OptionalTypeAdapter(TypeAdapter<E> elementAdapter) {
            this.elementAdapter = elementAdapter;
        }

        @Override
        public void write(JsonWriter out, Optional<E> value) throws IOException {
            if (value.isPresent()) {
                elementAdapter.write(out, value.get());
            } else {
                out.nullValue();
            }
        }

        @Override
        public Optional<E> read(JsonReader in) throws IOException {
            if (in.peek() == JsonToken.NULL) {
                in.nextNull();
                return Optional.empty();
            } else {
                return Optional.ofNullable(elementAdapter.read(in));
            }
        }
    }
}
