package io.migcheck.liquibase;

import io.migcheck.engine.MigrationEngine;
import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Set;

public class LiquibaseEngine implements MigrationEngine {

    private final String changelog;

    public LiquibaseEngine(String changelog) {
        this.changelog = changelog;
    }

    @Override
    public void migrate(DataSource dataSource) {
        try (Connection conn = dataSource.getConnection()) {
            Database database = DatabaseFactory.getInstance()
                    .findCorrectDatabaseImplementation(new JdbcConnection(conn));
            try (Liquibase liquibase =
                         new Liquibase(changelog, new ClassLoaderResourceAccessor(), database)) {
                liquibase.update(new Contexts(), new LabelExpression());
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to run Liquibase update", e);
        }
    }

    @Override
    public void rollback(DataSource dataSource, String rollbackSql) {
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try (Statement st = conn.createStatement()) {
                st.execute(rollbackSql);
                st.execute("DELETE FROM databasechangelog WHERE orderexecuted = "
                        + "(SELECT MAX(orderexecuted) FROM databasechangelog)");
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
        try (Connection conn = dataSource.getConnection()) {
            Database database = DatabaseFactory.getInstance()
                    .findCorrectDatabaseImplementation(new JdbcConnection(conn));
            try (Liquibase liquibase =
                         new Liquibase(changelog, new ClassLoaderResourceAccessor(), database)) {
                liquibase.dropAll();
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to drop all objects", e);
        }
    }

    @Override
    public Set<String> metadataTables() {
        return Set.of("databasechangelog", "databasechangeloglock");
    }
}
