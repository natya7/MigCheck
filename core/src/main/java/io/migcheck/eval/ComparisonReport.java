package io.migcheck.eval;

import io.migcheck.analysis.RiskLevel;
import io.migcheck.report.DynamicOutcome;

import java.util.List;

public class ComparisonReport {

    private final List<EvalRow> rows;

    public ComparisonReport(List<EvalRow> rows) {
        this.rows = List.copyOf(rows);
    }

    public List<EvalRow> rows() {
        return rows;
    }

    public int staticFalsePositives() {
        return (int) rows.stream().filter(r -> staticUnsafe(r) && !actualUnsafe(r)).count();
    }

    public int staticFalseNegatives() {
        return (int) rows.stream().filter(r -> !staticUnsafe(r) && actualUnsafe(r)).count();
    }

    public int dynamicFalsePositives() {
        return (int) rows.stream().filter(r -> dynamicUnsafe(r) && !actualUnsafe(r)).count();
    }

    public int dynamicFalseNegatives() {
        return (int) rows.stream().filter(r -> !dynamicUnsafe(r) && actualUnsafe(r)).count();
    }

    public String toMarkdown() {
        StringBuilder sb = new StringBuilder();
        sb.append("| Scenario | Static risk | Static verdict | Dynamic | Ground truth |\n");
        sb.append("|---|---|---|---|---|\n");
        for (EvalRow r : rows) {
            sb.append("| ").append(r.name())
                    .append(" | ").append(r.staticRisk())
                    .append(" | ").append(staticUnsafe(r) ? "DATA_LOST" : "PRESERVED")
                    .append(" | ").append(r.dynamicOutcome())
                    .append(" | ").append(r.groundTruth())
                    .append(" |\n");
        }
        sb.append("\nStatic: false positives=").append(staticFalsePositives())
                .append(", false negatives=").append(staticFalseNegatives());
        sb.append("\nDynamic: false positives=").append(dynamicFalsePositives())
                .append(", false negatives=").append(dynamicFalseNegatives());
        return sb.toString();
    }

    private boolean staticUnsafe(EvalRow r) {
        return r.staticRisk() != RiskLevel.LOW;
    }

    private boolean dynamicUnsafe(EvalRow r) {
        return r.dynamicOutcome() == DynamicOutcome.DATA_LOST;
    }

    private boolean actualUnsafe(EvalRow r) {
        return r.groundTruth() == DynamicOutcome.DATA_LOST;
    }
}
