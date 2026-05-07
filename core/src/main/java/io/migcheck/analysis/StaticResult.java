package io.migcheck.analysis;

import java.util.List;

public record StaticResult(RiskLevel risk, List<Finding> findings) {
}
