package io.migcheck.certify;

import io.migcheck.compare.SnapshotDiff;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CertificationResultTest {

    private final SnapshotDiff empty = new SnapshotDiff(List.of(), List.of(), Map.of());

    @Test
    void describesEveryStepAndSummarizes() {
        CertificationResult result = new CertificationResult(List.of(
                new StepResult("1", "create_users", CertificationOutcome.PRESERVED, empty),
                new StepResult("2", "add_note", CertificationOutcome.DATA_LOST, empty),
                new StepResult("3", "create_audit", CertificationOutcome.NO_ROLLBACK, null)));

        String text = result.describe();

        assertThat(text).contains("V1").contains("create_users").contains("PRESERVED");
        assertThat(text).contains("V2").contains("DATA_LOST");
        assertThat(text).contains("V3").contains("no rollback script");
        assertThat(text).contains("certified 2/3 migrations: 1 data loss, 1 uncovered");
        assertThat(result.hasDataLoss()).isTrue();
        assertThat(result.uncovered()).isEqualTo(1);
    }

    @Test
    void cleanHistoryReadsClean() {
        CertificationResult result = new CertificationResult(List.of(
                new StepResult("1", "init", CertificationOutcome.PRESERVED, empty)));

        assertThat(result.hasDataLoss()).isFalse();
        assertThat(result.uncovered()).isZero();
        assertThat(result.describe()).contains("certified 1/1 migrations: 0 data loss, 0 uncovered");
    }
}
