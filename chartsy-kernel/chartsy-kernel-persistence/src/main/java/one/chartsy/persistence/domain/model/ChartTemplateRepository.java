/* Copyright 2026 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.persistence.domain.model;

import one.chartsy.persistence.domain.ChartTemplateAggregateData;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChartTemplateRepository {

    Optional<ChartTemplateAggregateData> findByTemplateKey(UUID templateKey);

    List<ChartTemplateAggregateData> findAllByOriginOrderByNameAsc(ChartTemplateAggregateData.Origin origin);

    List<ChartTemplateAggregateData> findAllByOrderByDefaultTemplateDescNameAsc();

    boolean existsByNameKey(String nameKey);

    boolean existsByNameKeyAndTemplateKeyNot(String nameKey, UUID templateKey);

    ChartTemplateAggregateData save(ChartTemplateAggregateData template);

    void delete(ChartTemplateAggregateData template);
}
