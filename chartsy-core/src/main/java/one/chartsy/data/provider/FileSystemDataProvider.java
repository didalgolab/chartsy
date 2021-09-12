package one.chartsy.data.provider;

import one.chartsy.SymbolGroup;
import one.chartsy.SymbolIdentity;
import org.openide.util.Lookup;
import org.openide.util.lookup.Lookups;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

public class FileSystemDataProvider implements DataProvider, SymbolListAccessor, HierarchicalConfiguration {
    private final Lookup lookup = Lookups.singleton(this);
    private final FileSystem fileSystem;
    //private final FlatFileFormat fileFormat;

    public FileSystemDataProvider(DataProviderConfiguration config) throws IOException {
        this(FileSystems.newFileSystem(config.getFileSystemPath(), Map.of()), config);
    }

    private FileSystemDataProvider(FileSystem fileSystem, DataProviderConfiguration config) throws IOException {
        this.fileSystem = fileSystem;
        //fileFormat = Objects.requireNonNull(config.getFileFormat(), "fileFormat");

        FileTreeVisitor visitor = new FileTreeVisitor();
        for (Path rootDir : fileSystem.getRootDirectories())
            Files.walkFileTree(rootDir, visitor);
    }

    @Override
    public Lookup getLookup() {
        return lookup;
    }

    @Override
    public List<SymbolGroup> getRootGroups() {
        return asGroups(getFileSystem().getRootDirectories());
    }

    @Override
    public List<SymbolGroup> getSubGroups(SymbolGroup parent) {
        try {
            return asGroups(Files.newDirectoryStream(asPath(parent), Files::isDirectory));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public String getSimpleName(SymbolGroup group) {
        return Paths.get(group.name()).getFileName().toString();
    }

    @Override
    public List<SymbolIdentity> getSymbols(SymbolGroup group) {
        try {
            return asIdentifiers(Files.newDirectoryStream(asPath(group), Files::isRegularFile));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public FileSystem getFileSystem() {
        return fileSystem;
    }

    @Override
    public Collection<SymbolGroup> getAvailableGroups() {
        return immutableGroups.values();
    }

    @Override
    public List<SymbolIdentity> getSymbolList(SymbolGroup group) {
        return getSymbols(group);
    }

    private final Map<String, SymbolGroup> availableGroups = new TreeMap<>();
    private final Map<String, SymbolGroup> immutableGroups = Collections.unmodifiableMap(availableGroups);

    protected SymbolIdentity asIdentifier(Path path) {
        String name = path.getFileName().toString();
        int extpos = name.lastIndexOf('.');
        if (extpos > 0)
            name = name.substring(0, extpos);
        return SymbolIdentity.of(name);
    }

    protected List<SymbolIdentity> asIdentifiers(Iterable<Path> paths) {
        List<SymbolIdentity> symbols = new ArrayList<>();
        for (Path dir : paths)
            symbols.add(asIdentifier(dir));
        symbols.sort(SymbolIdentity.comparator());
        return symbols;
    }

    protected List<SymbolGroup> asGroups(Iterable<Path> paths) {
        List<SymbolGroup> groups = new ArrayList<>();
        for (Path dir : paths)
            groups.add(asGroup(dir));
        groups.sort(Comparator.comparing(SymbolGroup::name));
        return groups;
    }

    protected SymbolGroup asGroup(Path dir) {
        return new SymbolGroup(dir.toString());
    }

    protected Path asPath(SymbolGroup group) {
        String pathName = group.isBase()? "/": group.name();
        return getFileSystem().getPath(pathName);
    }

    private class FileTreeVisitor extends SimpleFileVisitor<Path> {
        @Override
        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
            SymbolGroup group = asGroup(dir);
            availableGroups.put(group.name(), group);
            return super.preVisitDirectory(dir, attrs);
        }
    }
}
