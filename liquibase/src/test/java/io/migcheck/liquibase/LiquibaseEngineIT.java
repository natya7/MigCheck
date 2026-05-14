package io.migcheck.liquibase;

import io.migcheck.testing.PostgresSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;

import static org.assertj.core.api.Assertions.assertThat;

class LiquibaseEngineIT {

    private final DataSource dataSource = PostgresSupport.dataSource();

    @BeforeEach
    void reset() {
        PostgresSupport.reset();
    }

    @Test
    void migrateAppliesChangelog() throws Exception {
        LiquibaseEngine engine = new LiquibaseEngine("changelogs/users_score/changelog.xml");

        engine.migrate(dataSource);

        assertThat(tableExists("users")).isTrue();
    }

    private boolean tableExists(String table) throws Exception {
        try (Connection conn = dataSource.getConnection();
             ResultSet rs = conn.getMetaData().getTables(null, "public", table, null)) {
            return rs.next();
        }
    }
}
