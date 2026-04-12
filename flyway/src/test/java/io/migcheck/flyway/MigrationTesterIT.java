package io.migcheck.flyway;

import io.migcheck.compare.DataComparator;
import io.migcheck.dialect.PostgresDialect;
import io.migcheck.report.DynamicOutcome;
import io.migcheck.report.SafetyReport;
import io.migcheck.snapshot.SnapshotEngine;
import io.migcheck.tester.MigrationScenario;
import io.migcheck.tester.MigrationTester;
import io.migcheck.testing.PostgresSupport;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.sql.DataSource;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class MigrationTesterIT {

    private final DataSource ds = PostgresSupport.dataSource();
    private final MigrationTester tester =
            new MigrationTester(
                    new SnapshotEngine(new PostgresDialect(), "public",
                            Set.of("flyway_schema_history")),
                    new DataComparator());

    static Stream<MigrationScenario> scenarios() {
        return Stream.of(
                new MigrationScenario("safe column rename",
                        "classpath:scenarios/safe_rename",
                        "INSERT INTO users (id, full_name) VALUES (1, 'Ada'), (2, 'Linus')",
                        "ALTER TABLE users RENAME COLUMN full_name TO name",
                        DynamicOutcome.PRESERVED),
                new MigrationScenario("destructive table drop on rollback",
                        "classpath:scenarios/destructive_drop",
                        "INSERT INTO audit_log (id, message) VALUES (1, 'created')",
                        "DROP TABLE audit_log",
                        DynamicOutcome.DATA_LOST),
                new MigrationScenario("lossy type narrowing on rollback",
                        "classpath:scenarios/lossy_narrowing",
                        "INSERT INTO users (id, name) VALUES (1, 'Alexandria the Great')",
                        "ALTER TABLE users ALTER COLUMN name TYPE VARCHAR(10) USING LEFT(name, 10)",
                        DynamicOutcome.DATA_LOST));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("scenarios")
    void detectsRollbackSafety(MigrationScenario scenario) {
        FlywayEngine engine = new FlywayEngine(scenario.migrationsLocation());

        SafetyReport report = tester.run(engine, ds, scenario);

        assertThat(report.dynamicResult().outcome()).isEqualTo(scenario.expectedOutcome());
    }
}
