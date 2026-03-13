/* Copyright 2026 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.persistence.domain.model;

import one.chartsy.SymbolGroupContent;
import one.chartsy.persistence.domain.SymbolGroupAggregateData;
import org.springframework.context.ApplicationEventPublisher;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class JdbcSymbolGroupRepository implements SymbolGroupRepository {

    private static final String NEXT_ID = "SELECT NEXT VALUE FOR ONE_SYMBOL_GROUP_IDS";
    private static final String SELECT_ROOTS = """
            SELECT id, parent_group_id, name, content_type, stereotype, data_provider_descriptor, created, last_modified
            FROM ONE_SYMBOL_GROUPS
            WHERE parent_group_id IS NULL
            ORDER BY id
            """;
    private static final String SELECT_BY_PARENT = """
            SELECT id, parent_group_id, name, content_type, stereotype, data_provider_descriptor, created, last_modified
            FROM ONE_SYMBOL_GROUPS
            WHERE parent_group_id = ?
            ORDER BY id
            """;
    private static final String INSERT = """
            INSERT INTO ONE_SYMBOL_GROUPS (
                id,
                parent_group_id,
                name,
                content_type,
                stereotype,
                data_provider_descriptor,
                created,
                last_modified
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;
    private static final String UPDATE = """
            UPDATE ONE_SYMBOL_GROUPS
            SET parent_group_id = ?,
                name = ?,
                content_type = ?,
                stereotype = ?,
                data_provider_descriptor = ?,
                last_modified = ?
            WHERE id = ?
            """;
    private static final String DELETE = "DELETE FROM ONE_SYMBOL_GROUPS WHERE id = ?";

    private final DataSource dataSource;
    private final ApplicationEventPublisher eventPublisher;

    public JdbcSymbolGroupRepository(DataSource dataSource, ApplicationEventPublisher eventPublisher) {
        this.dataSource = dataSource;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public List<SymbolGroupAggregateData> findByParentGroupId(Long parentId) {
        var sql = (parentId == null) ? SELECT_ROOTS : SELECT_BY_PARENT;
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement(sql)) {
            if (parentId != null)
                statement.setLong(1, parentId);

            try (var resultSet = statement.executeQuery()) {
                var groups = new ArrayList<SymbolGroupAggregateData>();
                while (resultSet.next())
                    groups.add(mapGroup(resultSet));
                return groups;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot load symbol groups", e);
        }
    }

    @Override
    public SymbolGroupAggregateData save(SymbolGroupAggregateData group) {
        try (var connection = dataSource.getConnection()) {
            if (group.getId() == null)
                insert(connection, group);
            else
                update(connection, group);

            eventPublisher.publishEvent(group);
            return group;
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot save symbol group", e);
        }
    }

    @Override
    public SymbolGroupAggregateData saveAndFlush(SymbolGroupAggregateData group) {
        return save(group);
    }

    @Override
    public void delete(SymbolGroupAggregateData group) {
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement(DELETE)) {
            statement.setLong(1, group.getId());
            int deletedRows = statement.executeUpdate();
            if (deletedRows != 1)
                throw new IllegalStateException("Expected to delete one symbol group, deleted " + deletedRows);

            group.markRemoved();
            eventPublisher.publishEvent(group);
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot delete symbol group", e);
        }
    }

    @Override
    public void deleteAll(Iterable<? extends SymbolGroupAggregateData> groups) {
        for (var group : groups)
            delete(group);
    }

    private void insert(Connection connection, SymbolGroupAggregateData group) throws SQLException {
        group.setId(nextId(connection));
        group.markNewlyCreated();
        try (var statement = connection.prepareStatement(INSERT)) {
            bindNullableLong(statement, 1, group.getId());
            bindNullableLong(statement, 2, group.getParentGroupId());
            statement.setString(3, group.getName());
            statement.setInt(4, group.getContentType().ordinal());
            statement.setString(5, group.getStereotype());
            statement.setString(6, group.getDataProviderDescriptor());
            statement.setObject(7, group.getCreated());
            statement.setObject(8, group.getLastModified());
            int insertedRows = statement.executeUpdate();
            if (insertedRows != 1)
                throw new IllegalStateException("Expected to insert one symbol group, inserted " + insertedRows);
        }
    }

    private void update(Connection connection, SymbolGroupAggregateData group) throws SQLException {
        group.markModified();
        try (var statement = connection.prepareStatement(UPDATE)) {
            bindNullableLong(statement, 1, group.getParentGroupId());
            statement.setString(2, group.getName());
            statement.setInt(3, group.getContentType().ordinal());
            statement.setString(4, group.getStereotype());
            statement.setString(5, group.getDataProviderDescriptor());
            statement.setObject(6, group.getLastModified());
            statement.setLong(7, group.getId());
            int updatedRows = statement.executeUpdate();
            if (updatedRows != 1)
                throw new IllegalStateException("Expected to update one symbol group, updated " + updatedRows);
        }
    }

    private long nextId(Connection connection) throws SQLException {
        try (var statement = connection.prepareStatement(NEXT_ID);
             var resultSet = statement.executeQuery()) {
            if (!resultSet.next())
                throw new IllegalStateException("Sequence ONE_SYMBOL_GROUP_IDS did not return a value");
            return resultSet.getLong(1);
        }
    }

    private SymbolGroupAggregateData mapGroup(ResultSet resultSet) throws SQLException {
        var group = new SymbolGroupAggregateData();
        group.setId(resultSet.getLong("id"));
        group.setParentGroupId(resultSet.getObject("parent_group_id", Long.class));
        group.setName(resultSet.getString("name"));
        group.setContentType(readContentType(resultSet.getString("content_type")));
        group.setStereotype(resultSet.getString("stereotype"));
        group.setDataProviderDescriptor(resultSet.getString("data_provider_descriptor"));
        group.setCreated(resultSet.getObject("created", LocalDateTime.class));
        group.setLastModified(resultSet.getObject("last_modified", LocalDateTime.class));
        return group;
    }

    private void bindNullableLong(PreparedStatement statement, int index, Long value) throws SQLException {
        if (value == null)
            statement.setNull(index, Types.BIGINT);
        else
            statement.setLong(index, value);
    }

    private SymbolGroupContent.Type readContentType(String value) {
        try {
            int ordinal = Integer.parseInt(value);
            return SymbolGroupContent.Type.values()[ordinal];
        } catch (NumberFormatException ignored) {
            return SymbolGroupContent.Type.valueOf(value);
        }
    }
}
