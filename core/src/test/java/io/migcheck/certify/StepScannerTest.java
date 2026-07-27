package io.migcheck.certify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StepScannerTest {

    @TempDir
    Path dir;

    @Test
    void pairsMigrationsWithRollbackScriptsInVersionOrder() throws Exception {
        Path migrations = Files.createDirectory(dir.resolve("migrations"));
        Path rollback = Files.createDirectory(dir.resolve("rollback"));
        Files.writeString(migrations.resolve("V2__add_note.sql"), "x");
        Files.writeString(migrations.resolve("V1__create_users.sql"), "x");
        Files.writeString(migrations.resolve("V10__add_index.sql"), "x");
        Files.writeString(rollback.resolve("U2__drop_note.sql"), "x");
        Files.writeString(rollback.resolve("R2__restore_note.sql"), "x");

        List<MigrationStep> steps = StepScanner.scan(migrations, rollback);

        assertThat(steps).extracting(MigrationStep::version)
                .containsExactly("1", "2", "10");
        assertThat(steps.get(0).migrationFile()).isNotNull();
        assertThat(steps.get(0).rollbackScript()).isNull();
        assertThat(steps.get(0).restoreScript()).isNull();
        assertThat(steps.get(1).rollbackScript()).isNotNull();
        assertThat(steps.get(1).restoreScript()).isNotNull();
        assertThat(steps.get(1).description()).isEqualTo("add_note");
    }

    @Test
    void toleratesMissingRollbackDirectory() throws Exception {
        Path migrations = Files.createDirectory(dir.resolve("m"));
        Files.writeString(migrations.resolve("V1__init.sql"), "x");

        List<MigrationStep> steps = StepScanner.scan(migrations, dir.resolve("absent"));

        assertThat(steps).hasSize(1);
        assertThat(steps.get(0).rollbackScript()).isNull();
    }
}
