/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.data.provider;

import one.chartsy.*;
import one.chartsy.api.messages.ImmutableBarEvent;
import one.chartsy.context.ExecutionContext;
import one.chartsy.core.ResourceHandle;
import one.chartsy.data.DataQuery;
import one.chartsy.data.SimpleCandle;
import one.chartsy.data.UnsupportedDataQueryException;
import one.chartsy.data.provider.file.*;
import one.chartsy.financial.IdentityType;
import one.chartsy.financial.InstrumentType;
import one.chartsy.financial.SymbolIdentifier;
import one.chartsy.messaging.MarketEvent;
import one.chartsy.messaging.MarketMessageSource;
import one.chartsy.time.Chronological;
import one.chartsy.util.CloseHelper;
import org.apache.commons.lang3.StringUtils;
import org.openide.util.Lookup;
import org.openide.util.lookup.Lookups;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.function.Predicate;

public class FlatFileDataProvider extends AbstractDataProvider implements SymbolListAccessor, SymbolProposalProvider, HierarchicalConfiguration {
    private final Lookup lookup = Lookups.singleton(this);
    private final FlatFileFormat fileFormat;
    private final ResourceHandle<FileSystem> fileSystem;
    private final Iterable<Path> baseDirectories;

    public FlatFileDataProvider(FlatFileFormat fileFormat, Path archiveFile) throws IOException {
        this(fileFormat, FileSystemCache.getGlobal().getFileSystem(archiveFile, Map.of()), fileName(archiveFile));
    }

    public FlatFileDataProvider(FlatFileFormat fileFormat, FileSystem fileSystem, String name) throws IOException {
        this(fileFormat, ResourceHandle.of(fileSystem), name);
    }

    public FlatFileDataProvider(FlatFileFormat fileFormat, ResourceHandle<FileSystem> fileSystem, String name) throws IOException {
        this(fileFormat, fileSystem, name, fileSystem.get().getRootDirectories());
    }

    public FlatFileDataProvider(FlatFileFormat fileFormat, ResourceHandle<FileSystem> fileSystem, String name, Iterable<Path> baseDirectories) throws IOException {
        super(Objects.requireNonNull(name, "name"));
        this.fileFormat = Objects.requireNonNull(fileFormat, "fileFormat");
        this.fileSystem = Objects.requireNonNull(fileSystem, "fileSystem");
        this.baseDirectories = Objects.requireNonNull(baseDirectories, "baseDirectories");
    }

    protected static boolean isCloseable(ResourceHandle<FileSystem> ref) {
        return ref.isCloseable() && ref.get() != FileSystems.getDefault();
    }

    private static String fileName(Path file) {
        return file.getFileName().toString();
    }

    @Override
    public Lookup getLookup() {
        return lookup;
    }

