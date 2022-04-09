package one.chartsy.ide.engine.launch;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class ConventionalJavaProjectClassLoader extends ClassLoader {
    public static final Path DEFAULT_BINARY_ROOT = Path.of("build/classes/java/main");
    private final Path projectDirectory;
    private final Path classesRoot;

    public ConventionalJavaProjectClassLoader(ClassLoader parent, Path projectDirectory) {
        this(parent, projectDirectory, DEFAULT_BINARY_ROOT);
    }

    public ConventionalJavaProjectClassLoader(ClassLoader parent, Path projectDirectory, Path classesRoot) {
        super(parent);
        this.projectDirectory = projectDirectory;
        this.classesRoot = classesRoot;
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        byte[] data = loadClassData(name);
        return defineClass(name, data, 0, data.length);
    }

    private byte[] loadClassData(String name) throws ClassNotFoundException {
        Path resource = projectDirectory
                .resolve(classesRoot)
                .resolve(name.replace('.', '/') + ".class");

        try {
            if (Files.exists(resource))
                return Files.readAllBytes(resource);

            try (InputStream in = getResourceAsStream(resource.toString())) {
                if (in == null)
                    throw new ClassNotFoundException(name, new IOException("Resource not found: " + resource));
                return in.readAllBytes();
            }
        } catch (IOException e) {
            throw new ClassNotFoundException(name, e);
        }
    }
}
