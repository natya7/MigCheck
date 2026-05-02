package io.migcheck.tester;

import io.migcheck.compare.DataComparator;
import io.migcheck.compare.SnapshotDiff;
import io.migcheck.engine.MigrationEngine;
import io.migcheck.report.DynamicOutcome;
import io.migcheck.report.DynamicResult;
import io.migcheck.report.SafetyReport;
import io.migcheck.seed.AutoSeeder;
import io.migcheck.snapshot.Snapshot;
import io.migcheck.snapshot.SnapshotEngine;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class MigrationTester {

    private final SnapshotEngine snapshots;
    private final DataComparator comparator;
    private final AutoSeeder seeder;

    public MigrationTester(SnapshotEngine snapshots, DataComparator comparator, AutoSeeder seeder) {
        this.snapshots = snapshots;
        this.comparator = comparator;
        this.seeder = seeder;
    }

    public SafetyReport run(MigrationEngine engine, DataSource ds, MigrationScenario scenario) {
        engine.clean(ds);
        engine.migrate(ds);
        seed(ds, engine, scenario);
        Snapshot before = snapshots.capture(ds, engine.metadataTables());

        engine.rollback(ds, scenario.rollbackSql());
        engine.migrate(ds);
        Snapshot after = snapshots.capture(ds, engine.metadataTables());

        SnapshotDiff diff = comparator.compare(before, after);
        DynamicOutcome outcome =
                diff.hasDataLoss() ? DynamicOutcome.DATA_LOST : DynamicOutcome.PRESERVED;
        return new SafetyReport(scenario.name(), new DynamicResult(outcome, diff));
    }

    private void seed(DataSource ds, MigrationEngine engine, MigrationScenario scenario) {
        if (scenario.seedSql() != null && !scenario.seedSql().isBlank()) {
            execute(ds, scenario.seedSql());
        } else {
            seeder.seed(ds, engine.metadataTables());
        }
    }

    private void execute(DataSource ds, String sql) {
        try (Connection conn = ds.getConnection();
             Statement st = conn.createStatement()) {
            st.execute(sql);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to run SQL: " + sql, e);
        }
    }
}
