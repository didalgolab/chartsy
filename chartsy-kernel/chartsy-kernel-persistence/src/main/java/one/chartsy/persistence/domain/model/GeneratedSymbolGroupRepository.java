/* Copyright 2026 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.persistence.domain.model;

import one.chartsy.persistence.domain.SymbolGroupAggregateData;
import org.springframework.data.repository.ListCrudRepository;

import java.util.List;

public interface GeneratedSymbolGroupRepository extends ListCrudRepository<SymbolGroupAggregateData, Long> {

    List<SymbolGroupAggregateData> findByParentGroupId(Long parentId);
}
