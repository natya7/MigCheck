package io.migcheck.gradle;

import io.migcheck.compare.DataComparator;
import io.migcheck.dialect.Dialect;
import io.migcheck.dialect.MySqlDialect;
import io.migcheck.dialect.PostgresDialect;
import io.migcheck.flyway.FlywayEngine;
import io.migcheck.repair.MySqlRepairTemplates;
import io.migcheck.repair.PostgresRepairTemplates;
import io.migcheck.repair.RepairTemplates;
import io.migcheck.repair.RollbackRepair;
import io.migcheck.repair.RollbackRepairer;
import io.migcheck.report.DynamicOutcome;
import io.migcheck.report.SafetyReport;
import io.migcheck.seed.AutoSeeder;
import io.migcheck.snapshot.SnapshotEngine;
import io.migcheck.tester.MigrationScenario;
import io.migcheck.tester.MigrationTester;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;

import javax.inject.Inject;
import javax.sql.DataSource;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.logging.Logger;

public abstract class MigrationSafetyDynamicTask extends DefaultTask {

    @Input
    public abstract Property<String> getMigrationDir();

    @Input
    public abstract Property<String> getJdbcUrl();

    @Input
    @Optional
    public abstract Property<String> getUsername();

    @Input
    @Optional
    public abstract Property<String> getPassword();

    @Input
    public abstract Property<String> getRollbackSql();

    @Input
    @Optional
    public abstract Property<String> getDatabase();

    @Input
    @Optional
    public abstract Property<String> getSchema();

    @Inject
    protected abstract ProjectLayout getProjectLayout();

    @TaskAction
    public void run() {
        if (!getJdbcUrl().isPresent()) {
            throw new GradleException("migrationSafety.jdbcUrl must be set to run the dynamic check");
        }
        if (!getRollbackSql().isPresent()) {
            throw new GradleException("migrationSafety.rollbackSql must be set to run the dynamic check");
        }
        String db = getDatabase().getOrElse("postgres");
        Dialect dialect = "mysql".equalsIgnoreCase(db) ? new MySqlDialect() : new PostgresDialect();
        String schema = getSchema().getOrElse("public");

        DataSource dataSource = new JdbcDataSource(getJdbcUrl().get(),
                getUsername().getOrElse(""), getPassword().getOrElse(""));
        MigrationTester tester = new MigrationTester(new SnapshotEngine(dialect, schema),
                new DataComparator(), new AutoSeeder(dialect, schema, 3));

        File dir = getProjectLayout().getProjectDirectory().dir(getMigrationDir().get()).getAsFile();
        String location = "filesystem:" + dir.getAbsolutePath();
        FlywayEngine engine = new FlywayEngine(location);
        MigrationScenario scenario = new MigrationScenario("migrationSafetyTest", location,
                null, getRollbackSql().get(), DynamicOutcome.PRESERVED);

        SafetyReport report = tester.run(engine, dataSource, scenario);
        getLogger().lifecycle(report.describe());
        if (!report.isSafe()) {
            suggestRepair(dataSource, dialect, schema, tester, engine, scenario);
            throw new GradleException("Rollback safety check failed");
        }
    }

    private void suggestRepair(DataSource dataSource, Dialect dialect, String schema,
                               MigrationTester tester, FlywayEngine engine,
                               MigrationScenario scenario) {
        try {
            RepairTemplates templates = dialect instanceof MySqlDialect
                    ? new MySqlRepairTemplates() : new PostgresRepairTemplates();
            RollbackRepairer repairer = new RollbackRepairer(dialect, templates, schema);
            java.util.Optional<RollbackRepair> repair =
                    repairer.repair(dataSource, scenario.rollbackSql());
            if (repair.isEmpty()) {
                return;
            }
            MigrationScenario candidate = new MigrationScenario(scenario.name() + " repaired",
                    scenario.migrationsLocation(), null,
                    repair.get().safeRollbackSql(), DynamicOutcome.PRESERVED);
            SafetyReport verified = tester.runWithRestore(engine, dataSource, candidate,
                    repair.get().restoreSql());
            if (!verified.isSafe()) {
                return;
            }
            File outDir = getProjectLayout().getBuildDirectory().dir("migcheck").get().getAsFile();
            outDir.mkdirs();
            File safe = new File(outDir, "safe-rollback.sql");
            File restore = new File(outDir, "restore-after-redeploy.sql");
            Files.writeString(safe.toPath(), repair.get().safeRollbackSql() + "\n");
            Files.writeString(restore.toPath(), repair.get().restoreSql() + "\n");
            getLogger().lifecycle("[MigCheck] verified safe rollback available:");
            getLogger().lifecycle("[MigCheck]   " + safe.getAbsolutePath());
            getLogger().lifecycle("[MigCheck]   " + restore.getAbsolutePath()
                    + " (run after the migration is deployed again)");
        } catch (RuntimeException | IOException e) {
            getLogger().info("Could not build a safe rollback suggestion: " + e.getMessage());
        }
    }

    private static final class JdbcDataSource implements DataSource {

        private final String url;
        private final String user;
        private final String password;

        private JdbcDataSource(String url, String user, String password) {
            this.url = url;
            this.user = user;
            this.password = password;
        }

        @Override
        public Connection getConnection() throws SQLException {
            return DriverManager.getConnection(url, user, password);
        }

        @Override
        public Connection getConnection(String username, String pass) throws SQLException {
            return DriverManager.getConnection(url, username, pass);
        }

        @Override
        public PrintWriter getLogWriter() {
            return null;
        }

        @Override
        public void setLogWriter(PrintWriter out) {
        }

        @Override
        public void setLoginTimeout(int seconds) {
        }

        @Override
        public int getLoginTimeout() {
            return 0;
        }

        @Override
        public Logger getParentLogger() throws SQLFeatureNotSupportedException {
            throw new SQLFeatureNotSupportedException();
        }

        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            throw new SQLException("Not a wrapper for " + iface);
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) {
            return false;
        }
    }
}
