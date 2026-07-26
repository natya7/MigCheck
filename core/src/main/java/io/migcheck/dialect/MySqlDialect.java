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

public class MySqlDialect implements Dialect {

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
        String sql = "SELECT column_name, data_type, is_nullable, extra, "
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
                    String extra = rs.getString("extra");
                    boolean generated = extra != null
                            && (extra.toLowerCase().contains("auto_increment")
                            || extra.toLowerCase().contains("generated"));
                    long length = rs.getLong("character_maximum_length");
                    Integer maxLength = rs.wasNull() ? null : (int) Math.min(length, 65535);
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
        String sql = "SELECT column_name FROM information_schema.key_column_usage "
                + "WHERE table_schema = ? AND table_name = ? AND constraint_name = 'PRIMARY' "
                + "ORDER BY ordinal_position";
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
        String sql = "SELECT column_type FROM information_schema.columns "
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
                return rs.getString(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<ForeignKey> foreignKeys(DataSource dataSource, String schema) {
        String sql = "SELECT table_name, referenced_table_name, column_name "
                + "FROM information_schema.key_column_usage "
                + "WHERE table_schema = ? AND referenced_table_name IS NOT NULL";
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
        String sql = "SELECT constraint_name, table_name, column_name, "
                + "referenced_table_name, referenced_column_name "
                + "FROM information_schema.key_column_usage "
                + "WHERE table_schema = ? AND referenced_table_name IS NOT NULL "
                + "ORDER BY constraint_name, ordinal_position";
        Map<String, String> childTables = new LinkedHashMap<>();
        Map<String, String> parentTables = new LinkedHashMap<>();
        Map<String, List<String>> childColumns = new LinkedHashMap<>();
        Map<String, List<String>> parentColumns = new LinkedHashMap<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, schema);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String name = rs.getString("constraint_name");
                    childTables.put(name, rs.getString("table_name"));
                    parentTables.put(name, rs.getString("referenced_table_name"));
                    childColumns.computeIfAbsent(name, k -> new ArrayList<>())
                            .add(rs.getString("column_name"));
                    parentColumns.computeIfAbsent(name, k -> new ArrayList<>())
                            .add(rs.getString("referenced_column_name"));
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
    public String quoteIdentifier(String identifier) {
        return "`" + identifier + "`";
    }

    @Override
    public String emptyInsert(String qualifiedTable) {
        return "INSERT INTO " + qualifiedTable + " () VALUES ()";
    }

    @Override
    public Object sampleValue(String dataType, int seed) {
        return switch (dataType) {
            case "bigint" -> (long) seed;
            case "int", "integer", "smallint", "mediumint", "tinyint" -> seed;
            case "decimal", "double", "float" -> seed + 0.3457;
            case "date" -> Date.valueOf(LocalDate.of(2020, 1, 1).plusDays(seed));
            case "datetime", "timestamp" ->
                    Timestamp.valueOf(LocalDateTime.of(2020, 1, 1, 0, 0).plusMinutes(seed));
            default -> "v" + seed;
        };
    }

    private List<String> queryOneParam(DataSource dataSource, String sql, String param) {
        List<String> out = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, param);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(rs.getString(1));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return out;
    }
}
