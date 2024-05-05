/*
 * Copyright 2024 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.json;

import jakarta.json.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.http.HttpResponse;
import java.util.function.Function;
import java.util.function.Supplier;

public class JsonBodyHandler implements HttpResponse.BodyHandler<Supplier<JsonStructure>> {

    public static final JsonBodyHandler INSTANCE = new JsonBodyHandler();

    private JsonBodyHandler() { }

    @Override
    public HttpResponse.BodySubscriber<Supplier<JsonStructure>> apply(HttpResponse.ResponseInfo responseInfo) {
        return asJsonStructure();
    }

    public static HttpResponse.BodySubscriber<Supplier<JsonStructure>> asJsonStructure() {
        return HttpResponse.BodySubscribers.mapping(
                HttpResponse.BodySubscribers.ofInputStream(),
                in -> toSupplier(in, JsonReader::read));
    }

    public static HttpResponse.BodySubscriber<Supplier<JsonObject>> asJsonObject() {
        return HttpResponse.BodySubscribers.mapping(
                HttpResponse.BodySubscribers.ofInputStream(),
                in -> toSupplier(in, JsonReader::readObject));
    }

    public static HttpResponse.BodySubscriber<Supplier<JsonArray>> asJsonArray() {
        return HttpResponse.BodySubscribers.mapping(
                HttpResponse.BodySubscribers.ofInputStream(),
                in -> toSupplier(in, JsonReader::readArray));
    }

    public static <T> Supplier<T> toSupplier(InputStream in, Function<JsonReader, T> readerFunction) {
        return () -> {
            try (InputStream stream = in) {
                return readerFunction.apply(Json.createReader(stream));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        };
    }
}