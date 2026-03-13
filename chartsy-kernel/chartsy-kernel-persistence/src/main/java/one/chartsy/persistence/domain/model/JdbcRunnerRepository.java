/* Copyright 2026 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.persistence.domain.model;

import one.chartsy.persistence.domain.RunnerAggregateData;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class JdbcRunnerRepository implements RunnerRepository {

    private static final String SELECT_ALL = """
            SELECT id, runner_key, name, type, status, supported_types, top_component
            FROM ONE_RUNNERS
            ORDER BY id
            """;

    private final DataSource dataSource;

    public JdbcRunnerRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public List<RunnerAggregateData> findAll() {
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement(SELECT_ALL);
             var resultSet = statement.executeQuery()) {
            var runners = new ArrayList<RunnerAggregateData>();
            while (resultSet.next())
                runners.add(mapRunner(resultSet));
            return runners;
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot load runners", e);
        }
    }

    private RunnerAggregateData mapRunner(ResultSet resultSet) throws SQLException {
        var runner = new RunnerAggregateData();
        runner.setId(resultSet.getLong("id"));
        runner.setKey(resultSet.getString("runner_key"));
        runner.setName(resultSet.getString("name"));
        runner.setType(resultSet.getString("type"));
        runner.setStatus(RunnerAggregateData.Status.valueOf(resultSet.getString("status")));
        runner.setSupportedTypes(resultSet.getString("supported_types"));
        runner.setTopComponent(resultSet.getString("top_component"));
        return runner;
    }
}
