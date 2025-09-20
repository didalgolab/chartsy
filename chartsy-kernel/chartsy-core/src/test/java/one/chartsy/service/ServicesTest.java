/* Copyright 2025 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.service;

import one.chartsy.service.loader.ServiceNotFoundException;
import org.junit.jupiter.api.Test;
import org.openide.util.lookup.ServiceProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class ServicesTest {

    @Test
    void getShared_gives_shared_instance_when_requested_ExistingService() {
        var instance = Services.getShared(ExistingService.class);
        assertNotNull(instance);
        assertSame(instance, Services.getShared(ExistingService.class));
    }

    @Test
    void findShared_gives_shared_instance_when_requested_ExistingService() {
        var instance1 = Services.findShared(ExistingService.class);
        var instance2 = Services.findShared(ExistingService.class);

        assertThat(instance1).isPresent();
        assertThat(instance2).isPresent();
        assertSame(instance1.get(), instance2.get());
    }

    @Test
    void getShared_throws_ServiceNotFoundException_when_requested_NotExistingService() {
        interface NotExistingService { }
        assertThrows(ServiceNotFoundException.class, () -> Services.getShared(NotExistingService.class));
    }

    @Test
    void findShared_gives_EMPTY_Optional_when_requested_NotExistingService() {
        interface NotExistingService { }
        assertThat(Services.findShared(NotExistingService.class)).isEmpty();
    }

    @ServiceProvider(service = ExistingService.class)
    public static class ExistingService { }
}