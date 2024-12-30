/* Copyright 2024 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.core.event;

import java.util.IdentityHashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public abstract class AbstractInvoker {

    private final Map<Class<?>, ListenerList<?>> listenerLists;

    protected AbstractInvoker(Object primaryService) {
        this(primaryService.getClass());
    }

    protected AbstractInvoker(Class<?> primaryServiceType) {
        this.listenerLists = createPrimaryHandlers(primaryServiceType);
    }

    public Set<Class<?>> getSupportedHandlers() {
        return listenerLists.keySet();
    }

    @SuppressWarnings("unchecked")
    protected <T> ListenerList<T> getListenerList(Class<T> type) {
        return (ListenerList<T>) listenerLists.get(type);
    }

    @SuppressWarnings("unchecked")
    protected <T> T getHandler(Class<T> type) {
        var listenerList = getListenerList(type);
        if (listenerList == null)
            throw new IllegalArgumentException("Unsupported handler type " + type);

        return listenerList.fire();
    }

    @SuppressWarnings("unchecked")
    public void addService(Object serviceInstance) {
        Set<Class<?>> handlers = findExposedServiceHandlers(serviceInstance.getClass(), getSupportedHandlers());
        handlers.forEach(iface -> ((ListenerList<Object>) getListenerList(iface)).addListener(serviceInstance));
    }

    @SuppressWarnings("unchecked")
    public void removeService(Object serviceInstance) {
        listenerLists.forEach((type, listenerList) -> {
            ((ListenerList<Object>) listenerList).removeListener(serviceInstance);
        });
    }

    protected Map<Class<?>, ListenerList<?>> createPrimaryHandlers(Class<?> type) {
        Set<Class<?>> handlers = findExposedServiceHandlers(type, null);

        return handlers.stream()
                .collect(Collectors.toMap(
                        Function.identity(),
                        ListenerList::of,
                        (v1, v2) -> v1, // In case of duplicate keys (shouldn't happen with interfaces)
                        IdentityHashMap::new
                ));
    }

    protected Set<Class<?>> findExposedServiceHandlers(Class<?> type, Set<Class<?>> allowlist) {
        Map<Class<?>, Boolean> interfaces = new IdentityHashMap<>();
        Queue<Class<?>> worklist = new LinkedList<>();
        worklist.add(type);

        while (!worklist.isEmpty()) {
            Class<?> current = worklist.poll();
            for (Class<?> iface : current.getInterfaces()) {
                if (null == interfaces.put(iface, isHandlerAllowed(iface, allowlist))) {
                    worklist.add(iface);
                }
            }
            if (current.getSuperclass() != null) {
                worklist.add(current.getSuperclass());
            }
        }

        interfaces.entrySet().removeIf(e -> !e.getValue());
        return interfaces.keySet();
    }

    protected static boolean isHandlerAllowed(Class<?> candidate, Set<Class<?>> allowlist) {
        return allowlist == null || allowlist.contains(candidate);
    }
}