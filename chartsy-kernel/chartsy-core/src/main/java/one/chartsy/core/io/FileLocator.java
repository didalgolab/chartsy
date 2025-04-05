/*
 * Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.core.io;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Comparator;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Utility class providing methods to locate the latest modified file
 * within a folder (e.g., the user's Downloads) based on a filename pattern.
 *
 * @author Mariusz Bernacki
 */
public class FileLocator {

    public static FileTime getLastModifiedTime(Path path) {
        try {
            return Files.getLastModifiedTime(path);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static Optional<Path> findLatestDownload(String fileNameRegex) {
        return findLatest(Path.of(System.getProperty("user.home"), "Downloads"), fileNameRegex);
    }

    public static Optional<Path> findLatest(Path folder, String fileNameRegex) {
        return findLatest(folder, Pattern.compile(fileNameRegex));
    }

    public static Optional<Path> findLatest(Path folder, Pattern fileNameRegex) {
        try (var files = Files.list(folder)) {
            return files
                    .filter(Files::isRegularFile)
                    .filter(f -> fileNameRegex.matcher(f.getFileName().toString()).matches())
                    .max(Comparator.comparing(FileLocator::getLastModifiedTime));
        } catch (IOException | UncheckedIOException e) {
            System.err.println("Error accessing files in " + folder + ": " + e);
            return Optional.empty();
        }
    }
}
