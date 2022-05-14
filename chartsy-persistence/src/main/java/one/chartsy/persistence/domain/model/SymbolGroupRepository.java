/* Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.persistence.domain.model;

import one.chartsy.SymbolGroupContentRepository;
import one.chartsy.persistence.domain.SymbolGroupAggregateData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SymbolGroupRepository extends JpaRepository<SymbolGroupAggregateData, Long>, SymbolGroupContentRepository {

    @Override
    List<SymbolGroupAggregateData> findByParentGroupId(Long parentId);
}