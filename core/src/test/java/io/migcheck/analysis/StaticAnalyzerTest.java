package io.migcheck.analysis;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StaticAnalyzerTest {

    private final StaticAnalyzer analyzer = new StaticAnalyzer();

    @Test
    void flagsDropTableAsHigh() {
        assertThat(analyzer.analyze("DROP TABLE users").risk()).isEqualTo(RiskLevel.HIGH);
    }

    @Test
    void flagsDropColumnAsHigh() {
        assertThat(analyzer.analyze("ALTER TABLE users DROP COLUMN email").risk())
                .isEqualTo(RiskLevel.HIGH);
    }

    @Test
    void flagsTruncateAsHigh() {
        assertThat(analyzer.analyze("TRUNCATE TABLE audit_log").risk()).isEqualTo(RiskLevel.HIGH);
    }

    @Test
    void flagsTypeChangeAsMedium() {
        assertThat(analyzer.analyze("ALTER TABLE users ALTER COLUMN name TYPE VARCHAR(10)").risk())
                .isEqualTo(RiskLevel.MEDIUM);
    }

    @Test
    void treatsRenameColumnAsLow() {
        assertThat(analyzer.analyze("ALTER TABLE users RENAME COLUMN name TO full_name").risk())
                .isEqualTo(RiskLevel.LOW);
    }

    @Test
    void treatsCreateTableAsLow() {
        assertThat(analyzer.analyze("CREATE TABLE logs (id BIGINT PRIMARY KEY)").risk())
                .isEqualTo(RiskLevel.LOW);
    }

    @Test
    void reportsFindingsForDangerousOperations() {
        assertThat(analyzer.analyze("DROP TABLE users").findings()).isNotEmpty();
    }
}
