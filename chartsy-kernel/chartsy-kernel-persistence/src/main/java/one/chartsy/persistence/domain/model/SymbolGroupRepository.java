/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.persistence.domain.model;

import one.chartsy.SymbolGroupContentRepository;
import one.chartsy.persistence.domain.SymbolGroupAggregateData;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SymbolGroupRepository extends SymbolGroupContentRepository {

    @Override
    List<SymbolGroupAggregateData> findByParentGroupId(Long parentId);

    SymbolGroupAggregateData save(SymbolGroupAggregateData group);

    SymbolGroupAggregateData saveAndFlush(SymbolGroupAggregateData group);

    void delete(SymbolGroupAggregateData group);

    void deleteAll(Iterable<? extends SymbolGroupAggregateData> groups);
}
