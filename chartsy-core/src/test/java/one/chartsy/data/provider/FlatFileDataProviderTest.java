package one.chartsy.data.provider;

import one.chartsy.SymbolGroup;
import one.chartsy.SymbolIdentity;
import one.chartsy.data.provider.file.FlatFileFormat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;
import static org.junit.jupiter.api.Assertions.*;

class FlatFileDataProviderTest {

    FlatFileDataProvider provider;

    @BeforeEach
    void loadDataProvider() throws URISyntaxException, IOException {
        FlatFileFormat fileFormat = FlatFileFormat.builder().build();
        FileSystem fileSystem = FileSystems.newFileSystem(Paths.get(getClass().getResource("/FileSystemDataProvider.zip").toURI()), Map.of());

        provider = new FlatFileDataProvider(fileFormat, fileSystem, "FileSystemDataProvider.zip");
    }

    @Test
    void is_closed_when_GCed() throws URISyntaxException, IOException, InterruptedException {
        var provider = FlatFileFormat.builder().build()
                .newDataProvider(Paths.get(getClass().getResource("/FileSystemDataProvider.zip").toURI()));
        var fileSystem = provider.getFileSystem();

        System.gc();
        Thread.sleep(1);
        assertTrue(fileSystem.isOpen(), "while reachable");

        //noinspection UnusedAssignment
        provider = null;
        System.gc();
        Thread.sleep(1);
        assertFalse(fileSystem.isOpen(), "when unreachable and GCed");
    }

    @Test
    void is_not_closed_on_GC_when_cached() throws URISyntaxException, IOException, InterruptedException {
        var provider1 = FlatFileFormat.builder().build()
                .newDataProvider(Paths.get(getClass().getResource("/FileSystemDataProvider.zip").toURI()));
        var provider2 = FlatFileFormat.builder().build()
                .newDataProvider(Paths.get(getClass().getResource("/FileSystemDataProvider.zip").toURI()));

        var fileSystem = provider1.getFileSystem();
        assertSame(fileSystem, provider2.getFileSystem());

        //noinspection UnusedAssignment
        provider2 = null;
        System.gc();
        Thread.sleep(1);
        assertTrue(fileSystem.isOpen(), "when cached and still in use");
        assertTrue(provider1.getFileSystem().isOpen(), "when cached and still in use");
    }

    @Test
    void is_not_closable_manually_when_cached() throws URISyntaxException, IOException, InterruptedException {
        var provider1 = FlatFileFormat.builder().build()
                .newDataProvider(Paths.get(getClass().getResource("/FileSystemDataProvider.zip").toURI()));
        var provider2 = FlatFileFormat.builder().build()
                .newDataProvider(Paths.get(getClass().getResource("/FileSystemDataProvider.zip").toURI()));

        provider1.close();
        assertTrue(provider1.getFileSystem().isOpen(), "when cached and still in use");
        assertTrue(provider2.getFileSystem().isOpen(), "when cached and still in use");
    }

    @Test
    void provides_HierarchicalConfiguration() {
        HierarchicalConfiguration conf = provider.getLookup().lookup(HierarchicalConfiguration.class);

        // verify
        assertNotNull(conf);
        assertEquals(List.of("/"), names(conf.getRootGroups()));

        SymbolGroup root = conf.getRootGroups().get(0);
        List<SymbolGroup> children = conf.getSubGroups(root);
        assertEquals(List.of("/group1", "/group2"), names(children));
        assertEquals(List.of("/group2/2a", "/group2/2b"),
                names(conf.getSubGroups(children.get(1))));
        assertEquals(List.of("2a", "2b"),
                conf.getSubGroups(children.get(1)).stream().map(conf::getSimpleName).collect(toList()));
        assertEquals(emptyList(), names(conf.getSubGroups(children.get(0))));
    }

    @Test
    void getAvailableGroups_gives_list_of_directories_on_the_FileSystem() {
        assertEquals(List.of("/", "/group1", "/group2", "/group2/2a", "/group2/2b"),
                names(provider.listSymbolGroups()));
    }

    @Test
    void getSymbols_gives_list_of_files_on_the_FileSystem() {
        assertEquals(List.of("ID1", "ID2"),
                names(provider.listSymbols(new SymbolGroup("/group1"))));
        assertEquals(emptyList(),
                names(provider.listSymbols(new SymbolGroup("/group2"))));
    }

    private static List<String> names(Collection<SymbolGroup> list) {
        return list.stream().map(SymbolGroup::name).collect(toList());
    }

    private static List<String> names(List<? extends SymbolIdentity> list) {
        return list.stream().map(SymbolIdentity::name).collect(toList());
    }
}