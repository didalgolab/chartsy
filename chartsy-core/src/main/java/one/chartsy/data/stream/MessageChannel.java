/* Copyright 2024 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.data.stream;

import one.chartsy.Manageable;

/**
 *  A message sink that consumes messages.
 */
public interface MessageChannel<T> extends Manageable {

    /**
     *  This method is invoked to send a message to the channel.
     * 
     *  @param msg  A temporary buffer with the message.
     *              By convention, the message is only valid for the duration 
     *              of this call.
     */
    void send(T msg);
}