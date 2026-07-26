package io.migcheck.flyway;

import io.migcheck.engine.MigrationEngine;
import org.flywaydb.core.Flyway;

import java.util.Set;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class FlywayEngine implements MigrationEngine {

    private static final String HISTORY_TABLE = "flyway_schema_history";

    private final String migrationsLocation;

    public FlywayEngine(String migrationsLocation) {
        this.migrationsLocation = migrationsLocation;
    }

    @Override
    public Set<String> metadataTables() {
        return Set.of(HISTORY_TABLE);
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
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try (Statement st = conn.createStatement()) {
                for (String part : rollbackSql.split(";")) {
                    if (!part.isBlank()) {
                        st.execute(part);
                    }
                }
                st.execute("DELETE FROM " + HISTORY_TABLE + " WHERE installed_rank = "
                        + "(SELECT max_rank FROM (SELECT MAX(installed_rank) max_rank FROM "
                        + HISTORY_TABLE + ") AS latest)");
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
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
