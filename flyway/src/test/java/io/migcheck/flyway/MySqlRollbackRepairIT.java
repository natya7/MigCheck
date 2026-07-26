package io.migcheck.flyway;

import io.migcheck.compare.DataComparator;
import io.migcheck.dialect.MySqlDialect;
import io.migcheck.repair.MySqlRepairTemplates;
import io.migcheck.repair.RollbackRepair;
import io.migcheck.repair.RollbackRepairer;
import io.migcheck.report.DynamicOutcome;
import io.migcheck.report.SafetyReport;
import io.migcheck.seed.AutoSeeder;
import io.migcheck.snapshot.SnapshotEngine;
import io.migcheck.tester.MigrationScenario;
import io.migcheck.tester.MigrationTester;
import io.migcheck.testing.MySqlSupport;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class MySqlRollbackRepairIT {

    private final DataSource ds = MySqlSupport.dataSource();
    private final MySqlDialect dialect = new MySqlDialect();
    private final String schema = MySqlSupport.schema();
    private final MigrationTester tester =
            new MigrationTester(new SnapshotEngine(dialect, schema),
                    new DataComparator(),
                    new AutoSeeder(dialect, schema, 3));
    private final RollbackRepairer repairer =
            new RollbackRepairer(dialect, new MySqlRepairTemplates(), schema);

    @Test
    void repairsColumnDroppingRollbackOnMySql() {
        MigrationScenario scenario = new MigrationScenario("mysql drop added column",
                "classpath:scenarios/mysql_add_drop", null,
                "ALTER TABLE account DROP COLUMN note",
                DynamicOutcome.DATA_LOST);
        FlywayEngine engine = new FlywayEngine(scenario.migrationsLocation());

        SafetyReport naive = tester.run(engine, ds, scenario);
        assertThat(naive.isSafe()).isFalse();

        Optional<RollbackRepair> repair = repairer.repair(ds, scenario.rollbackSql());
        assertThat(repair).isPresent();

        MigrationScenario repaired = new MigrationScenario("mysql drop repaired",
                scenario.migrationsLocation(), null,
                repair.get().safeRollbackSql(),
                DynamicOutcome.PRESERVED);
        SafetyReport verified = tester.runWithRestore(engine, ds, repaired,
                repair.get().restoreSql());
        assertThat(verified.isSafe()).isTrue();
    }
}
