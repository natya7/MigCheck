package io.migcheck.flyway;

import io.migcheck.compare.DataComparator;
import io.migcheck.dialect.PostgresDialect;
import io.migcheck.repair.PostgresRepairTemplates;
import io.migcheck.repair.RollbackRepair;
import io.migcheck.repair.RollbackRepairer;
import io.migcheck.report.DynamicOutcome;
import io.migcheck.report.SafetyReport;
import io.migcheck.seed.AutoSeeder;
import io.migcheck.snapshot.SnapshotEngine;
import io.migcheck.tester.MigrationScenario;
import io.migcheck.tester.MigrationTester;
import io.migcheck.testing.PostgresSupport;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class RollbackRepairIT {

    private final DataSource ds = PostgresSupport.dataSource();
    private final PostgresDialect dialect = new PostgresDialect();
    private final MigrationTester tester =
            new MigrationTester(new SnapshotEngine(dialect, "public"),
                    new DataComparator(),
                    new AutoSeeder(dialect, "public", 3));
    private final RollbackRepairer repairer =
            new RollbackRepairer(dialect, new PostgresRepairTemplates(), "public");

    @Test
    void repairsColumnDroppingRollback() {
        MigrationScenario scenario = new MigrationScenario("drop added column",
                "classpath:scenarios/drop_added_column", null,
                "ALTER TABLE users DROP COLUMN score",
                DynamicOutcome.DATA_LOST);
        FlywayEngine engine = new FlywayEngine(scenario.migrationsLocation());

        SafetyReport naive = tester.run(engine, ds, scenario);
        assertThat(naive.isSafe()).isFalse();

        Optional<RollbackRepair> repair = repairer.repair(ds, scenario.rollbackSql());
        assertThat(repair).isPresent();

        MigrationScenario repaired = new MigrationScenario("drop added column repaired",
                scenario.migrationsLocation(), null,
                repair.get().safeRollbackSql(),
                DynamicOutcome.PRESERVED);
        SafetyReport verified = tester.runWithRestore(engine, ds, repaired,
                repair.get().restoreSql());
        assertThat(verified.isSafe()).isTrue();
    }

    @Test
    void repairsTableDroppingRollback() {
        MigrationScenario scenario = new MigrationScenario("drop audit table",
                "classpath:scenarios/destructive_drop",
                "INSERT INTO audit_log (id, message) VALUES (1, 'created')",
                "DROP TABLE audit_log",
                DynamicOutcome.DATA_LOST);
        FlywayEngine engine = new FlywayEngine(scenario.migrationsLocation());

        SafetyReport naive = tester.run(engine, ds, scenario);
        assertThat(naive.isSafe()).isFalse();

        Optional<RollbackRepair> repair = repairer.repair(ds, scenario.rollbackSql());
        assertThat(repair).isPresent();
        assertThat(repair.get().safeRollbackSql()).doesNotContain("DROP TABLE");

        MigrationScenario repaired = new MigrationScenario("drop audit table repaired",
                scenario.migrationsLocation(), scenario.seedSql(),
                repair.get().safeRollbackSql(),
                DynamicOutcome.PRESERVED);
        SafetyReport verified = tester.runWithRestore(engine, ds, repaired,
                repair.get().restoreSql());
        assertThat(verified.isSafe()).isTrue();
    }

    @Test
    void repairsTypeNarrowingRollback() {
        MigrationScenario scenario = new MigrationScenario("narrow amount",
                "classpath:scenarios/narrow_amount", null,
                "ALTER TABLE measurement ALTER COLUMN amount TYPE NUMERIC(12,2)",
                DynamicOutcome.DATA_LOST);
        FlywayEngine engine = new FlywayEngine(scenario.migrationsLocation());

        SafetyReport naive = tester.run(engine, ds, scenario);
        assertThat(naive.isSafe()).isFalse();

        Optional<RollbackRepair> repair = repairer.repair(ds, scenario.rollbackSql());
        assertThat(repair).isPresent();

        MigrationScenario repaired = new MigrationScenario("narrow amount repaired",
                scenario.migrationsLocation(), null,
                repair.get().safeRollbackSql(),
                DynamicOutcome.PRESERVED);
        SafetyReport verified = tester.runWithRestore(engine, ds, repaired,
                repair.get().restoreSql());
        assertThat(verified.isSafe()).isTrue();
    }

    @Test
    void repairsNullCollapseAlongsideColumnDrop() {
        MigrationScenario scenario = new MigrationScenario("tidy rollback",
                "classpath:scenarios/tidy_rollback", null,
                "ALTER TABLE users DROP COLUMN verified; "
                        + "UPDATE users SET note = '' WHERE note IS NULL",
                DynamicOutcome.DATA_LOST);
        FlywayEngine engine = new FlywayEngine(scenario.migrationsLocation());

        SafetyReport naive = tester.run(engine, ds, scenario);
        assertThat(naive.isSafe()).isFalse();

        Optional<RollbackRepair> repair = repairer.repair(ds, scenario.rollbackSql());
        assertThat(repair).isPresent();

        MigrationScenario repaired = new MigrationScenario("tidy rollback repaired",
                scenario.migrationsLocation(), null,
                repair.get().safeRollbackSql(),
                DynamicOutcome.PRESERVED);
        SafetyReport verified = tester.runWithRestore(engine, ds, repaired,
                repair.get().restoreSql());
        assertThat(verified.isSafe()).isTrue();
    }

    @Test
    void repairsRowDeletingRollbackIncludingCascadeChildren() {
        MigrationScenario scenario = new MigrationScenario("delete cascading parent",
                "classpath:scenarios/delete_cascade", null,
                "DELETE FROM category WHERE id = 1",
                DynamicOutcome.DATA_LOST);
        FlywayEngine engine = new FlywayEngine(scenario.migrationsLocation());

        SafetyReport naive = tester.run(engine, ds, scenario);
        assertThat(naive.isSafe()).isFalse();

        Optional<RollbackRepair> repair = repairer.repair(ds, scenario.rollbackSql());
        assertThat(repair).isPresent();
        assertThat(repair.get().safeRollbackSql()).contains("migcheck_backup_product_rows");

        MigrationScenario repaired = new MigrationScenario("delete cascading parent repaired",
                scenario.migrationsLocation(), null,
                repair.get().safeRollbackSql(),
                DynamicOutcome.PRESERVED);
        SafetyReport verified = tester.runWithRestore(engine, ds, repaired,
                repair.get().restoreSql());
        assertThat(verified.isSafe()).isTrue();
    }
}
