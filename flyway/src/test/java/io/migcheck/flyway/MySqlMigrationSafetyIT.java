package io.migcheck.flyway;

import io.migcheck.junit.Database;
import io.migcheck.junit.MigrationSafetyTest;
import io.migcheck.report.DynamicOutcome;
import io.migcheck.report.SafetyReport;
import io.migcheck.tester.MigrationScenario;
import io.migcheck.tester.MigrationTester;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

@MigrationSafetyTest(database = Database.MYSQL)
class MySqlMigrationSafetyIT {

    @Test
    void droppedColumnLosesDataThroughTheAnnotation(DataSource ds, MigrationTester tester) {
        MigrationScenario scenario = new MigrationScenario(
                "mysql drop added column",
                "classpath:scenarios/mysql_add_drop",
                null,
                "ALTER TABLE account DROP COLUMN note",
                DynamicOutcome.DATA_LOST);
        FlywayEngine engine = new FlywayEngine(scenario.migrationsLocation());

        SafetyReport report = tester.run(engine, ds, scenario);

        assertThat(report.isSafe()).isFalse();
        assertThat(report.dynamicResult().outcome()).isEqualTo(DynamicOutcome.DATA_LOST);
    }
}
