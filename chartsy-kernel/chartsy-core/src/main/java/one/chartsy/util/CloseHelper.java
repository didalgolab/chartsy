/* Copyright 2024 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.util;

/**
 * Utility class for safely closing AutoCloseable resources.
 * This class provides methods to close resources quietly, suppressing any exceptions that may occur during the closing process.
 * <p>
 * Usage example:
 * <pre>
 * {@code
 * try (InputStream is = new FileInputStream("file.txt");
 *      OutputStream os = new FileOutputStream("output.txt")) {
 *     // Use the streams
 * } catch (IOException e) {
 *     // Handle exception
 * } finally {
 *     CloseHelper.closeQuietly(is, os);
 * }
 * }
 * </pre>
 */
public final class CloseHelper {

    private CloseHelper() {
        // Private constructor to prevent instantiation
    }

    /**
     * Closes one or more {@code AutoCloseable} resources quietly, suppressing any exceptions.
     *
     * @param resources the {@code AutoCloseable} resources to close
     */
    public static void closeQuietly(AutoCloseable... resources) {
        if (resources != null) {
            for (AutoCloseable resource : resources) {
                closeQuietly(resource);
            }
        }
    }

    /**
     * Closes a single {@code AutoCloseable} resource quietly, suppressing any exceptions.
     *
     * @param resource the {@code AutoCloseable} resource to close
     */
    public static void closeQuietly(AutoCloseable resource) {
        if (resource != null) {
            try {
                resource.close();
            } catch (Exception e) {
                // TODO: log exception
            }
        }
    }
}