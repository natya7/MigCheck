package io.migcheck.flyway;

import io.migcheck.compare.DataComparator;
import io.migcheck.dialect.PostgresDialect;
import io.migcheck.eval.ComparisonReport;
import io.migcheck.eval.EvalCase;
import io.migcheck.eval.EvaluationHarness;
import io.migcheck.analysis.StaticAnalyzer;
import io.migcheck.report.DynamicOutcome;
import io.migcheck.seed.AutoSeeder;
import io.migcheck.snapshot.SnapshotEngine;
import io.migcheck.testing.PostgresSupport;
import io.migcheck.tester.MigrationScenario;
import io.migcheck.tester.MigrationTester;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EvaluationHarnessIT {

    private final DataSource ds = PostgresSupport.dataSource();
    private final EvaluationHarness harness = new EvaluationHarness(
            new StaticAnalyzer(),
            new MigrationTester(new SnapshotEngine(new PostgresDialect(), "public"),
                    new DataComparator(),
                    new AutoSeeder(new PostgresDialect(), "public", 3)));

    @Test
    void comparesStaticAndDynamicAcrossCorpus() {
        List<EvalCase> corpus = List.of(
                evalCase("safe rename",
                        "ALTER TABLE users RENAME COLUMN name TO full_name",
                        "classpath:scenarios/safe_rename",
                        "INSERT INTO users (id, full_name) VALUES (1, 'Ada'), (2, 'Linus')",
                        "ALTER TABLE users RENAME COLUMN full_name TO name",
                        DynamicOutcome.PRESERVED),
                evalCase("add column",
                        "ALTER TABLE users ADD COLUMN score INT",
                        "classpath:scenarios/drop_added_column",
                        "INSERT INTO users (id, name, score) VALUES (1, 'Ada', 100)",
                        "ALTER TABLE users DROP COLUMN score",
                        DynamicOutcome.DATA_LOST),
                evalCase("lossy narrow",
                        "ALTER TABLE users ALTER COLUMN name TYPE VARCHAR(255)",
                        "classpath:scenarios/lossy_narrowing",
                        "INSERT INTO users (id, name) VALUES (1, 'Alexandria the Great')",
                        "ALTER TABLE users ALTER COLUMN name TYPE VARCHAR(10) USING LEFT(name, 10)",
                        DynamicOutcome.DATA_LOST),
                evalCase("drop column",
                        "ALTER TABLE users DROP COLUMN email",
                        "classpath:scenarios/drop_column_safe_rollback",
                        "INSERT INTO users (id, name) VALUES (1, 'Ada')",
                        "ALTER TABLE users ADD COLUMN email VARCHAR(255)",
                        DynamicOutcome.PRESERVED));

        ComparisonReport report = harness.evaluate(ds, corpus);
        System.out.println(report.toMarkdown());

        assertThat(report.dynamicFalsePositives()).isZero();
        assertThat(report.dynamicFalseNegatives()).isZero();
        assertThat(report.staticFalseNegatives()).isEqualTo(1);
        assertThat(report.staticFalsePositives()).isEqualTo(1);
    }

    private EvalCase evalCase(String name, String forwardSql, String location,
                              String seed, String rollback, DynamicOutcome expected) {
        MigrationScenario scenario = new MigrationScenario(name, location, seed, rollback, expected);
        return new EvalCase(name, forwardSql, new FlywayEngine(location), scenario);
    }
}
