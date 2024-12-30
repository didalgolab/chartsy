/* Copyright 2024 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy;

/**
 * Represents a resource, a component, or a service that can be managed, typically
 * involving operations to {@link #open()} and {@link #close()} the resource. This
 * interface extends {@link AutoCloseable}, ensuring that the resource can be closed
 * automatically in a try-with-resources statement.
 */
public interface Manageable extends AutoCloseable {

    /**
     * Opens the resource, preparing it for use.
     */
    void open();

    /**
     * Closes the resource, releasing any resources it holds.
     */
    @Override
    void close();
}