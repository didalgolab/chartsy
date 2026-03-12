/*
 * Copyright 2026 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.persistence.domain.model;

import one.chartsy.persistence.domain.ChartTemplateAggregateData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ChartTemplateRepository extends JpaRepository<ChartTemplateAggregateData, Long> {

    Optional<ChartTemplateAggregateData> findByTemplateKey(UUID templateKey);

    List<ChartTemplateAggregateData> findAllByOriginOrderByNameAsc(ChartTemplateAggregateData.Origin origin);

    List<ChartTemplateAggregateData> findAllByOrderByDefaultTemplateDescNameAsc();

    boolean existsByNameKey(String nameKey);

    boolean existsByNameKeyAndTemplateKeyNot(String nameKey, UUID templateKey);
}
