/* Copyright 2024 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.messaging.common.handlers;

import one.chartsy.messaging.common.ShutdownResponse;

/**
 * An interface for handling {@link ShutdownResponse} messages. Implementations
 * of this interface define the behavior when a shutdown response is received.
 */
public interface ShutdownResponseHandler {

    /**
     * Handles a shutdown response. Implementations should define the actions to be taken
     * when a shutdown response is received.
     *
     * @param response the shutdown response to handle
     */
    void onShutdownResponse(ShutdownResponse response);
}