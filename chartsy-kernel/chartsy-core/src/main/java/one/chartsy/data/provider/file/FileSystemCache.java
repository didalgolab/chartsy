/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.data.provider.file;

import one.chartsy.core.ResourceHandle;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

public class FileSystemCache {
    private static final FileSystemCache GLOBAL = new FileSystemCache();
    private final Map<Key, WeakReference<ResourceHandle<FileSystem>>> cachedFileSystems = new HashMap<>();

    public static FileSystemCache getGlobal() {
        return GLOBAL;
    }


    public ResourceHandle<FileSystem> getFileSystem(Path path, Map<String,?> env) throws IOException {
        Key key = new Key(path, (env == null || env.isEmpty())? new TreeMap<>(): new TreeMap<>(env));
        synchronized (cachedFileSystems) {
            ResourceHandle<FileSystem> fileSystemRef;
            var weakRef = cachedFileSystems.get(key);
            if (weakRef == null || (fileSystemRef = weakRef.get()) == null) {
                freeWeakReferences(cachedFileSystems);

                var fileSystem = FileSystems.newFileSystem(key.path(), key.env());
                fileSystemRef = ResourceHandle.of(fileSystem, false);
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

    private record Key(Path path, TreeMap<String,?> env) { }
}
