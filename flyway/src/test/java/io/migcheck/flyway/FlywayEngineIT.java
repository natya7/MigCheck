package io.migcheck.flyway;

import io.migcheck.dialect.PostgresDialect;
import io.migcheck.testing.PostgresSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;

import static org.assertj.core.api.Assertions.assertThat;

class FlywayEngineIT {

    private final DataSource dataSource = PostgresSupport.dataSource();

    @BeforeEach
    void reset() {
        PostgresSupport.reset();
    }

    @Test
    void migrateCreatesUsersTable() throws Exception {
        FlywayEngine engine = new FlywayEngine("classpath:db/migration");

        engine.migrate(dataSource);

        assertThat(tableExists("users")).isTrue();
    }

    @Test
    void migrateToStopsAtTheTargetVersion() {
        FlywayEngine engine = new FlywayEngine("classpath:scenarios/safe_rename");

        engine.migrateTo(dataSource, "1");

        assertThat(new PostgresDialect().columnNames(dataSource, "public", "users"))
                .contains("name")
                .doesNotContain("full_name");
    }

    private boolean tableExists(String table) throws Exception {
        try (Connection conn = dataSource.getConnection();
             ResultSet rs = conn.getMetaData().getTables(null, "public", table, null)) {
            return rs.next();
        }
    }
}
