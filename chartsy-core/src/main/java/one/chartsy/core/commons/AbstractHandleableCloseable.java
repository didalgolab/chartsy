/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.core.commons;

import one.chartsy.core.event.ListenerList;

/**
 * The basic implementation of the closeable resource.
 * 
 * @author Mariusz Bernacki
 *
 * @param <T> the type of the resource
 */
public abstract class AbstractHandleableCloseable<T extends AbstractHandleableCloseable<T>> implements HandleableCloseable<T> {
    /** The close handlers registry, created lazily when needed. */
    private ListenerList<CloseHandler<T>> closeHandlers;
    
    
    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Override
    public void addCloseHandler(CloseHandler<T> closeHandler) {
        if (closeHandlers == null) {
            closeHandlers = (ListenerList<CloseHandler<T>>) new ListenerList(CloseHandler.class);
        }
        closeHandlers.addListener(closeHandler);
    }
    
    @Override
    public void removeCloseHandler(CloseHandler<T> closeHandler) {
        if (closeHandlers != null)
            closeHandlers.removeListener(closeHandler);
    }
    
    @SuppressWarnings("unchecked")
    @Override
    public void close() {
        if (closeHandlers != null)
            closeHandlers.fire().handleClose((T) this);
    }
}
