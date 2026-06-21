package io.migcheck.report;

import java.util.List;

public record SafetyReport(String scenario, DynamicResult dynamicResult) {

    public boolean isSafe() {
        return dynamicResult.outcome() == DynamicOutcome.PRESERVED;
    }

    public List<String> dataDifferences() {
        return dynamicResult.diff().differences();
    }

    public String describe() {
        String head = "[MigCheck] " + (isSafe() ? "PASS" : "FAIL") + " - " + scenario;
        if (isSafe()) {
            return head;
        }
        return head + "\n" + dynamicResult.diff().summary();
    }
}
