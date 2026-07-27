package io.migcheck.flyway;

import io.migcheck.certify.HistoryCertifier;
import io.migcheck.certify.MigrationStep;
import io.migcheck.certify.RollbackGenerator;
import io.migcheck.certify.StepScanner;
import io.migcheck.certify.Suggestion;
import io.migcheck.compare.DataComparator;
import io.migcheck.dialect.PostgresDialect;
import io.migcheck.repair.PostgresRepairTemplates;
import io.migcheck.seed.AutoSeeder;
import io.migcheck.snapshot.SnapshotEngine;
import io.migcheck.testing.PostgresSupport;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MissingRollbackIT {

    private final DataSource ds = PostgresSupport.dataSource();
    private final PostgresDialect dialect = new PostgresDialect();
    private final HistoryCertifier certifier =
            new HistoryCertifier(new SnapshotEngine(dialect, "public"),
                    new DataComparator(),
                    new AutoSeeder(dialect, "public", 3));

    @Test
    void generatesVerifiedRollbacksForUncoveredMigrationsAndRefusesTheRest() throws Exception {
        Path migrations = Path.of(getClass().getClassLoader()
                .getResource("scenarios/autofill_history/migrations").toURI());
        Path rollback = Path.of(getClass().getClassLoader()
                .getResource("scenarios/autofill_history/rollback").toURI());
        List<MigrationStep> steps = StepScanner.scan(migrations, rollback);
        FlywayEngine engine = new FlywayEngine("filesystem:" + migrations);

        List<Suggestion> suggestions = certifier.suggestMissing(engine, ds, steps,
                new RollbackGenerator(dialect, new PostgresRepairTemplates(), "public"));

        assertThat(suggestions).hasSize(2);
        assertThat(suggestions.get(0).version()).isEqualTo("2");
        assertThat(suggestions.get(0).reason()).isNull();
        assertThat(suggestions.get(0).rollback().undoSql())
                .contains("migcheck_backup_users_note")
                .contains("DROP COLUMN note");
        assertThat(suggestions.get(0).rollback().restoreSql()).contains("SET \"note\"");
        assertThat(suggestions.get(1).version()).isEqualTo("3");
        assertThat(suggestions.get(1).rollback()).isNull();
        assertThat(suggestions.get(1).reason()).isEqualTo("cannot invert this migration");
    }
}
