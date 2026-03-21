/* Copyright 2026 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.persistence.domain.model;

import one.chartsy.persistence.domain.ChartTemplateAggregateData;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class SpringGeneratedChartTemplateRepository implements ChartTemplateRepository {

    private final GeneratedChartTemplateRepository delegate;

    public SpringGeneratedChartTemplateRepository(GeneratedChartTemplateRepository delegate) {
        this.delegate = delegate;
    }

    @Override
    public Optional<ChartTemplateAggregateData> findByTemplateKey(UUID templateKey) {
        return delegate.findByTemplateKey(templateKey);
    }

    @Override
    public List<ChartTemplateAggregateData> findAllByOriginOrderByNameAsc(ChartTemplateAggregateData.Origin origin) {
        return delegate.findAllByOriginOrderByNameAsc(origin);
    }

    @Override
    public List<ChartTemplateAggregateData> findAllByOrderByDefaultTemplateDescNameAsc() {
        return delegate.findAllByOrderByDefaultTemplateDescNameAsc();
    }

    @Override
    public boolean existsByNameKey(String nameKey) {
        return delegate.existsByNameKey(nameKey);
    }

    @Override
    public boolean existsByNameKeyAndTemplateKeyNot(String nameKey, UUID templateKey) {
        return delegate.existsByNameKeyAndTemplateKeyNot(nameKey, templateKey);
    }

    @Override
    public ChartTemplateAggregateData save(ChartTemplateAggregateData template) {
        return delegate.save(template);
    }

    @Override
    public void delete(ChartTemplateAggregateData template) {
        delegate.delete(template);
    }
}
