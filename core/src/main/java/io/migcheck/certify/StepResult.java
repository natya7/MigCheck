package io.migcheck.certify;

import io.migcheck.compare.SnapshotDiff;

public record StepResult(String version, String description,
                         CertificationOutcome outcome, SnapshotDiff diff) {
}
