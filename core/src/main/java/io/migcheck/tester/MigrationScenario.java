package io.migcheck.tester;

import io.migcheck.report.DynamicOutcome;

public record MigrationScenario(String name, String migrationsLocation,
                                String seedSql, String rollbackSql,
                                DynamicOutcome expectedOutcome) {
}
