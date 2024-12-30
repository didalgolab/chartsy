/*
 * Copyright 2024 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.json;

import jakarta.json.JsonObject;

import java.net.http.HttpRequest;

public class JsonBodyPublisher {

    public static HttpRequest.BodyPublisher of(JsonObject obj) {
        return HttpRequest.BodyPublishers.ofString(obj.toString());
    }
}
