/* Copyright 2026 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.persistence.domain.model;

import one.chartsy.persistence.domain.RunnerAggregateData;

import java.util.List;

public class SpringGeneratedRunnerRepository implements RunnerRepository {

    private final GeneratedRunnerRepository delegate;

    public SpringGeneratedRunnerRepository(GeneratedRunnerRepository delegate) {
        this.delegate = delegate;
    }

    @Override
    public List<RunnerAggregateData> findAll() {
        return delegate.findAll();
    }
}
