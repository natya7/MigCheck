package io.migcheck.certify;

import io.migcheck.compare.DataComparator;
import io.migcheck.compare.SnapshotDiff;
import io.migcheck.engine.VersionedMigrationEngine;
import io.migcheck.report.DynamicOutcome;
import io.migcheck.report.DynamicResult;
import io.migcheck.seed.AutoSeeder;
import io.migcheck.snapshot.Snapshot;
import io.migcheck.snapshot.SnapshotEngine;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class HistoryCertifier {

    private final SnapshotEngine snapshots;
    private final DataComparator comparator;
    private final AutoSeeder seeder;

    public HistoryCertifier(SnapshotEngine snapshots, DataComparator comparator, AutoSeeder seeder) {
        this.snapshots = snapshots;
        this.comparator = comparator;
        this.seeder = seeder;
    }

    public CertificationResult certify(VersionedMigrationEngine engine, DataSource ds,
                                       List<MigrationStep> steps) {
        List<StepResult> results = new ArrayList<>();
        for (MigrationStep step : steps) {
            if (step.rollbackScript() == null) {
                results.add(new StepResult(step.version(), step.description(),
                        CertificationOutcome.NO_ROLLBACK, null));
                continue;
            }
            SnapshotDiff diff = roundTrip(engine, ds, step.version(),
                    List.of(step.rollbackScript()));
            CertificationOutcome outcome = diff.hasDataLoss()
                    ? CertificationOutcome.DATA_LOST : CertificationOutcome.PRESERVED;
            results.add(new StepResult(step.version(), step.description(), outcome, diff));
        }
        return new CertificationResult(results);
    }

    public DynamicResult certifyChain(VersionedMigrationEngine engine, DataSource ds,
                                      List<MigrationStep> steps) {
        List<Path> scripts = new ArrayList<>();
        for (int i = steps.size() - 1; i >= 0; i--) {
            if (steps.get(i).rollbackScript() == null) {
                throw new IllegalArgumentException(
                        "No rollback script for V" + steps.get(i).version());
            }
            scripts.add(steps.get(i).rollbackScript());
        }
        String top = steps.get(steps.size() - 1).version();
        SnapshotDiff diff = roundTrip(engine, ds, top, scripts);
        DynamicOutcome outcome = diff.hasDataLoss()
                ? DynamicOutcome.DATA_LOST : DynamicOutcome.PRESERVED;
        return new DynamicResult(outcome, diff);
    }

    private SnapshotDiff roundTrip(VersionedMigrationEngine engine, DataSource ds,
                                   String version, List<Path> rollbackScripts) {
        engine.clean(ds);
        engine.migrateTo(ds, version);
        seeder.seed(ds, engine.metadataTables());
        Snapshot before = snapshots.capture(ds, engine.metadataTables());
        for (Path script : rollbackScripts) {
            engine.rollback(ds, read(script));
        }
        engine.migrateTo(ds, version);
        Snapshot after = snapshots.capture(ds, engine.metadataTables());
        return comparator.compare(before, after);
    }

    private String read(Path script) {
        try {
            return Files.readString(script);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read rollback script " + script, e);
        }
    }
}
