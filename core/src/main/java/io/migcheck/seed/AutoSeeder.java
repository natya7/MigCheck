package io.migcheck.seed;

import io.migcheck.dialect.Column;
import io.migcheck.dialect.Dialect;
import io.migcheck.dialect.ForeignKey;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class AutoSeeder {

    private final Dialect dialect;
    private final String schema;
    private final int rowsPerTable;

    public AutoSeeder(Dialect dialect, String schema, int rowsPerTable) {
        this.dialect = dialect;
        this.schema = schema;
        this.rowsPerTable = rowsPerTable;
    }

    public void seed(DataSource dataSource) {
        seed(dataSource, Set.of());
    }

    public void seed(DataSource dataSource, Set<String> ignoredTables) {
        List<String> tables = new ArrayList<>(dialect.tableNames(dataSource, schema));
        tables.removeAll(ignoredTables);
        List<ForeignKey> foreignKeys = dialect.foreignKeys(dataSource, schema);
        List<String> order = new DependencyGraph(tables, foreignKeys).topologicalOrder();
        Map<String, List<Object>> primaryKeys = new HashMap<>();
        for (String table : order) {
            seedTable(dataSource, table, foreignKeys, primaryKeys);
        }
    }

    private void seedTable(DataSource dataSource, String table, List<ForeignKey> foreignKeys,
                           Map<String, List<Object>> primaryKeys) {
        List<Column> columns = dialect.columns(dataSource, schema, table);
        List<String> pkColumns = dialect.primaryKeyColumns(dataSource, schema, table);
        Map<String, String> fkTargets = new HashMap<>();
        for (ForeignKey fk : foreignKeys) {
            if (fk.table().equals(table)) {
                fkTargets.put(fk.column(), fk.referencedTable());
            }
        }
        List<Object> insertedKeys = new ArrayList<>();
        for (int row = 0; row < rowsPerTable; row++) {
            Map<String, Object> values = new LinkedHashMap<>();
            for (Column column : columns) {
                values.put(column.name(), valueFor(column, row, fkTargets, primaryKeys));
            }
            insertRow(dataSource, table, values);
            if (pkColumns.size() == 1) {
                insertedKeys.add(values.get(pkColumns.get(0)));
            }
        }
        primaryKeys.put(table, insertedKeys);
    }

    private Object valueFor(Column column, int row, Map<String, String> fkTargets,
                            Map<String, List<Object>> primaryKeys) {
        String referenced = fkTargets.get(column.name());
        if (referenced != null) {
            List<Object> parentKeys = primaryKeys.getOrDefault(referenced, List.of());
            return parentKeys.isEmpty() ? null : parentKeys.get(row % parentKeys.size());
        }
        return dialect.sampleValue(column.dataType(), row + 1);
    }

    private void insertRow(DataSource dataSource, String table, Map<String, Object> values) {
        String columnList = values.keySet().stream()
                .map(name -> "\"" + name + "\"")
                .collect(Collectors.joining(", "));
        String placeholders = values.values().stream()
                .map(v -> "?")
                .collect(Collectors.joining(", "));
        String sql = "INSERT INTO \"" + schema + "\".\"" + table + "\" ("
                + columnList + ") VALUES (" + placeholders + ")";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            int index = 1;
            for (Object value : values.values()) {
                ps.setObject(index++, value);
            }
            ps.execute();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to seed table " + table, e);
        }
    }
}
