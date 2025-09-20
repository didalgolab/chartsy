/* Copyright 2024 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.messaging.common.handlers;

import one.chartsy.messaging.common.ShutdownRequest;

/**
 * An interface for handling {@link ShutdownRequest} messages. Implementations
 * of this interface define the behavior when a shutdown request is received.
 */
public interface ShutdownRequestHandler {

    /**
     * Handles a shutdown request. Implementations should define the actions to be taken
     * when a shutdown request is received.
     *
     * @param request the shutdown request to handle
     */
    void onShutdownRequest(ShutdownRequest request);
}