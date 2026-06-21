package io.migcheck.eval;

import io.migcheck.analysis.Finding;
import io.migcheck.analysis.RiskLevel;
import io.migcheck.report.DynamicOutcome;

import java.util.List;

public record EvalRow(String name, RiskLevel staticRisk, List<Finding> staticFindings,
                      DynamicOutcome dynamicOutcome, DynamicOutcome groundTruth) {
}
