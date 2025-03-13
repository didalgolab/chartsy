/* Copyright 2025 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.kernel.json;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import one.chartsy.core.json.JsonFormatter;
import org.openide.util.lookup.ServiceProvider;

/**
 * A JSON formatter implementation that utilizes the Jackson library for serialization
 * and deserialization of Java objects to and from JSON.
 * <p>
 * This formatter leverages Jackson's ObjectMapper for JSON processing.
 * </p>
 *
 * @author Mariusz Bernacki
 */
@ServiceProvider(service = JacksonJsonFormatter.class)
public class JacksonJsonFormatter implements JsonFormatter {

    public static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .addModule(new JavaTimeModule())
            .build();

    private final ObjectMapper objectMapper;

    public JacksonJsonFormatter() {
        this(OBJECT_MAPPER);
    }

    public JacksonJsonFormatter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public final ObjectMapper getObjectMapper() {
        return objectMapper;
    }

    @Override
    public String toJson(Object src) {
        try {
            return objectMapper.writeValueAsString(src);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public <T> T fromJson(String json, Class<T> resultType) {
        try {
            return objectMapper.readValue(json, resultType);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}
