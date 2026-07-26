package io.migcheck.repair;

import io.migcheck.dialect.PostgresDialect;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RollbackRepairerTest {

    private final RollbackRepairer repairer = new RollbackRepairer(
            new PostgresDialect(), new PostgresRepairTemplates(), "public");

    @Test
    void refusesUnparseableSql() {
        assertThat(repairer.repair(null, "not sql at all")).isEmpty();
    }

    @Test
    void refusesUpdatesItCannotInvert() {
        assertThat(repairer.repair(null, "UPDATE users SET note = LOWER(note)")).isEmpty();
    }

    @Test
    void refusesWhenAnyDestructiveStatementIsUnknown() {
        assertThat(repairer.repair(null,
                "ALTER TABLE users DROP COLUMN note; UPDATE users SET note = LOWER(note)"))
                .isEmpty();
    }

    @Test
    void passesThroughHarmlessRollbacksUntouched() {
        assertThat(repairer.repair(null,
                "ALTER TABLE users RENAME COLUMN full_name TO name")).isEmpty();
    }

    @Test
    void refusesSetNotNullRollbacks() {
        assertThat(repairer.repair(null,
                "ALTER TABLE users ALTER COLUMN note SET NOT NULL")).isEmpty();
    }
}
