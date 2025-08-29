/*
 * Copyright 2025 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.core.io;

import org.junit.jupiter.api.Test;

import java.io.FileNotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ResourcePathsTest {

    @Test
    void pathToResource_resolves_existing_resource() throws Exception {
        Path path = ResourcePaths.pathToResource("test-resource.txt");
        assertTrue(Files.exists(path));
        assertEquals("test resource content", Files.readString(path).trim());
    }

    @Test
    void pathToResource_handles_leading_slash() throws Exception {
        Path path = ResourcePaths.pathToResource("/test-resource.txt");
        assertTrue(Files.exists(path));
    }

    @Test
    void pathToResource_throws_when_missing() {
        assertThrows(FileNotFoundException.class, () -> ResourcePaths.pathToResource("missing-resource.txt"));
    }
}
