package one.chartsy;

import java.nio.file.Path;

public enum SystemFiles {

    DOWNLOADS_DIR {
        @Override
        public Path resolve() {
            String userHome = System.getProperty("user.home");
            return Path.of(userHome, "Downloads");
        }
    },

    PRIVATE_DIR {
        @Override
        public Path resolve() {
            Path moduleRoot = Path.of("").toAbsolutePath();
            Path repoRoot = moduleRoot.getParent() != null ? moduleRoot.getParent() : moduleRoot;
            return repoRoot.resolve("private");
        }
    };

    public Path resolve(String other) {
        return resolve().resolve(other);
    }

    public abstract Path resolve();
}
