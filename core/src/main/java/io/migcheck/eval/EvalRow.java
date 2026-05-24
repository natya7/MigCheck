package io.migcheck.eval;

import io.migcheck.analysis.RiskLevel;
import io.migcheck.report.DynamicOutcome;

public record EvalRow(String name, RiskLevel staticRisk,
                      DynamicOutcome dynamicOutcome, DynamicOutcome groundTruth) {
}
