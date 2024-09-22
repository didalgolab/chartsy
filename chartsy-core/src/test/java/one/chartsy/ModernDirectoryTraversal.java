package one.chartsy;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;

public class ModernDirectoryTraversal {

    public static void main(String[] args) {
        // Get the current working directory
        Path currentDirectory = Paths.get(System.getProperty("user.dir")).resolve("../chartsy");
        System.out.println(currentDirectory);

        // Define the subpath we are looking for
        String targetSubpath = "src/main/java";

        try {
            // Traverse and print directories containing the target subpath
            Files.walkFileTree(currentDirectory, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    // Convert to relative path and check if it contains the subpath
                    Path relativePath = currentDirectory.relativize(dir);
                    if (relativePath.toString().replace('\\', '/').contains(targetSubpath)) {
                        System.out.println(relativePath);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
