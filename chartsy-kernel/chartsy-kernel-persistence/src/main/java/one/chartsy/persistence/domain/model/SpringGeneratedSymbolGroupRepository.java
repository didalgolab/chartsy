/* Copyright 2026 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.persistence.domain.model;

import one.chartsy.persistence.domain.SymbolGroupAggregateData;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;

public class SpringGeneratedSymbolGroupRepository implements SymbolGroupRepository {

    private final GeneratedSymbolGroupRepository delegate;
    private final ApplicationEventPublisher eventPublisher;

    public SpringGeneratedSymbolGroupRepository(
            GeneratedSymbolGroupRepository delegate,
            ApplicationEventPublisher eventPublisher
    ) {
        this.delegate = delegate;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public List<SymbolGroupAggregateData> findByParentGroupId(Long parentId) {
        return delegate.findByParentGroupId(parentId);
    }

    @Override
    public SymbolGroupAggregateData save(SymbolGroupAggregateData group) {
        var saved = delegate.save(group);
        eventPublisher.publishEvent(saved);
        return saved;
    }

    @Override
    public SymbolGroupAggregateData saveAndFlush(SymbolGroupAggregateData group) {
        return save(group);
    }

    @Override
    public void delete(SymbolGroupAggregateData group) {
        delegate.delete(group);
        group.markRemoved();
        eventPublisher.publishEvent(group);
    }

    @Override
    public void deleteAll(Iterable<? extends SymbolGroupAggregateData> groups) {
        for (var group : groups)
            delete(group);
    }
}
