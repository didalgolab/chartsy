/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.data.provider;

import one.chartsy.Candle;
import one.chartsy.Symbol;
import one.chartsy.SymbolGroup;
import one.chartsy.SymbolIdentity;
import one.chartsy.SymbolResource;
import one.chartsy.TimeFrame;
import one.chartsy.TimeFrameHelper;
import one.chartsy.context.ExecutionContext;
import one.chartsy.core.ResourceHandle;
import one.chartsy.data.DataQuery;
import one.chartsy.data.SimpleCandle;
import one.chartsy.data.UnsupportedDataQueryException;
import one.chartsy.data.provider.file.FileSystemCache;
import one.chartsy.data.provider.file.FlatFileFormat;
import one.chartsy.data.provider.file.FlatFileItemReader;
import one.chartsy.data.provider.file.FlatFileParseException;
import one.chartsy.data.provider.file.LineMapper;
import one.chartsy.financial.IdentityType;
import one.chartsy.financial.InstrumentType;
import one.chartsy.financial.SymbolIdentifier;
import one.chartsy.messaging.MarketEvent;
import one.chartsy.messaging.MarketMessageSource;
import one.chartsy.messaging.data.TradeBar;
import one.chartsy.time.Chronological;
import org.apache.commons.lang3.StringUtils;
import org.openide.util.Lookup;
import org.openide.util.lookup.Lookups;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileSystem;
import java.nio.charset.Charset;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

public class FlatFileDataProvider extends AbstractDataProvider implements SymbolListAccessor, SymbolProposalProvider, HierarchicalConfiguration, LatestCandleTimeProvider, AutoCloseable {
    private final Lookup lookup = Lookups.singleton(this);
    private final FlatFileFormat fileFormat;
    private final ResourceHandle<FileSystem> fileSystem;
    private final Iterable<Path> baseDirectories;
    private final Map<SymbolIdentifier, CachedTimeFrame> sourceTimeFrames = new ConcurrentHashMap<>();
    private final Map<SymbolIdentifier, CachedEndpoint> sourceEndpoints = new ConcurrentHashMap<>();
    private final ThreadLocal<FlatFileLastRecordReader> lastRecordReaders = ThreadLocal.withInitial(FlatFileLastRecordReader::new);
    private final boolean endpointMetadataSupported;

    private record SourceRevision(long size, FileTime modifiedTime, Object fileKey) {
        static SourceRevision of(BasicFileAttributes attributes) {
            return new SourceRevision(attributes.size(), attributes.lastModifiedTime(), attributes.fileKey());
        }
    }

    private record CachedEndpoint(BasicFileAttributes attributes, Snapshot snapshot) {
        boolean matches(BasicFileAttributes current) {
            return sameFile(attributes, current);
        }
    }

