package io.migcheck.flyway;

import io.migcheck.junit.MigrationSafetyTest;
import io.migcheck.report.DynamicOutcome;
import io.migcheck.report.SafetyReport;
import io.migcheck.tester.MigrationScenario;
import io.migcheck.tester.MigrationTester;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.sql.DataSource;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@MigrationSafetyTest
class MigrationSafetyExtensionIT {

    static Stream<MigrationScenario> scenarios() {
        return Stream.of(
                new MigrationScenario("safe table rename",
                        "classpath:scenarios/safe_rename_table",
                        "INSERT INTO accounts (id, name) VALUES (1, 'Ada')",
                        "ALTER TABLE accounts RENAME TO users",
                        DynamicOutcome.PRESERVED),
                new MigrationScenario("column dropped on rollback",
                        "classpath:scenarios/drop_added_column",
                        "INSERT INTO users (id, name, score) VALUES (1, 'Ada', 100)",
                        "ALTER TABLE users DROP COLUMN score",
                        DynamicOutcome.DATA_LOST));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("scenarios")
    void checksRollbackSafety(MigrationScenario scenario, MigrationTester tester, DataSource ds) {
        SafetyReport report = tester.run(new FlywayEngine(scenario.migrationsLocation()), ds, scenario);

        assertThat(report.dynamicResult().outcome()).isEqualTo(scenario.expectedOutcome());
    }
}
