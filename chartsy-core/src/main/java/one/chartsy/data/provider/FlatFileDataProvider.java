package one.chartsy.data.provider;

import one.chartsy.AssetTypes;
import one.chartsy.Candle;
import one.chartsy.SymbolGroup;
import one.chartsy.SymbolIdentity;
import one.chartsy.data.DataQuery;
import one.chartsy.data.SimpleCandle;
import one.chartsy.data.UnsupportedDataQueryException;
import one.chartsy.data.batch.Batch;
import one.chartsy.data.batch.Batchers;
import one.chartsy.data.batch.SimpleBatch;
import one.chartsy.data.provider.file.FlatFileFormat;
import one.chartsy.data.provider.file.FlatFileItemReader;
import one.chartsy.naming.SymbolIdentifier;
import one.chartsy.time.Chronological;
import org.openide.util.Lookup;
import org.openide.util.lookup.Lookups;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.file.LineMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

public class FlatFileDataProvider implements DataProvider, SymbolListAccessor, HierarchicalConfiguration {
    private final Lookup lookup = Lookups.singleton(this);
    private final FileSystem fileSystem;
    private final FlatFileFormat fileFormat;
    private final ExecutionContext context;

    public FlatFileDataProvider(FlatFileFormat fileFormat, Path archiveFile) throws IOException {
        this(fileFormat, FileSystems.newFileSystem(archiveFile));
    }

    public FlatFileDataProvider(FlatFileFormat fileFormat, FileSystem fileSystem) throws IOException {
        this.fileFormat = Objects.requireNonNull(fileFormat, "fileFormat");
        this.fileSystem = fileSystem;
        this.context = new ExecutionContext();

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

    @Override
    @SuppressWarnings("unchecked")
    public <T extends Chronological> Batch<T> queryInBatches(Class<T> type, DataQuery<T> request) {
        if (type == Candle.class || type == SimpleCandle.class)
            return (Batch<T>) queryForCandles((DataQuery<Candle>) request);
        else
            throw new UnsupportedDataQueryException(request, String.format("DataType `%s` not supported", type.getSimpleName()));
    }

    public <T extends Candle> Batch<T> queryForCandles(DataQuery<T> request) {
        SymbolIdentifier identifier = new SymbolIdentifier(request.resource().symbol());
        Path file = getSymbolFiles().get(identifier);
        if (file == null)
            throw new IllegalArgumentException(String.format("Symbol %s not found", identifier));

        FlatFileItemReader<T> itemReader = new FlatFileItemReader<>();
        itemReader.setLineMapper((LineMapper<T>) fileFormat.getLineMapper().createLineMapper(context));
        itemReader.setLinesToSkip(fileFormat.getSkipFirstLines());
        itemReader.setInputStreamSource(() -> Files.newInputStream(file));

        try {
            itemReader.open();
            return new SimpleBatch<>(new Batchers.StandaloneQueryBatcher<>(request), Chronological.Order.CHRONOLOGICAL, 0L, itemReader.readAll());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            itemReader.close();
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

    private final Map<SymbolIdentifier, Path> symbolFiles = Collections.synchronizedMap(new HashMap<>());

    public Map<SymbolIdentifier, Path> getSymbolFiles() {
        if (symbolFiles.isEmpty()) {
            synchronized (symbolFiles) {
                var visitor = new SymbolFileVisitor();
                try {
                    for (Path rootDir : fileSystem.getRootDirectories())
                        Files.walkFileTree(rootDir, visitor);

                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
        }
        return symbolFiles;
    }

    protected String asSymbolName(Path fileName) {
        String name = fileName.toString();
        int lastDot = name.lastIndexOf('.');
        return (lastDot > 0)? name.substring(0, lastDot): name;
    }

    protected SymbolIdentity asIdentifier(Path path) {
        return new SymbolIdentifier(asSymbolName(path.getFileName()), AssetTypes.GENERIC);
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

    private class SymbolFileVisitor extends SimpleFileVisitor<Path> {
        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
            SymbolIdentity symbol = asIdentifier(file);
            symbolFiles.put(new SymbolIdentifier(symbol), file);
            return super.visitFile(file, attrs);
        }
    }
}
