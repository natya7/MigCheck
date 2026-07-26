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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
        String sql = "SELECT column_name, data_type, is_nullable, is_identity, column_default, "
                + "character_maximum_length "
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
                    String columnDefault = rs.getString("column_default");
                    boolean generated = "YES".equals(rs.getString("is_identity"))
                            || (columnDefault != null && columnDefault.startsWith("nextval"));
                    int length = rs.getInt("character_maximum_length");
                    Integer maxLength = rs.wasNull() ? null : length;
                    columns.add(new Column(rs.getString("column_name"),
                            rs.getString("data_type"),
                            "YES".equals(rs.getString("is_nullable")),
                            generated,
                            maxLength));
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
    public String columnType(DataSource dataSource, String schema, String table, String column) {
        String sql = "SELECT data_type, character_maximum_length, numeric_precision, numeric_scale "
                + "FROM information_schema.columns "
                + "WHERE table_schema = ? AND table_name = ? AND column_name = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, schema);
            ps.setString(2, table);
            ps.setString(3, column);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new RuntimeException("No such column: " + table + "." + column);
                }
                String dataType = rs.getString("data_type");
                int length = rs.getInt("character_maximum_length");
                if (!rs.wasNull()) {
                    return dataType + "(" + length + ")";
                }
                if ("numeric".equals(dataType) || "decimal".equals(dataType)) {
                    int precision = rs.getInt("numeric_precision");
                    int scale = rs.getInt("numeric_scale");
                    return dataType + "(" + precision + "," + scale + ")";
                }
                return dataType;
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
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
    public List<CompositeForeignKey> compositeForeignKeys(DataSource dataSource, String schema) {
        String sql = "SELECT rc.constraint_name AS cname, kcu.table_name AS child_table, "
                + "kcu.column_name AS child_column, ccu.table_name AS parent_table, "
                + "ccu.column_name AS parent_column "
                + "FROM information_schema.referential_constraints rc "
                + "JOIN information_schema.key_column_usage kcu "
                + "  ON kcu.constraint_name = rc.constraint_name "
                + " AND kcu.constraint_schema = rc.constraint_schema "
                + "JOIN information_schema.key_column_usage ccu "
                + "  ON ccu.constraint_name = rc.unique_constraint_name "
                + " AND ccu.constraint_schema = rc.unique_constraint_schema "
                + " AND ccu.ordinal_position = kcu.position_in_unique_constraint "
                + "WHERE kcu.table_schema = ? "
                + "ORDER BY rc.constraint_name, kcu.ordinal_position";
        Map<String, String> childTables = new LinkedHashMap<>();
        Map<String, String> parentTables = new LinkedHashMap<>();
        Map<String, List<String>> childColumns = new LinkedHashMap<>();
        Map<String, List<String>> parentColumns = new LinkedHashMap<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, schema);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String name = rs.getString("cname");
                    childTables.put(name, rs.getString("child_table"));
                    parentTables.put(name, rs.getString("parent_table"));
                    childColumns.computeIfAbsent(name, k -> new ArrayList<>())
                            .add(rs.getString("child_column"));
                    parentColumns.computeIfAbsent(name, k -> new ArrayList<>())
                            .add(rs.getString("parent_column"));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        List<CompositeForeignKey> result = new ArrayList<>();
        for (String name : childColumns.keySet()) {
            if (childColumns.get(name).size() > 1) {
                result.add(new CompositeForeignKey(childTables.get(name), parentTables.get(name),
                        childColumns.get(name), parentColumns.get(name)));
            }
        }
        return result;
    }

    @Override
    public Object sampleValue(String dataType, int seed) {
        return switch (dataType) {
            case "bigint" -> (long) seed;
            case "integer", "smallint" -> seed;
            case "boolean" -> seed % 2 == 0;
            case "numeric", "real", "double precision" -> seed + 0.3457;
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
