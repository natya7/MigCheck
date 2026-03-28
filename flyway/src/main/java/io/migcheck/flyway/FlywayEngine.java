package io.migcheck.flyway;

import io.migcheck.engine.MigrationEngine;
import org.flywaydb.core.Flyway;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class FlywayEngine implements MigrationEngine {

    private final String migrationsLocation;

    public FlywayEngine(String migrationsLocation) {
        this.migrationsLocation = migrationsLocation;
    }

    @Override
    public void migrate(DataSource dataSource) {
        Flyway.configure()
                .dataSource(dataSource)
                .locations(migrationsLocation)
                .load()
                .migrate();
    }

    @Override
    public void rollback(DataSource dataSource, String rollbackSql) {
        try (Connection conn = dataSource.getConnection();
             Statement st = conn.createStatement()) {
            st.execute(rollbackSql);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to execute rollback SQL", e);
        }
    }

    @Override
    public void clean(DataSource dataSource) {
        Flyway.configure()
                .dataSource(dataSource)
                .locations(migrationsLocation)
                .cleanDisabled(false)
                .load()
                .clean();
    }
}
