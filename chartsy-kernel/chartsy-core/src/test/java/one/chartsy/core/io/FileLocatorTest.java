/*
 * Copyright 2025 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.core.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class FileLocatorTest {

    @TempDir
    Path tempDir;

    @Test
    void findLatest_gives_none_when_matched_no_files() {
        Optional<Path> latest = FileLocator.findLatest(tempDir, ".*\\.txt");
        assertTrue(latest.isEmpty(), "Should match no files");
    }

    @Test
    void findLatest_gives_latest_file_when_matched_multiple() throws Exception {
        Path oldFile = Files.createFile(tempDir.resolve("old_file.txt"));
        Thread.sleep(10); // ensure different last-modified times
        Path newFile = Files.createFile(tempDir.resolve("new_file.txt"));

        Optional<Path> latest = FileLocator.findLatest(tempDir, ".*\\.txt");
        assertTrue(latest.isPresent(), "Should found a matching file");
        assertEquals(newFile, latest.get(), "Should return latest created file");
    }
}