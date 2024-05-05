/*
 * Copyright 2024 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.json;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import org.junit.jupiter.api.Test;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodySubscribers;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.Flow;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class JsonBodyPublisherTest {

    @Test
    public void testOfJsonObject() throws Exception {
        JsonObject jsonObject = Json.createObjectBuilder()
                .add("id", 1)
                .add("name", "Test")
                .build();
        HttpRequest.BodyPublisher bodyPublisher = JsonBodyPublisher.of(jsonObject);

        var subscriber = BodySubscribers.ofString(StandardCharsets.UTF_8);
        bodyPublisher.subscribe(new Flow.Subscriber<>() {
            private Flow.Subscription subscription;

            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                this.subscription = subscription;
                subscription.request(1);
            }

            @Override
            public void onNext(ByteBuffer item) {
                subscriber.onNext(List.of(item));
                subscription.request(1);
            }

            @Override
            public void onError(Throwable throwable) {
                subscriber.onError(throwable);
            }

            @Override
            public void onComplete() {
                subscriber.onComplete();
            }
        });

        String resultJson = subscriber.getBody().toCompletableFuture().get();
        assertEquals(jsonObject.toString(), resultJson);
    }
}