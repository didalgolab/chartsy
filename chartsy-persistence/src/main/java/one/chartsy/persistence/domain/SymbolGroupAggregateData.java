/* Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.persistence.domain;

import lombok.Getter;
import lombok.Setter;
import one.chartsy.*;
import one.chartsy.data.provider.DataProvider;
import one.chartsy.data.provider.DataProviderLoader;
import one.chartsy.data.provider.HierarchicalConfiguration;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.*;

import static javax.persistence.GenerationType.SEQUENCE;
import static one.chartsy.SymbolGroupContent.Type.DATA_PROVIDER_FOLDER;
import static one.chartsy.SymbolGroupContent.Type.FOLDER;

@Getter
@Setter
@Entity
@Table(name = "ONE_SYMBOL_GROUPS")
public class SymbolGroupAggregateData implements SymbolGroupContent {
    @Id
    @GeneratedValue(strategy = SEQUENCE, generator = "ONE_SYMBOL_GROUP_IDS")
    @SequenceGenerator(name = "ONE_SYMBOL_GROUP_IDS", sequenceName = "ONE_SYMBOL_GROUP_IDS")
    private Long id;
    private Long parentGroupId;
    private String name;
    private Type contentType;
    private String stereotype;
    private String dataProviderDescriptor;
    private LocalDateTime created;
    private LocalDateTime lastModified;

    @Transient
    private LocalDateTime removed;

    @PrePersist
    public void markNewlyCreated() {
        created = LocalDateTime.now();
    }

    @PreUpdate
    public void markModified() {
        lastModified = LocalDateTime.now();
    }

    @PreRemove
    public void markRemoved() {
        if (removed == null)
            removed = LocalDateTime.now();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this)
            return true;
        if (id == null)
            return super.equals(obj);

        if (obj instanceof SymbolGroupAggregateData that)
            return Objects.equals(id, that.id) && Objects.equals(lastModified, that.lastModified);

        return false;
    }

    @Override
    public int hashCode() {
        if (id == null)
            return super.hashCode();

        int hash = id.hashCode();
        if (lastModified != null)
            hash ^= lastModified.hashCode();
        return hash;
    }

    @Override
    public Optional<Symbol> getAsSymbol() {
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
        if (id != null || FOLDER.equals(getContentType()))
            content.addAll(repo.findByParentGroupId(id));

        switch (getContentType()) {
            case DATA_PROVIDER -> loadDataProviderContent(loader, content);
            case DATA_PROVIDER_FOLDER -> loadDataProviderFolderContent(loader, content);
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
        symbolGroup.setContentType(DATA_PROVIDER_FOLDER);
        symbolGroup.setDataProvider(dataProvider);
        symbolGroup.setSymbolGroup(group);

        return symbolGroup;
    }

    protected Symbol asSymbol(SymbolIdentity symbolId, DataProvider provider) {
        return new Symbol(symbolId, provider);
    }
}