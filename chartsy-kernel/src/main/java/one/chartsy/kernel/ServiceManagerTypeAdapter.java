/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.kernel;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import one.chartsy.core.Named;

import java.io.IOException;

public class ServiceManagerTypeAdapter<T extends Named> extends TypeAdapter<T> {

    private final ServiceManager<T> services;

    public ServiceManagerTypeAdapter(ServiceManager<T> services) {
        this.services = services;
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
        return services.get(in.nextString());
    }
}
