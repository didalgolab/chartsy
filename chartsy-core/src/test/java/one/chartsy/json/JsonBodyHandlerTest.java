/*
 * Copyright 2024 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.json;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonStructure;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.concurrent.SubmissionPublisher;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class JsonBodyHandlerTest {

    @Test
    public void testAsJsonStructure() throws Exception {
        JsonObject expected = Json.createObjectBuilder()
                .add("id", 2)
                .add("name", "Test2")
                .build();

        // Create a BodyHandler for TestData
        HttpResponse.BodyHandler<Supplier<JsonStructure>> handler = JsonBodyHandler.INSTANCE;

        // Create a SubmissionPublisher to simulate the body publisher of the HttpClient
        SubmissionPublisher<List<ByteBuffer>> publisher = new SubmissionPublisher<>();

        // Subscribe the BodyHandler to the SubmissionPublisher
        HttpResponse.ResponseInfo responseInfo = new HttpResponse.ResponseInfo() {
            @Override
            public int statusCode() {
                return 200;
            }

            @Override
            public HttpHeaders headers() {
                return HttpHeaders.of(Map.of(), (s1, s2) -> true);
            }

            @Override
            public HttpClient.Version version() {
                return HttpClient.Version.HTTP_1_1;
            }
        };

        HttpResponse.BodySubscriber<Supplier<JsonStructure>> bodySubscriber = handler.apply(responseInfo);
        publisher.subscribe(bodySubscriber);

        // Simulate receiving the JSON data
        publisher.submit(List.of(ByteBuffer.wrap(expected.toString().getBytes())));
        publisher.close();

        // Get the deserialized object
        JsonStructure result = bodySubscriber.getBody().toCompletableFuture().get().get();

        // Assertions
        assertEquals(expected, result);
    }
}