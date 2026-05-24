package io.migcheck.eval;

import io.migcheck.engine.MigrationEngine;
import io.migcheck.tester.MigrationScenario;

public record EvalCase(String name, String forwardSql,
                       MigrationEngine engine, MigrationScenario scenario) {
}
