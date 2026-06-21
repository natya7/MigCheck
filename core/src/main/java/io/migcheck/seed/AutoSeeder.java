package io.migcheck.seed;

import io.migcheck.dialect.Column;
import io.migcheck.dialect.CompositeForeignKey;
import io.migcheck.dialect.Dialect;
import io.migcheck.dialect.ForeignKey;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
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
        List<CompositeForeignKey> compositeKeys = dialect.compositeForeignKeys(dataSource, schema);
        List<String> order = new DependencyGraph(tables, foreignKeys).topologicalOrder();
        Map<String, List<Object>> primaryKeys = new HashMap<>();
        Map<String, List<Map<String, Object>>> primaryTuples = new HashMap<>();
        for (String table : order) {
            seedTable(dataSource, table, foreignKeys, compositeKeys, primaryKeys, primaryTuples);
        }
    }

    private void seedTable(DataSource dataSource, String table, List<ForeignKey> foreignKeys,
                           List<CompositeForeignKey> compositeKeys,
                           Map<String, List<Object>> primaryKeys,
                           Map<String, List<Map<String, Object>>> primaryTuples) {
        List<Column> columns = dialect.columns(dataSource, schema, table);
        List<String> pkColumns = dialect.primaryKeyColumns(dataSource, schema, table);
        Map<String, String> fkTargets = new HashMap<>();
        for (ForeignKey fk : foreignKeys) {
            if (fk.table().equals(table)) {
                fkTargets.put(fk.column(), fk.referencedTable());
            }
        }
        List<CompositeForeignKey> composites = new ArrayList<>();
        Set<String> compositeColumns = new HashSet<>();
        for (CompositeForeignKey cfk : compositeKeys) {
            if (cfk.childTable().equals(table)) {
                composites.add(cfk);
                compositeColumns.addAll(cfk.childColumns());
            }
        }
        String generatedPk = pkColumns.size() == 1 && isGenerated(columns, pkColumns.get(0))
                ? pkColumns.get(0) : null;
        List<Object> insertedKeys = new ArrayList<>();
        List<Map<String, Object>> insertedTuples = new ArrayList<>();
        for (int row = 0; row < rowsPerTable; row++) {
            Map<String, Object> values = new LinkedHashMap<>();
            for (CompositeForeignKey cfk : composites) {
                List<Map<String, Object>> parent = primaryTuples.getOrDefault(cfk.parentTable(), List.of());
                if (!parent.isEmpty()) {
                    Map<String, Object> parentRow = parent.get(row % parent.size());
                    for (int i = 0; i < cfk.childColumns().size(); i++) {
                        values.put(cfk.childColumns().get(i), parentRow.get(cfk.parentColumns().get(i)));
                    }
                }
            }
            for (Column column : columns) {
                if (column.generated() || compositeColumns.contains(column.name())) {
                    continue;
                }
                if (row == 0 && column.nullable()
                        && !pkColumns.contains(column.name())
                        && !fkTargets.containsKey(column.name())) {
                    values.put(column.name(), null);
                } else {
                    values.put(column.name(), valueFor(column, row, fkTargets, primaryKeys));
                }
            }
            Object generatedKey = insertRow(dataSource, table, values, generatedPk);
            if (pkColumns.size() == 1) {
                insertedKeys.add(generatedPk != null ? generatedKey : values.get(pkColumns.get(0)));
            }
            Map<String, Object> tuple = new LinkedHashMap<>();
            for (String pk : pkColumns) {
                tuple.put(pk, generatedPk != null && pk.equals(generatedPk)
                        ? generatedKey : values.get(pk));
            }
            insertedTuples.add(tuple);
        }
        primaryKeys.put(table, insertedKeys);
        primaryTuples.put(table, insertedTuples);
    }

    private boolean isGenerated(List<Column> columns, String name) {
        return columns.stream().anyMatch(c -> c.name().equals(name) && c.generated());
    }

    private Object valueFor(Column column, int row, Map<String, String> fkTargets,
                            Map<String, List<Object>> primaryKeys) {
        String referenced = fkTargets.get(column.name());
        if (referenced != null) {
            List<Object> parentKeys = primaryKeys.getOrDefault(referenced, List.of());
            return parentKeys.isEmpty() ? null : parentKeys.get(row % parentKeys.size());
        }
        Object value = dialect.sampleValue(column.dataType(), row + 1);
        if (value instanceof String s) {
            return fitString(s, column.maxLength());
        }
        return value;
    }

    private String fitString(String base, Integer maxLength) {
        int target = maxLength != null ? Math.min(maxLength, 64) : 24;
        if (base.length() >= target) {
            return base.substring(0, target);
        }
        StringBuilder sb = new StringBuilder(base);
        while (sb.length() < target) {
            sb.append('x');
        }
        return sb.toString();
    }

    private Object insertRow(DataSource dataSource, String table, Map<String, Object> values,
                             String generatedPk) {
        String sql = buildInsert(table, values);
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = generatedPk != null
                     ? conn.prepareStatement(sql, new String[]{generatedPk})
                     : conn.prepareStatement(sql)) {
            int index = 1;
            for (Object value : values.values()) {
                ps.setObject(index++, value);
            }
            ps.execute();
            if (generatedPk == null) {
                return null;
            }
            try (ResultSet keys = ps.getGeneratedKeys()) {
                return keys.next() ? keys.getObject(1) : null;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to seed table " + table, e);
        }
    }

    private String buildInsert(String table, Map<String, Object> values) {
        String qualified = dialect.quoteIdentifier(schema) + "." + dialect.quoteIdentifier(table);
        if (values.isEmpty()) {
            return dialect.emptyInsert(qualified);
        }
        String columnList = values.keySet().stream()
                .map(dialect::quoteIdentifier)
                .collect(Collectors.joining(", "));
        String placeholders = values.values().stream()
                .map(v -> "?")
                .collect(Collectors.joining(", "));
        return "INSERT INTO " + qualified + " (" + columnList + ") VALUES (" + placeholders + ")";
    }
}
