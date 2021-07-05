package one.chartsy.data.provider;

import one.chartsy.SymbolGroup;

import java.util.List;

public interface HierarchicalConfiguration {

    List<SymbolGroup> getRootGroups();
    List<SymbolGroup> getSubGroups(SymbolGroup parent);
    String getSimpleName(SymbolGroup group);

    static HierarchicalConfiguration of(DataProvider provider) {
        var configuration = provider.getLookup().lookup(HierarchicalConfiguration.class);
        if (configuration == null)
            configuration = new FlatHierarchicalConfiguration(provider);
        return configuration;
    }
}
