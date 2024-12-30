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

public class Paths {

    public static FileTime lastModifiedFileTime(Path path) {
        try {
            return Files.getLastModifiedTime(path);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static Optional<Path> latest(Path folder, Pattern filenameRegex) throws IOException {
        try (var files = Files.list(folder)) {
            return files.filter(Files::isRegularFile)
                    .filter(f -> filenameRegex.matcher(f.getFileName().toString()).matches())
                    .max(Comparator.comparing(Paths::lastModifiedFileTime));
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }

    public static void main(String[] args) throws IOException {
        System.out.println(latest(Path.of("C:/Users/Mariusz/Downloads"), Pattern.compile("d_pl_txt( ?\\(\\d+\\))?\\.zip")));
    }
}
