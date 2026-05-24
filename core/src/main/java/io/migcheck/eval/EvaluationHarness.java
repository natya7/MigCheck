package io.migcheck.eval;

import io.migcheck.analysis.RiskLevel;
import io.migcheck.analysis.StaticAnalyzer;
import io.migcheck.report.DynamicOutcome;
import io.migcheck.tester.MigrationTester;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;

public class EvaluationHarness {

    private final StaticAnalyzer analyzer;
    private final MigrationTester tester;

    public EvaluationHarness(StaticAnalyzer analyzer, MigrationTester tester) {
        this.analyzer = analyzer;
        this.tester = tester;
    }

    public ComparisonReport evaluate(DataSource dataSource, List<EvalCase> cases) {
        List<EvalRow> rows = new ArrayList<>();
        for (EvalCase c : cases) {
            RiskLevel staticRisk = analyzer.analyze(c.forwardSql()).risk();
            DynamicOutcome dynamic = tester.run(c.engine(), dataSource, c.scenario())
                    .dynamicResult().outcome();
            rows.add(new EvalRow(c.name(), staticRisk, dynamic, c.scenario().expectedOutcome()));
        }
        return new ComparisonReport(rows);
    }
}
