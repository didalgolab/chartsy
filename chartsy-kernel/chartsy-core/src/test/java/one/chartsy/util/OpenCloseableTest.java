/* Copyright 2025 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.util;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class OpenCloseableTest {

    @Test
    void calls_close_when_open_fails_and_used_try_with_resources() {
        class MyOpenFailedException extends RuntimeException { }
        class MyCloseFailedException extends RuntimeException { }
        var wasClosed = new AtomicBoolean();

        var thrown = assertThrows(MyOpenFailedException.class, () -> {
            try (OpenCloseable oc = new OpenCloseable() {
                @Override
                public void open() {
                    throw new MyOpenFailedException();
                }

                @Override
                public void close() {
                    wasClosed.set(true);
                    throw new MyCloseFailedException();
                }
            }) {
                oc.open();
            }
        });
        assertThat(wasClosed).isTrue();
        assertThat(thrown).isInstanceOf(MyOpenFailedException.class);
        assertThat(thrown.getSuppressed()).hasSize(1);
        assertThat(thrown.getSuppressed()[0]).isInstanceOf(MyCloseFailedException.class);
    }
}