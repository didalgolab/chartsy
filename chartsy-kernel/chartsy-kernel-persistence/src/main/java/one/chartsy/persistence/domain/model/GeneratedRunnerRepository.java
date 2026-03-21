/* Copyright 2026 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.persistence.domain.model;

import one.chartsy.persistence.domain.RunnerAggregateData;
import org.springframework.data.repository.ListCrudRepository;

public interface GeneratedRunnerRepository extends ListCrudRepository<RunnerAggregateData, Long> {
}
