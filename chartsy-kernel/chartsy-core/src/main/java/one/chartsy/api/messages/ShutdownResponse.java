/* Copyright 2024 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.api.messages;

import one.chartsy.data.stream.Message;

/**
 * A shutdown response message.
 */
public record ShutdownResponse(String serviceName, long time) implements Message {
}