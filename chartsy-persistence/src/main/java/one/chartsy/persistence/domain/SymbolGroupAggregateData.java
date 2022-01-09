package one.chartsy.persistence.domain;

import lombok.Getter;
import lombok.Setter;
import one.chartsy.*;
import one.chartsy.data.provider.DataProvider;
import one.chartsy.data.provider.DataProviderLoader;
import one.chartsy.data.provider.HierarchicalConfiguration;

import javax.persistence.*;
import java.util.*;

@Getter
@Setter
@Entity
@Table(name = "ONE_SYMBOL_GROUPS")
public class SymbolGroupAggregateData implements SymbolGroupContent {
    @Id
    @GeneratedValue
    private Long id;
    private Long parentGroupId;
    private String name;
    private String typeName;
    private String dataProviderDescriptor;
    private Date auditMD;

    @Transient
    private Date auditRD;

    @PreUpdate
    public void markModified() {
        auditMD = new Date();
    }

    @PreRemove
    public void markRemoved() {
        if (auditRD == null)
            auditRD = new Date();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this)
            return true;
        if (id == null)
            return super.equals(obj);

        if (obj instanceof SymbolGroupAggregateData that)
            return Objects.equals(id, that.id) && Objects.equals(auditMD, that.auditMD);

        return false;
    }

    @Override
    public int hashCode() {
        if (id == null)
            return super.hashCode();

        int hash = id.hashCode();
        if (auditMD != null)
            hash ^= (int) auditMD.getTime();
        return hash;
    }

    @Override
    public Optional<SymbolIdentity> getSymbol() {
        return Optional.empty();
    }

    @Transient
    private volatile DataProvider dataProvider;
    @Transient
    private SymbolGroup symbolGroup;

    public DataProvider getDataProvider(DataProviderLoader loader) {
        DataProvider provider = this.dataProvider;
        if (provider == null && getDataProviderDescriptor() != null)
            provider = dataProvider = loader.load(getDataProviderDescriptor());

        return provider;
    }

    @Override
    public List<SymbolGroupContent> getContent(SymbolGroupContentRepository repo, DataProviderLoader loader) {
        List<SymbolGroupContent> content = new ArrayList<>();
        if (id != null || "FOLDER".equals(getTypeName()))
            content.addAll(repo.findByParentGroupId(id));

        switch (getTypeName()) {
            case "DATA_PROVIDER" -> loadDataProviderContent(loader, content);
            case "DATA_PROVIDER_FOLDER" -> loadDataProviderFolderContent(loader, content);
        }
        return content;
    }

    protected void loadDataProviderContent(DataProviderLoader loader, Collection<SymbolGroupContent> content) {
        DataProvider provider = getDataProvider(loader);
        var hierarchicalConfiguration = HierarchicalConfiguration.of(provider);
        var groups = hierarchicalConfiguration.getRootGroups();
        if (groups.size() == 1)
            groups = hierarchicalConfiguration.getSubGroups(groups.get(0));

        int lastSize = content.size();
        for (var group : groups)
            content.add(asSymbolGroup(group, hierarchicalConfiguration));
        for (var identifier : provider.listSymbols(SymbolGroup.BASE))
            content.add(asSymbol(identifier, provider));
        boolean wasEmpty = (lastSize == content.size());
        if (wasEmpty)
            for (var identifier : provider.listSymbols())
                content.add(asSymbol(identifier, provider));
    }

    protected void loadDataProviderFolderContent(DataProviderLoader loader, Collection<SymbolGroupContent> content) {
        var provider = getDataProvider(loader);
        var hierarchicalConfiguration = HierarchicalConfiguration.of(provider);
        var channelIds = hierarchicalConfiguration.getSubGroups(symbolGroup);

        for (var channel : channelIds)
            content.add(asSymbolGroup(channel, hierarchicalConfiguration));
        for (var identifier : provider.listSymbols(symbolGroup))
            content.add(asSymbol(identifier, provider));
    }

    protected SymbolGroupAggregateData asSymbolGroup(SymbolGroup group, HierarchicalConfiguration config) {
        var symbolGroup = new SymbolGroupAggregateData();
        symbolGroup.setName(config.getSimpleName(group));
        symbolGroup.setTypeName("DATA_PROVIDER_FOLDER");
        symbolGroup.setDataProvider(dataProvider);
        symbolGroup.setSymbolGroup(group);

        return symbolGroup;
    }

    protected Symbol asSymbol(SymbolIdentity symbolId, DataProvider provider) {
        return new Symbol(symbolId, provider);
    }
}