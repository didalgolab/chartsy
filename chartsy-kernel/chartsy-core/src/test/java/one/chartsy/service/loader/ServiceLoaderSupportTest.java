/* Copyright 2025 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.service.loader;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class ServiceLoaderSupportTest {

    ServiceLoaderSupport support = ServiceLoaderSupport.INSTANCE;

    @Test
    void loadService_gives_shared_instance() {
        var instance1 = support.findService(ServiceLoaderSupport.class);
        var instance2 = support.findService(ServiceLoaderSupport.class);

        assertThat(instance1).isPresent();
        assertThat(instance2).isPresent();
        assertSame(instance1.get(), instance2.get());
    }

    @Test
    void loadService_throws_when_instance_not_found() {
        interface NotExisting { }
        assertEquals(Optional.empty(), support.findService(NotExisting.class));
    }
}