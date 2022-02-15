package one.chartsy.data.provider.file;

import one.chartsy.Cached;
import one.chartsy.core.ThrowingRunnable;
import one.chartsy.misc.ManagedReference;

import java.io.IOException;
import java.lang.ref.Cleaner;
import java.lang.ref.WeakReference;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

public class FileSystemCache {
    private static final FileSystemCache GLOBAL = new FileSystemCache();
    private final Map<Key, WeakReference<ManagedReference<FileSystem>>> cachedFileSystems = new HashMap<>();

    public static FileSystemCache getGlobal() {
        return GLOBAL;
    }


    public ManagedReference<FileSystem> getFileSystem(Path path, Map<String,?> env) throws IOException {
        Key key = new Key(path, (env == null || env.isEmpty())? new TreeMap<>(): new TreeMap<>(env));
        synchronized (cachedFileSystems) {
            ManagedReference<FileSystem> fileSystemRef;
            var weakRef = cachedFileSystems.get(key);
            if (weakRef == null || (fileSystemRef = weakRef.get()) == null) {
                freeWeakReferences(cachedFileSystems);

                var fileSystem = FileSystems.newFileSystem(key.path(), key.env());
                fileSystemRef = new ManagedReference<>(fileSystem);
                getCleaner().register(fileSystemRef, ThrowingRunnable.unchecked(fileSystem::close));
                cachedFileSystems.put(key, new WeakReference<>(fileSystemRef));
            }
            return fileSystemRef;
        }
    }

    private static void freeWeakReferences(Map<?, ? extends WeakReference<?>> map) {
        final int LIMIT = 1000;
        int count = 0;
        var iter = map.entrySet().iterator();
        while (iter.hasNext() && count++ < LIMIT)
            if (iter.next().getValue().get() == null)
                iter.remove();
    }

    protected Cleaner getCleaner() {
        return Cached.get(Cleaner.class, Cleaner::create);
    }

    private record Key(Path path, TreeMap<String,?> env) { }
}
