/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.persistence.domain.model;

import one.chartsy.persistence.domain.RunnerAggregateData;

import java.util.List;

public interface RunnerRepository {

    List<RunnerAggregateData> findAll();
}