    private record CachedTimeFrame(BasicFileAttributes attributes, TimeFrame timeFrame) {
        boolean matches(BasicFileAttributes current) {
            return sameFile(attributes, current);
        }
    }

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
        this.endpointMetadataSupported = fileFormat.isLatestCandleFromLastRecord()
                && fileFormat.getDataOrder() == Chronological.ChronoOrder.CHRONOLOGICAL
                && fileFormat.getTimeFrameExtractor() != null
                && hasSingleByteLineBreaks(fileFormat.getEncoding());
    }

    private static boolean hasSingleByteLineBreaks(String encoding) {
        Charset charset = Charset.forName(encoding);
        return charset.newEncoder().maxBytesPerChar() == 1
                && Arrays.equals("\r\n".getBytes(charset), new byte[] {'\r', '\n'});
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
    public boolean supports(SymbolResource<Candle> resource) {
        if (!endpointMetadataSupported)
            return false;
        TimeFrame nativeFrame = sourceTimeFrame(new SymbolIdentifier(resource.symbol()));
        return nativeFrame == null || canAggregate(nativeFrame, resource.timeFrame());
    }

    @Override
    public Snapshot getLatestCandleSnapshot(SymbolResource<Candle> resource) {
        if (!endpointMetadataSupported)
            throw new UnsupportedOperationException("Endpoint metadata is not supported by this file format");
        SymbolIdentifier identifier = new SymbolIdentifier(resource.symbol());
        Path file = fileForSymbol(identifier);
        try {
            var before = Files.readAttributes(file, BasicFileAttributes.class);
            TimeFrame nativeFrame = sourceTimeFrame(identifier);
            if (nativeFrame != null && !canAggregate(nativeFrame, resource.timeFrame()))
                throw new UnsupportedOperationException("Cannot create " + resource.timeFrame() + " candles from " + nativeFrame + " data");
            var cached = sourceEndpoints.get(identifier);
            if (cached != null && cached.matches(before)) {
                verifyUnchanged(identifier, file, before);
                return cached.snapshot();
            }

            OptionalLong time = readLastCandleTime(file, nativeFrame);
            return cacheEndpoint(identifier, file, before, time);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private OptionalLong readLastCandleTime(Path file, TimeFrame nativeFrame) throws IOException {
        if (nativeFrame == null)
            return OptionalLong.empty();
        String line = lastRecordReaders.get().read(file, fileFormat);
        if (line == null)
            return OptionalLong.empty();

        ExecutionContext context = new ExecutionContext();
        context.put("TimeFrame", nativeFrame);
        try {
            Candle last = (Candle) fileFormat.getLineMapper().createLineMapper(context).mapLine(line, -1);
            return OptionalLong.of(last.time());
        } catch (FlatFileParseException e) {
            throw e;
        } catch (Exception e) {
            throw new FlatFileParseException("Unable to parse the last candle", e, line, -1);
        }
    }

    private Path fileForSymbol(SymbolIdentifier identifier) {
        Path file = getFileTreeMetadata().availableSymbols.get(identifier);
        if (file == null)
            throw new DataProviderException("Symbol '" + identifier + "' not found");
        return file;
    }

    private Snapshot cacheEndpoint(SymbolIdentifier identifier, Path file, BasicFileAttributes before, OptionalLong time) throws IOException {
        verifyUnchanged(identifier, file, before);
        var snapshot = new Snapshot(time, SourceRevision.of(before));
        sourceEndpoints.put(identifier, new CachedEndpoint(before, snapshot));
        return snapshot;
    }

    private void verifyUnchanged(SymbolIdentifier identifier, Path file, BasicFileAttributes before) throws IOException {
        if (!sameFile(before, Files.readAttributes(file, BasicFileAttributes.class))) {
            sourceEndpoints.remove(identifier);
            sourceTimeFrames.remove(identifier);
            throw new DataProviderException("History changed while reading '" + identifier + "'; run the exploration again");
        }
    }

    private static boolean sameFile(BasicFileAttributes first, BasicFileAttributes second) {
        return first.size() == second.size()
                && first.lastModifiedTime().equals(second.lastModifiedTime())
                && Objects.equals(first.fileKey(), second.fileKey());
    }

    private static BasicFileAttributes fileAttributes(Path file) {
        try {
            return Files.readAttributes(file, BasicFileAttributes.class);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
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

    @Override
    public List<TimeFrame> getAvailableTimeFrames(SymbolIdentity symbol) {
        TimeFrame source = sourceTimeFrame(new SymbolIdentifier(symbol));
        if (source == null)
            return super.getAvailableTimeFrames(symbol);

        List<TimeFrame> frames = new ArrayList<>();
        frames.add(source);
        Arrays.stream(TimeFrame.Period.values())
                .filter(frame -> !frame.equals(source) && canAggregate(source, frame))
                .forEach(frames::add);
        return List.copyOf(frames);
    }

    private TimeFrame sourceTimeFrame(SymbolIdentifier symbol) {
        if (fileFormat.getTimeFrameExtractor() == null)
            return null;
        Path file = fileForSymbol(symbol);
        try {
            var attributes = Files.readAttributes(file, BasicFileAttributes.class);
            var cached = sourceTimeFrames.get(symbol);
            if (cached != null && cached.matches(attributes))
                return cached.timeFrame();
            try (var reader = new FlatFileItemReader<String>(fileFormat)) {
                reader.setLineMapper((line, number) -> line);
                reader.setInputStreamSource(() -> Files.newInputStream(file));
                reader.open();
                String line = reader.read();
                if (line == null) {
                    sourceTimeFrames.remove(symbol);
                    return null;
                }
                var timeFrame = fileFormat.getTimeFrameExtractor().apply(line);
                if (!sameFile(attributes, Files.readAttributes(file, BasicFileAttributes.class)))
                    throw new DataProviderException("History changed while reading '" + symbol + "'; run the exploration again");
                sourceTimeFrames.put(symbol, new CachedTimeFrame(attributes, timeFrame));
                return timeFrame;
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static boolean canAggregate(TimeFrame source, TimeFrame target) {
        if (sameTimeFrame(source, target))
            return true;
        // Coarsening is supported only for known boundaries that do not split source bars.
        if (!(target instanceof TimeFrame.Period)
                || !source.getTimeZone().equals(target.getTimeZone())
                || !source.getDailyAlignment().equals(target.getDailyAlignment()))
            return false;
        var sourceSeconds = TimeFrameHelper.toSeconds(source);
        var targetSeconds = TimeFrameHelper.toSeconds(target);
        if (!(source instanceof TimeFrame.Period)
                && (sourceSeconds.isEmpty() || sourceSeconds.getAsInt() <= 0
                || 86400 % sourceSeconds.getAsInt() != 0 || source.getCandleAlignment().isPresent()))
            return false;
        if (sourceSeconds.isPresent() && targetSeconds.isPresent())
            return targetSeconds.getAsInt() % sourceSeconds.getAsInt() == 0;
        var targetMonths = TimeFrameHelper.toMonths(target);
        if (targetMonths.isEmpty())
            return false;
        if (sourceSeconds.isPresent())
            return 86400 % sourceSeconds.getAsInt() == 0;
        var sourceMonths = TimeFrameHelper.toMonths(source);
        return sourceMonths.isPresent() && targetMonths.getAsInt() % sourceMonths.getAsInt() == 0;
    }

    private static boolean sameTimeFrame(TimeFrame first, TimeFrame second) {
        return first.equals(second)
                || first instanceof TimeFrame.TemporallyRegular a
                && second instanceof TimeFrame.TemporallyRegular b
                && a.getRegularity().equals(b.getRegularity())
                && first.getTimeZone().equals(second.getTimeZone())
                && first.getDailyAlignment().equals(second.getDailyAlignment())
                && first.getCandleAlignment().equals(second.getCandleAlignment());
    }

    public <T extends Candle> Flux<T> queryForCandles(DataQuery<T> request) {
        SymbolIdentifier identifier = new SymbolIdentifier(request.resource().symbol());
        Path file = fileForSymbol(identifier);

        var before = endpointMetadataSupported ? fileAttributes(file) : null;
        TimeFrame requestedTimeFrame = request.resource().timeFrame();
        TimeFrame sourceTimeFrame = sourceTimeFrame(identifier);
        if (sourceTimeFrame != null && !canAggregate(sourceTimeFrame, requestedTimeFrame))
            throw new UnsupportedDataQueryException(request,
                    "Cannot create " + requestedTimeFrame + " candles from " + sourceTimeFrame + " data");

        ExecutionContext context = new ExecutionContext();
        context.put("TimeFrame", sourceTimeFrame != null ? sourceTimeFrame : requestedTimeFrame);

        try (FlatFileItemReader<T> itemReader = endpointMetadataSupported
                ? new FlatFileItemReader<>(fileFormat) : new FlatFileItemReader<>()) {
            itemReader.setLineMapper((LineMapper<T>) fileFormat.getLineMapper().createLineMapper(context));
            itemReader.setLinesToSkip(fileFormat.getSkipFirstLines());
            itemReader.setInputStreamSource(() -> Files.newInputStream(file));

            itemReader.open();
            List<T> items = itemReader.readAll();
            if (before != null && request.startTime() == null && request.endTime() == null && request.limit() <= 0)
                cacheEndpoint(identifier, file, before, items.isEmpty() ? OptionalLong.empty() : OptionalLong.of(items.getLast().time()));
            if (request.endTime() != null) {
                long endTime = Chronological.toEpochNanos(request.endTime());
                items.removeIf(item -> item.time() > endTime);
            }

            if (sourceTimeFrame != null && !sameTimeFrame(sourceTimeFrame, requestedTimeFrame))
                items = (List<T>) requestedTimeFrame.getAggregator().aggregate(items);

            int itemCount = items.size();
            int itemLimit = request.limit();
            if (itemLimit > 0 && itemLimit < itemCount)
                items = items.subList(itemCount - itemLimit, itemCount);

            return Flux.fromIterable(items);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public MarketMessageSource iterator(DataQuery<?> request, ExecutionContext context) {
        SymbolIdentifier identifier = new SymbolIdentifier(request.resource().symbol());
        Path file = getFileTreeMetadata().availableSymbols.get(identifier);
        if (file == null)
            throw new DataProviderException(String.format("Symbol '%s' not found", identifier));

        context.put("TimeFrame", request.resource().timeFrame());

        var startTime = (request.startTime() != null)? Chronological.toEpochNanos(request.startTime()): Long.MIN_VALUE;
        var endTime = (request.endTime() != null)? Chronological.toEpochNanos(request.endTime()): Long.MAX_VALUE;

        FlatFileItemReader<Candle> itemReader = new FlatFileItemReader<>();
        itemReader.setLineMapper((LineMapper<Candle>) fileFormat.getLineMapper().createLineMapper(context));
        itemReader.setLinesToSkip(fileFormat.getSkipFirstLines());
        itemReader.setInputStreamSource(() -> Files.newInputStream(file));
        itemReader.open();

        return new MarketMessageSource() {
            @Override
            public MarketEvent getMessage() {
                try {
                    for (;;) {
                        var bar = itemReader.read();
                        if (bar == null || bar.time() > endTime)
                            return null;
                        if (bar.time() < startTime)
                            continue;

                        return new TradeBar.Of(identifier, bar);
                    }
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

    @Override
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

    private volatile FileTreeMetadata metadata;

    private synchronized FileTreeMetadata getFileTreeMetadata() {
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
