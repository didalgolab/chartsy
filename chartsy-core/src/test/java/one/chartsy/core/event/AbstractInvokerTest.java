/* Copyright 2024 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.core.event;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class AbstractInvokerTest {

    static class TestInvoker extends AbstractInvoker {
        public TestInvoker(Object primaryService) {
            super(primaryService);
        }

        public TestInvoker(Class<?> primaryServiceType) {
            super(primaryServiceType);
        }

        @Override
        protected Set<Class<?>> findExposedServiceHandlers(Class<?> type, Set<Class<?>> allowlist) {
            return super.findExposedServiceHandlers(type, allowlist);
        }
    }

    interface Service1 {}
    interface Service2 {}
    interface Service3 extends Service2 {}

    static class TestService implements Service1, Service3 {}

    @Test
    void testFindExposedServiceHandlers() {
        TestInvoker invoker = new TestInvoker(TestService.class);
        Set<Class<?>> handlers = invoker.findExposedServiceHandlers(TestService.class, null);

        assertEquals(3, handlers.size());
        assertTrue(handlers.contains(Service1.class));
        assertTrue(handlers.contains(Service2.class));
        assertTrue(handlers.contains(Service3.class));
    }

    @Test
    void testFindExposedServiceHandlersWithAllowlist() {
        TestInvoker invoker = new TestInvoker(TestService.class);
        Set<Class<?>> allowlist = new HashSet<>();
        allowlist.add(Service1.class);
        allowlist.add(Service2.class);
        Set<Class<?>> handlers = invoker.findExposedServiceHandlers(TestService.class, allowlist);

        assertEquals(2, handlers.size());
        assertTrue(handlers.contains(Service1.class));
        assertTrue(handlers.contains(Service2.class));
        assertFalse(handlers.contains(Service3.class));
    }

    @Test
    void testCreatePrimaryHandlers() {
        TestInvoker invoker = new TestInvoker(TestService.class);
        var handlers = invoker.createPrimaryHandlers(TestService.class);

        assertEquals(3, handlers.size());
        assertTrue(handlers.containsKey(Service1.class));
        assertTrue(handlers.containsKey(Service2.class));
        assertTrue(handlers.containsKey(Service3.class));

        assertTrue(handlers.get(Service1.class) instanceof ListenerList);
        assertTrue(handlers.get(Service2.class) instanceof ListenerList);
        assertTrue(handlers.get(Service3.class) instanceof ListenerList);
    }

    @Test
    void testGetSupportedHandlers() {
        TestInvoker invoker = new TestInvoker(TestService.class);
        Set<Class<?>> handlers = invoker.getSupportedHandlers();

        assertEquals(3, handlers.size());
        assertTrue(handlers.contains(Service1.class));
        assertTrue(handlers.contains(Service2.class));
        assertTrue(handlers.contains(Service3.class));
    }

    @Test
    void testAddAndRemoveService() {
        TestInvoker invoker = new TestInvoker(TestService.class);
        TestService service = new TestService();
        invoker.addService(service);

        var service1Listeners = invoker.getListenerList(Service1.class).getListeners();
        var service2Listeners = invoker.getListenerList(Service2.class).getListeners();
        var service3Listeners = invoker.getListenerList(Service3.class).getListeners();

        assertEquals(1, service1Listeners.length);
        assertEquals(service, service1Listeners[0]);
        assertEquals(1, service2Listeners.length);
        assertEquals(service, service2Listeners[0]);
        assertEquals(1, service3Listeners.length);
        assertEquals(service, service3Listeners[0]);

        invoker.removeService(service);

        assertEquals(0, invoker.getListenerList(Service1.class).getListenerCount());
        assertEquals(0, invoker.getListenerList(Service2.class).getListenerCount());
        assertEquals(0, invoker.getListenerList(Service3.class).getListenerCount());
    }

    @Test
    void addService_does_not_create_listener_for_interface_unsupported_by_primary_service() {
        var invoker = new TestInvoker(TestService.class); // Using TestService, which doesn't implement Service4

        interface Service4 {}
        class TestServiceWithExtraInterface implements Service1, Service3, Service4 {}
        var service = new TestServiceWithExtraInterface();
        invoker.addService(service);

        var service1Listeners = invoker.getListenerList(Service1.class).getListeners();
        var service2Listeners = invoker.getListenerList(Service2.class).getListeners();
        var service3Listeners = invoker.getListenerList(Service3.class).getListeners();

        // Verify that the service is added to the correct listeners
        assertEquals(1, service1Listeners.length);
        assertEquals(service, service1Listeners[0]);
        assertEquals(1, service2Listeners.length);
        assertEquals(service, service2Listeners[0]);
        assertEquals(1, service3Listeners.length);
        assertEquals(service, service3Listeners[0]);

        // Verify that no listener is created for Service4
        assertNull(invoker.getListenerList(Service4.class));
    }
}