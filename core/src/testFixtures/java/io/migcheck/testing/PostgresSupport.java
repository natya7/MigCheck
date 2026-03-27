package io.migcheck.testing;

import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public final class PostgresSupport {

    private static final PostgreSQLContainer<?> CONTAINER =
            new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        CONTAINER.start();
    }

    private PostgresSupport() {
    }

    public static DataSource dataSource() {
        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setUrl(CONTAINER.getJdbcUrl());
        ds.setUser(CONTAINER.getUsername());
        ds.setPassword(CONTAINER.getPassword());
        return ds;
    }

    public static void reset() {
        try (Connection conn = dataSource().getConnection();
             Statement st = conn.createStatement()) {
            st.execute("DROP SCHEMA public CASCADE; CREATE SCHEMA public;");
        } catch (SQLException e) {
            throw new RuntimeException("Failed to reset schema", e);
        }
    }
}
