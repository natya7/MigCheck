package io.migcheck.dialect;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class PostgresDialect implements Dialect {

    @Override
    public List<String> tableNames(DataSource dataSource, String schema) {
        String sql = "SELECT table_name FROM information_schema.tables "
                + "WHERE table_schema = ? AND table_type = 'BASE TABLE' "
                + "ORDER BY table_name";
        return queryOneParam(dataSource, sql, schema);
    }

    @Override
    public List<String> columnNames(DataSource dataSource, String schema, String table) {
        List<String> names = new ArrayList<>();
        for (Column column : columns(dataSource, schema, table)) {
            names.add(column.name());
        }
        return names;
    }

    @Override
    public List<Column> columns(DataSource dataSource, String schema, String table) {
        String sql = "SELECT column_name, data_type, is_nullable "
                + "FROM information_schema.columns "
                + "WHERE table_schema = ? AND table_name = ? "
                + "ORDER BY ordinal_position";
        List<Column> columns = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, schema);
            ps.setString(2, table);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    columns.add(new Column(rs.getString("column_name"),
                            rs.getString("data_type"),
                            "YES".equals(rs.getString("is_nullable"))));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return columns;
    }

    @Override
    public List<String> primaryKeyColumns(DataSource dataSource, String schema, String table) {
        String sql = "SELECT kcu.column_name "
                + "FROM information_schema.table_constraints tc "
                + "JOIN information_schema.key_column_usage kcu "
                + "  ON tc.constraint_name = kcu.constraint_name "
                + " AND tc.table_schema = kcu.table_schema "
                + "WHERE tc.constraint_type = 'PRIMARY KEY' "
                + " AND tc.table_schema = ? AND tc.table_name = ? "
                + "ORDER BY kcu.ordinal_position";
        List<String> pk = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, schema);
            ps.setString(2, table);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    pk.add(rs.getString(1));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return pk;
    }

    @Override
    public List<ForeignKey> foreignKeys(DataSource dataSource, String schema) {
        String sql = "SELECT tc.table_name, ccu.table_name AS referenced_table, "
                + "       kcu.column_name "
                + "FROM information_schema.table_constraints tc "
                + "JOIN information_schema.key_column_usage kcu "
                + "  ON tc.constraint_name = kcu.constraint_name "
                + " AND tc.table_schema = kcu.table_schema "
                + "JOIN information_schema.constraint_column_usage ccu "
                + "  ON ccu.constraint_name = tc.constraint_name "
                + " AND ccu.table_schema = tc.table_schema "
                + "WHERE tc.constraint_type = 'FOREIGN KEY' AND tc.table_schema = ?";
        List<ForeignKey> fks = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, schema);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    fks.add(new ForeignKey(rs.getString(1), rs.getString(2), rs.getString(3)));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return fks;
    }

    @Override
    public Object sampleValue(String dataType, int seed) {
        return switch (dataType) {
            case "bigint" -> (long) seed;
            case "integer", "smallint" -> seed;
            case "boolean" -> seed % 2 == 0;
            case "numeric", "real", "double precision" -> (double) seed;
            case "date" -> Date.valueOf(LocalDate.of(2020, 1, 1).plusDays(seed));
            case "timestamp without time zone", "timestamp with time zone" ->
                    Timestamp.valueOf(LocalDateTime.of(2020, 1, 1, 0, 0).plusMinutes(seed));
            case "uuid" -> new UUID(0L, seed);
            default -> "v" + seed;
        };
    }

    private List<String> queryOneParam(DataSource ds, String sql, String param) {
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, param);
            return readSingleColumn(ps);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private List<String> readSingleColumn(PreparedStatement ps) throws SQLException {
        List<String> out = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                out.add(rs.getString(1));
            }
        }
        return out;
    }
}