    @Override
    public List<SymbolGroup> getRootGroups() {
        return asGroups(getBaseDirectories());
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
    public List<SymbolIdentity> listSymbols(SymbolGroup group) {
        try {
            return asIdentifiers(Files.newDirectoryStream(asPath(group), Files::isRegularFile));
        } catch (IOException e) {
            throw new DataProviderException("I/O error occurred", e);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends Chronological> Flux<T> query(Class<T> type, DataQuery<T> request) {
        if (type == Candle.class || type == SimpleCandle.class)
            return (Flux<T>) queryForCandles((DataQuery<Candle>) request);
        else
            throw new UnsupportedDataQueryException(request, String.format("DataType `%s` not supported", type.getSimpleName()));
    }

    public <T extends Candle> Flux<T> queryForCandles(DataQuery<T> request) {
        SymbolIdentifier identifier = new SymbolIdentifier(request.resource().symbol());
        Path file = getFileTreeMetadata().availableSymbols.get(identifier);
        if (file == null)
            throw new DataProviderException(String.format("Symbol '%s' not found", identifier));

        ExecutionContext context = new ExecutionContext();
        context.put("TimeFrame", request.resource().timeFrame());

        FlatFileItemReader<T> itemReader = new FlatFileItemReader<>();
        itemReader.setLineMapper((LineMapper<T>) fileFormat.getLineMapper().createLineMapper(context));
        itemReader.setLinesToSkip(fileFormat.getSkipFirstLines());
        itemReader.setInputStreamSource(() -> Files.newInputStream(file));

        try {
            itemReader.open();
            List<T> items = itemReader.readAll();
            //items.sort(Comparator.naturalOrder());
            if (request.endTime() != null) {
                long endTime = Chronological.toEpochNanos(request.endTime());
                items.removeIf(item -> item.getTime() > endTime);
            }

            int itemCount = items.size();
            int itemLimit = request.limit();
            if (itemLimit > 0 && itemLimit < itemCount)
                items = items.subList(itemCount - itemLimit, itemCount);

            return Flux.fromIterable(items);

        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            CloseHelper.closeQuietly(itemReader);
        }
    }

    public MarketMessageSource iterator(DataQuery<?> request, ExecutionContext context) {
        SymbolIdentifier identifier = new SymbolIdentifier(request.resource().symbol());
        Path file = getFileTreeMetadata().availableSymbols.get(identifier);
        if (file == null)
            throw new DataProviderException(String.format("Symbol '%s' not found", identifier));

        context.put("TimeFrame", request.resource().timeFrame());

        FlatFileItemReader<Candle> itemReader = new FlatFileItemReader<>();
        itemReader.setLineMapper((LineMapper<Candle>) fileFormat.getLineMapper().createLineMapper(context));
        itemReader.setLinesToSkip(fileFormat.getSkipFirstLines());
        itemReader.setInputStreamSource(() -> Files.newInputStream(file));
        itemReader.open();

        return new MarketMessageSource() {
            @Override
            public MarketEvent getMessage() {
                try {
                    var bar = itemReader.read();
                    return (bar == null) ? null : new ImmutableBarEvent(identifier, bar);
                }
                catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }

            @Override
            public boolean isOpen() {
                return itemReader.isOpen();
            }

            @Override
            public void close() {
                itemReader.close();
            }
        };
    }

    public final FileSystem getFileSystem() {
        return fileSystem.get();
    }

    public final FlatFileFormat getFileFormat() {
        return fileFormat;
    }

    public final Iterable<Path> getBaseDirectories() {
        return baseDirectories;
    }

    public void close() throws IOException {
        if (isCloseable(fileSystem))
            getFileSystem().close();
    }

    protected SymbolIdentity asIdentifier(Path path) {
        return new SymbolIdentifier(asAssetName(path.getFileName()), asAssetType(path));
    }

    protected String asAssetName(Path fileName) {
        String name = fileName.toString();
        int lastDot = name.lastIndexOf('.');
        name = (lastDot > 0)? name.substring(0, lastDot): name;
        return getFileFormat().isCaseSensitiveSymbols()? name : name.toUpperCase();
    }

    protected IdentityType asAssetType(Path path) {
        return InstrumentType.CUSTOM;
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

    @Override
    public List<SymbolGroup> listSymbolGroups() {
        return getFileTreeMetadata().availableGroupsList();
    }

    public List<SymbolGroup> listSymbolGroups(Predicate<SymbolGroup> filter) {
        return getFileTreeMetadata().availableGroupsList(filter);
    }

    @Override
    public List<SymbolIdentity> listSymbols() {
        return getFileTreeMetadata().getAvailableSymbolsList();
    }

    private static class FileTreeMetadata {
        private final Map<String, SymbolGroup> availableGroups;
        private final Map<SymbolIdentifier, Path> availableSymbols;
        private List<SymbolGroup> availableGroupsList;
        private List<SymbolIdentity> availableSymbolsList;

        private FileTreeMetadata(Map<String, SymbolGroup> availableGroups, Map<SymbolIdentifier, Path> availableSymbols) {
            this.availableGroups = availableGroups;
            this.availableSymbols = availableSymbols;
        }

        public List<SymbolGroup> availableGroupsList() {
            if (availableGroupsList == null)
                availableGroupsList = List.copyOf(availableGroups.values());
            return availableGroupsList;
        }

        public List<SymbolGroup> availableGroupsList(Predicate<SymbolGroup> filter) {
            return availableGroups.values().stream().filter(filter).toList();
        }

        public List<SymbolIdentity> getAvailableSymbolsList() {
            if (availableSymbolsList == null)
                availableSymbolsList = List.copyOf(availableSymbols.keySet());
            return availableSymbolsList;
        }
    }

    private FileTreeMetadata metadata;

    private FileTreeMetadata getFileTreeMetadata() {
        if (metadata == null)
            metadata = scanFileTree(getBaseDirectories());
        return metadata;
    }

    protected FileTreeMetadata scanFileTree(Iterable<Path> baseDirs) {
        var availableGroups = new TreeMap<String, SymbolGroup>();
        var availableSymbols = new TreeMap<SymbolIdentifier, Path>();

        try {
            FileTreeScanner scanner = new FileTreeScanner(availableGroups, availableSymbols);
            for (Path rootDir : baseDirs)
                Files.walkFileTree(rootDir, scanner);

        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return new FileTreeMetadata(availableGroups, availableSymbols);
    }

    private class FileTreeScanner extends SimpleFileVisitor<Path> {
        private final Map<String, SymbolGroup> availableGroups;
        private final Map<SymbolIdentifier, Path> availableSymbols;

        private FileTreeScanner(Map<String, SymbolGroup> availableGroups, Map<SymbolIdentifier, Path> availableSymbols) {
            this.availableGroups = availableGroups;
            this.availableSymbols = availableSymbols;
        }

        @Override
        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
            SymbolGroup group = asGroup(dir);
            availableGroups.put(group.name(), group);
            return super.preVisitDirectory(dir, attrs);
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
            SymbolIdentity symbol = asIdentifier(file);
            availableSymbols.put(new SymbolIdentifier(symbol), file);
            return super.visitFile(file, attrs);
        }
    }

    private boolean nonNumericProposalExchange = true;

    public void setNonNumericProposalExchange(boolean flag) {
        this.nonNumericProposalExchange = flag;
    }

    @Override
    public List<Symbol> getProposals(String keyword) {
        if (keyword.length() <= 1)
            return List.of();

        // convert text to upper case
        String text = keyword.toUpperCase();
        List<Symbol> list = new ArrayList<>();
        getFileTreeMetadata().availableSymbols.forEach((symbol, path) -> {
            if (symbol.name().contains(text)) {
                Symbol match = new Symbol(symbol, this);

                Path parent = path.getParent();
                if (parent != null)
                    match.setExchange(getProposalExchangeName(parent));

                list.add(match);
            }
        });

        list.sort((o1, o2) -> {
            int p1 = o1.getName().indexOf(text);
            int p2 = o2.getName().indexOf(text);
            if (p1 != p2)
                return p1 - p2;
            return o1.getName().compareTo(o2.getName());
        });
        return list;
    }

    protected String getProposalExchangeName(Path inFolder) {
        SymbolGroup symbolGroup = asGroup(inFolder);
        if (symbolGroup == null)
            return "";

        String symbolGroupName = getSimpleName(symbolGroup);
        if (nonNumericProposalExchange
                && StringUtils.isNumeric(symbolGroupName)
                && (inFolder = inFolder.getParent()) != null) {
            symbolGroupName = getProposalExchangeName(inFolder) + '/' + symbolGroupName;
        }
        return symbolGroupName;
    }
}
