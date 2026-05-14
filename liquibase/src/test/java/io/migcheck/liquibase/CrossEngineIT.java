package io.migcheck.liquibase;

import io.migcheck.compare.DataComparator;
import io.migcheck.dialect.PostgresDialect;
import io.migcheck.flyway.FlywayEngine;
import io.migcheck.report.DynamicOutcome;
import io.migcheck.seed.AutoSeeder;
import io.migcheck.snapshot.SnapshotEngine;
import io.migcheck.testing.PostgresSupport;
import io.migcheck.tester.MigrationScenario;
import io.migcheck.tester.MigrationTester;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

class CrossEngineIT {

    private static final String SEED =
            "INSERT INTO users (id, name, score) VALUES (1, 'Ada', 100)";
    private static final String ROLLBACK = "ALTER TABLE users DROP COLUMN score";

    private final DataSource ds = PostgresSupport.dataSource();
    private final MigrationTester tester = new MigrationTester(
            new SnapshotEngine(new PostgresDialect(), "public"),
            new DataComparator(),
            new AutoSeeder(new PostgresDialect(), "public", 3));

    @Test
    void flywayAndLiquibaseProduceTheSameVerdict() {
        MigrationScenario flyway = new MigrationScenario("flyway drop column",
                "classpath:scenarios/users_score", SEED, ROLLBACK, DynamicOutcome.DATA_LOST);
        MigrationScenario liquibase = new MigrationScenario("liquibase drop column",
                "changelogs/users_score/changelog.xml", SEED, ROLLBACK, DynamicOutcome.DATA_LOST);

        DynamicOutcome flywayOutcome = tester
                .run(new FlywayEngine(flyway.migrationsLocation()), ds, flyway)
                .dynamicResult().outcome();
        DynamicOutcome liquibaseOutcome = tester
                .run(new LiquibaseEngine(liquibase.migrationsLocation()), ds, liquibase)
                .dynamicResult().outcome();

        assertThat(flywayOutcome).isEqualTo(liquibaseOutcome).isEqualTo(DynamicOutcome.DATA_LOST);
    }
}
