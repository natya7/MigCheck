package io.migcheck.snapshot;

import io.migcheck.dialect.Dialect;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SnapshotEngine {

    private final Dialect dialect;
    private final String schema;

    public SnapshotEngine(Dialect dialect, String schema) {
        this.dialect = dialect;
        this.schema = schema;
    }

    public Snapshot capture(DataSource dataSource) {
        Map<String, TableSnapshot> tables = new LinkedHashMap<>();
        for (String table : dialect.tableNames(dataSource, schema)) {
            tables.put(table, captureTable(dataSource, table));
        }
        return new Snapshot(tables);
    }

    private TableSnapshot captureTable(DataSource dataSource, String table) {
        List<String> columns = dialect.columnNames(dataSource, schema, table);
        List<Map<String, Object>> rows = new ArrayList<>();
        String sql = "SELECT * FROM \"" + schema + "\".\"" + table + "\"";
        try (Connection conn = dataSource.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (String col : columns) {
                    row.put(col, rs.getObject(col));
                }
                rows.add(row);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to snapshot table " + table, e);
        }
        return new TableSnapshot(table, columns, rows);
    }
}
