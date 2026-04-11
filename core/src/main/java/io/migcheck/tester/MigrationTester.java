package io.migcheck.tester;

import io.migcheck.compare.DataComparator;
import io.migcheck.compare.SnapshotDiff;
import io.migcheck.engine.MigrationEngine;
import io.migcheck.report.DynamicOutcome;
import io.migcheck.report.DynamicResult;
import io.migcheck.report.SafetyReport;
import io.migcheck.snapshot.Snapshot;
import io.migcheck.snapshot.SnapshotEngine;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class MigrationTester {

    private final SnapshotEngine snapshots;
    private final DataComparator comparator;

    public MigrationTester(SnapshotEngine snapshots, DataComparator comparator) {
        this.snapshots = snapshots;
        this.comparator = comparator;
    }

    public SafetyReport run(MigrationEngine engine, DataSource ds, MigrationScenario scenario) {
        engine.clean(ds);
        engine.migrate(ds);
        execute(ds, scenario.seedSql());
        Snapshot before = snapshots.capture(ds);

        engine.rollback(ds, scenario.rollbackSql());
        engine.migrate(ds);
        Snapshot after = snapshots.capture(ds);

        SnapshotDiff diff = comparator.compare(before, after);
        DynamicOutcome outcome =
                diff.hasDataLoss() ? DynamicOutcome.DATA_LOST : DynamicOutcome.PRESERVED;
        return new SafetyReport(scenario.name(), new DynamicResult(outcome, diff));
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
