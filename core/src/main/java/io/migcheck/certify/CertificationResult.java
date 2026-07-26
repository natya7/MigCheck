package io.migcheck.certify;

import java.util.List;

public record CertificationResult(List<StepResult> steps) {

    public boolean hasDataLoss() {
        return steps.stream().anyMatch(s -> s.outcome() == CertificationOutcome.DATA_LOST);
    }

    public long uncovered() {
        return steps.stream().filter(s -> s.outcome() == CertificationOutcome.NO_ROLLBACK).count();
    }

    public String describe() {
        StringBuilder out = new StringBuilder();
        long lost = 0;
        for (StepResult step : steps) {
            String label = switch (step.outcome()) {
                case PRESERVED -> "PRESERVED";
                case DATA_LOST -> "DATA_LOST";
                case NO_ROLLBACK -> "no rollback script";
            };
            out.append("V").append(step.version()).append("  ")
                    .append(step.description()).append("  ").append(label).append("\n");
            if (step.outcome() == CertificationOutcome.DATA_LOST) {
                lost++;
                for (String line : step.diff().differences()) {
                    out.append("    ").append(line).append("\n");
                }
            }
        }
        long covered = steps.size() - uncovered();
        out.append("certified ").append(covered).append("/").append(steps.size())
                .append(" migrations: ").append(lost).append(" data loss, ")
                .append(uncovered()).append(" uncovered");
        return out.toString();
    }
}
