package io.migcheck.gradle;

import io.migcheck.certify.CertificationResult;
import io.migcheck.certify.HistoryCertifier;
import io.migcheck.certify.MigrationStep;
import io.migcheck.certify.StepScanner;
import io.migcheck.compare.DataComparator;
import io.migcheck.dialect.Dialect;
import io.migcheck.dialect.MySqlDialect;
import io.migcheck.dialect.PostgresDialect;
import io.migcheck.flyway.FlywayEngine;
import io.migcheck.seed.AutoSeeder;
import io.migcheck.snapshot.SnapshotEngine;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.options.Option;

import javax.inject.Inject;
import java.io.File;
import java.util.List;

public abstract class MigrationSafetyCertifyTask extends DefaultTask {

    @Input
    public abstract Property<String> getMigrationDir();

    @Input
    public abstract Property<String> getRollbackDir();

    @Input
    public abstract Property<String> getJdbcUrl();

    @Input
    @Optional
    public abstract Property<String> getUsername();

    @Input
    @Optional
    public abstract Property<String> getPassword();

    @Input
    @Optional
    public abstract Property<String> getDatabase();

    @Input
    @Optional
    public abstract Property<String> getSchema();

    @Input
    @Optional
    @Option(option = "require-rollbacks",
            description = "Fail when a migration has no rollback script")
    public abstract Property<Boolean> getRequireRollbacks();

    @Inject
    protected abstract ProjectLayout getProjectLayout();

    @TaskAction
    public void run() {
        if (!getJdbcUrl().isPresent()) {
            throw new GradleException("migrationSafety.jdbcUrl must be set to run the certification");
        }
        String db = getDatabase().getOrElse("postgres");
        Dialect dialect = "mysql".equalsIgnoreCase(db) ? new MySqlDialect() : new PostgresDialect();
        String schema = getSchema().getOrElse("public");

        File migrations = getProjectLayout().getProjectDirectory()
                .dir(getMigrationDir().get()).getAsFile();
        File rollback = getProjectLayout().getProjectDirectory()
                .dir(getRollbackDir().get()).getAsFile();
        List<MigrationStep> steps = StepScanner.scan(migrations.toPath(), rollback.toPath());
        if (steps.isEmpty()) {
            getLogger().lifecycle("[MigCheck] no migrations found in " + migrations);
            return;
        }

        HistoryCertifier certifier = new HistoryCertifier(new SnapshotEngine(dialect, schema),
                new DataComparator(), new AutoSeeder(dialect, schema, 3));
        FlywayEngine engine = new FlywayEngine("filesystem:" + migrations.getAbsolutePath());
        CertificationResult result = certifier.certify(engine,
                new JdbcDataSource(getJdbcUrl().get(),
                        getUsername().getOrElse(""), getPassword().getOrElse("")), steps);

        getLogger().lifecycle(result.describe());
        if (result.hasDataLoss()) {
            throw new GradleException("Rollback certification failed");
        }
        if (result.uncovered() > 0 && getRequireRollbacks().getOrElse(false)) {
            throw new GradleException(result.uncovered() + " migrations have no rollback script");
        }
    }
}
