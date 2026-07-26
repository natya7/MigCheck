package io.migcheck.flyway;

import io.migcheck.certify.CertificationOutcome;
import io.migcheck.certify.CertificationResult;
import io.migcheck.certify.HistoryCertifier;
import io.migcheck.certify.MigrationStep;
import io.migcheck.certify.StepScanner;
import io.migcheck.compare.DataComparator;
import io.migcheck.dialect.PostgresDialect;
import io.migcheck.report.DynamicOutcome;
import io.migcheck.report.DynamicResult;
import io.migcheck.seed.AutoSeeder;
import io.migcheck.snapshot.SnapshotEngine;
import io.migcheck.testing.PostgresSupport;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HistoryCertifierIT {

    private final DataSource ds = PostgresSupport.dataSource();
    private final PostgresDialect dialect = new PostgresDialect();
    private final HistoryCertifier certifier =
            new HistoryCertifier(new SnapshotEngine(dialect, "public"),
                    new DataComparator(),
                    new AutoSeeder(dialect, "public", 3));

    private Path resource(String name) throws Exception {
        return Path.of(getClass().getClassLoader()
                .getResource("scenarios/certify_history/" + name).toURI());
    }

    @Test
    void certifiesEveryMigrationInTheHistory() throws Exception {
        Path migrations = resource("migrations");
        List<MigrationStep> steps = StepScanner.scan(migrations, resource("rollback"));
        FlywayEngine engine = new FlywayEngine("filesystem:" + migrations);

        CertificationResult result = certifier.certify(engine, ds, steps);

        assertThat(result.steps()).extracting(r -> r.outcome()).containsExactly(
                CertificationOutcome.DATA_LOST,
                CertificationOutcome.PRESERVED,
                CertificationOutcome.NO_ROLLBACK);
        assertThat(result.describe())
                .contains("certified 2/3 migrations: 1 data loss, 1 uncovered");
    }

    @Test
    void chainRollsSeveralStepsBackAndDetectsCompoundLoss() throws Exception {
        Path migrations = resource("migrations");
        List<MigrationStep> steps = StepScanner.scan(migrations, resource("rollback"));
        FlywayEngine engine = new FlywayEngine("filesystem:" + migrations);

        DynamicResult chain = certifier.certifyChain(engine, ds, steps.subList(0, 2));

        assertThat(chain.outcome()).isEqualTo(DynamicOutcome.DATA_LOST);
    }
}
