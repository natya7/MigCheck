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
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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
            List<Path> restores = step.restoreScript() == null
                    ? List.of() : List.of(step.restoreScript());
            SnapshotDiff diff = roundTrip(engine, ds, step.version(),
                    List.of(step.rollbackScript()), restores);
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
        List<Path> restores = new ArrayList<>();
        for (MigrationStep step : steps) {
            if (step.restoreScript() != null) {
                restores.add(step.restoreScript());
            }
        }
        String top = steps.get(steps.size() - 1).version();
        SnapshotDiff diff = roundTrip(engine, ds, top, scripts, restores);
        DynamicOutcome outcome = diff.hasDataLoss()
                ? DynamicOutcome.DATA_LOST : DynamicOutcome.PRESERVED;
        return new DynamicResult(outcome, diff);
    }

    public List<Suggestion> suggestMissing(VersionedMigrationEngine engine, DataSource ds,
                                           List<MigrationStep> steps,
                                           RollbackGenerator generator) {
        List<Suggestion> suggestions = new ArrayList<>();
        for (MigrationStep step : steps) {
            if (step.rollbackScript() != null) {
                continue;
            }
            try {
                engine.clean(ds);
                engine.migrateTo(ds, step.version());
                Optional<GeneratedRollback> generated =
                        generator.generate(ds, read(step.migrationFile()));
                if (generated.isEmpty()) {
                    suggestions.add(new Suggestion(step.version(), step.description(),
                            null, "cannot invert this migration"));
                    continue;
                }
                seeder.seed(ds, engine.metadataTables());
                Snapshot before = snapshots.capture(ds, engine.metadataTables());
                engine.rollback(ds, generated.get().undoSql());
                engine.migrateTo(ds, step.version());
                if (!generated.get().restoreSql().isBlank()) {
                    execute(ds, generated.get().restoreSql());
                }
                Snapshot after = snapshots.capture(ds, engine.metadataTables());
                if (comparator.compare(before, after).hasDataLoss()) {
                    suggestions.add(new Suggestion(step.version(), step.description(),
                            null, "generated draft failed verification"));
                } else {
                    suggestions.add(new Suggestion(step.version(), step.description(),
                            generated.get(), null));
                }
            } catch (RuntimeException e) {
                suggestions.add(new Suggestion(step.version(), step.description(),
                        null, "generation failed: " + firstLine(e)));
            }
        }
        return suggestions;
    }

    private SnapshotDiff roundTrip(VersionedMigrationEngine engine, DataSource ds,
                                   String version, List<Path> rollbackScripts,
                                   List<Path> restoreScripts) {
        engine.clean(ds);
        engine.migrateTo(ds, version);
        seeder.seed(ds, engine.metadataTables());
        Snapshot before = snapshots.capture(ds, engine.metadataTables());
        for (Path script : rollbackScripts) {
            engine.rollback(ds, read(script));
        }
        engine.migrateTo(ds, version);
        for (Path script : restoreScripts) {
            execute(ds, read(script));
        }
        Snapshot after = snapshots.capture(ds, engine.metadataTables());
        return comparator.compare(before, after);
    }

    private void execute(DataSource ds, String sql) {
        try (Connection conn = ds.getConnection();
             Statement st = conn.createStatement()) {
            for (String part : sql.split(";")) {
                if (!part.isBlank()) {
                    st.execute(part);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to run SQL: " + sql, e);
        }
    }

    private String read(Path script) {
        try {
            return Files.readString(script);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read script " + script, e);
        }
    }

    private String firstLine(RuntimeException e) {
        String message = e.getMessage();
        return message == null ? e.getClass().getSimpleName() : message.split("\n")[0];
    }
}
