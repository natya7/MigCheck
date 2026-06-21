package io.migcheck.testing;

import com.mysql.cj.jdbc.MysqlDataSource;
import org.testcontainers.containers.MySQLContainer;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public final class MySqlSupport {

    private static final MySQLContainer<?> CONTAINER = new MySQLContainer<>("mysql:8.0");

    static {
        CONTAINER.start();
    }

    private MySqlSupport() {
    }

    public static String schema() {
        return CONTAINER.getDatabaseName();
    }

    public static DataSource dataSource() {
        MysqlDataSource ds = new MysqlDataSource();
        ds.setUrl(CONTAINER.getJdbcUrl());
        ds.setUser(CONTAINER.getUsername());
        ds.setPassword(CONTAINER.getPassword());
        return ds;
    }

    public static void reset() {
        try (Connection conn = dataSource().getConnection();
             Statement st = conn.createStatement()) {
            st.execute("SET FOREIGN_KEY_CHECKS = 0");
            List<String> tables = new ArrayList<>();
            try (ResultSet rs = st.executeQuery(
                    "SELECT table_name FROM information_schema.tables WHERE table_schema = '"
                            + schema() + "'")) {
                while (rs.next()) {
                    tables.add(rs.getString(1));
                }
            }
            for (String table : tables) {
                st.execute("DROP TABLE IF EXISTS `" + table + "`");
            }
            st.execute("SET FOREIGN_KEY_CHECKS = 1");
        } catch (SQLException e) {
            throw new RuntimeException("Failed to reset schema", e);
        }
    }
}
