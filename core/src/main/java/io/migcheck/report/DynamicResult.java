package io.migcheck.report;

import io.migcheck.compare.SnapshotDiff;

public record DynamicResult(DynamicOutcome outcome, SnapshotDiff diff) {
}
