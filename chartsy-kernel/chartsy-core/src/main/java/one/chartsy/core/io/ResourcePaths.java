/*
 * Copyright 2025 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.core.io;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/**
 * Utility methods for working with classpath resources.
 */
public final class ResourcePaths {

    private ResourcePaths() { }

    /**
     * Resolves the given resource name to a {@link Path}.
     * <p>If the resource is available as a regular file on the filesystem, its
     * path is returned directly. Otherwise, the resource is copied to a
     * temporary file which is returned.
     *
     * @param resourceName the resource name, optionally prefixed with '/'
     * @return a path to the resource contents
     * @throws IOException if the resource cannot be resolved
     */
    public static Path pathToResource(String resourceName) throws IOException {
        String name = resourceName.startsWith("/") ? resourceName.substring(1) : resourceName;
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        URL url = cl.getResource(name);
        if (url == null)
            throw new FileNotFoundException("Resource not found on classpath: " + name);

        try {
            URI uri = url.toURI();
            if ("file".equalsIgnoreCase(uri.getScheme()))
                return Paths.get(uri);
        } catch (Exception ignore) {
            // fall through to copy-from-stream
        }

        try (InputStream in = cl.getResourceAsStream(name)) {
            if (in == null)
                throw new FileNotFoundException("Resource stream is null: " + name);

            String fileName = Paths.get(name).getFileName().toString();
            Path tmp = Files.createTempFile("res-", "-" + fileName);
            Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
            tmp.toFile().deleteOnExit();
            return tmp;
        }
    }
}
