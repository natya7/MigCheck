package io.migcheck.eval;

import io.migcheck.analysis.Finding;
import io.migcheck.analysis.RiskLevel;
import io.migcheck.report.DynamicOutcome;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ComparisonReportTest {

    @Test
    void listsAllStaticFindingsNotJustHighest() {
        EvalRow row = new EvalRow("multi", RiskLevel.HIGH,
                List.of(new Finding(RiskLevel.HIGH, "DROP TABLE users"),
                        new Finding(RiskLevel.MEDIUM, "column type change")),
                DynamicOutcome.DATA_LOST, DynamicOutcome.DATA_LOST);
        ComparisonReport report = new ComparisonReport(List.of(row));

        String md = report.toMarkdown();

        assertThat(md).contains("DROP TABLE users");
        assertThat(md).contains("column type change");
    }
}
