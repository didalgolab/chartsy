/* Copyright 2024 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.data.stream;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * A message buffer that uses an {@link ArrayBlockingQueue} to store messages.
 * This implementation also serves as a message channel for adding new messages
 * to the buffer. The buffer is managed and can be opened and closed as needed.
 *
 * @param <T> the type of messages this buffer handles
 */
public class ArrayBlockingQueueMessageBuffer<T extends Message>
        implements MessageBuffer, MessageChannel<T> {

    private final BlockingQueue<T> queue;
    private volatile boolean open;

    /**
     * Constructs an {@link ArrayBlockingQueueMessageBuffer} with the specified capacity.
     *
     * @param capacity the maximum number of messages the buffer can hold
     */
    public ArrayBlockingQueueMessageBuffer(int capacity) {
        this.queue = new ArrayBlockingQueue<>(capacity);
        this.open = true;
    }

    /**
     * Opens the message buffer, allowing it to accept new messages and process
     * existing ones. This method should be called before the buffer is used.
     */
    public void open() {
        this.open = true;
    }

    /**
     * @return {@code true} if the message buffer is opened, {@code false} otherwise
     */
    public boolean isOpen() {
        return this.open;
    }

    /**
     * Closes the message buffer, preventing it from accepting new messages.
     * This method also clears any messages that remain in the buffer.
     */
    @Override
    public void close() {
        this.open = false;
        queue.clear();
    }

    /**
     * Sends a message to the buffer. This method adds the message to the underlying
     * queue if the buffer is open. If the buffer is full or closed, the message will
     * be rejected.
     *
     * @param msg the message to be added to the buffer
     */
    @Override
    public void send(T msg) {
        if (!isOpen())
            throw new IllegalStateException("Message buffer is closed");
        if (!queue.offer(msg))
            throw new IllegalStateException("Message buffer is full");
    }

    /**
     * Fetches and processes messages from the buffer, invoking the provided
     * {@link MessageHandler} for each message. The number of messages processed
     * is limited by the {@code messageLimit} parameter.
     *
     * @param handler      the {@link MessageHandler} to process each message
     * @param messageLimit the maximum number of messages to process in this call
     * @return the actual number of messages that were processed
     */
    @Override
    public int read(MessageHandler handler, int messageLimit) {
        int count = 0;
        while (count < messageLimit) {
            T message = queue.poll();
            if (message == null) {
                break;
            }
            handler.handleMessage(message);
            count++;
        }
        return count;
    }
}
