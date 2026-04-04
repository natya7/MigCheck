package io.migcheck.snapshot;

import io.migcheck.dialect.PostgresDialect;
import io.migcheck.testing.PostgresSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

class SnapshotEngineIT {

    private final DataSource ds = PostgresSupport.dataSource();
    private final SnapshotEngine engine =
            new SnapshotEngine(new PostgresDialect(), "public");

    @BeforeEach
    void setUp() throws Exception {
        PostgresSupport.reset();
        exec("CREATE TABLE person (id BIGINT PRIMARY KEY, name VARCHAR(50))");
        exec("INSERT INTO person (id, name) VALUES (1, 'Ada'), (2, 'Linus')");
    }

    @Test
    void capturesRows() {
        Snapshot snapshot = engine.capture(ds);

        assertThat(snapshot.tables()).containsKey("person");
        assertThat(snapshot.tables().get("person").rows()).hasSize(2);
        assertThat(snapshot.tables().get("person").rows())
                .extracting(row -> row.get("name"))
                .containsExactlyInAnyOrder("Ada", "Linus");
    }

    @Test
    void capturesTableWithQuotedName() throws Exception {
        exec("CREATE TABLE \"Orders\" (id BIGINT PRIMARY KEY)");
        exec("INSERT INTO \"Orders\" (id) VALUES (1)");

        Snapshot snapshot = engine.capture(ds);

        assertThat(snapshot.tables()).containsKey("Orders");
        assertThat(snapshot.tables().get("Orders").rows()).hasSize(1);
    }

    private void exec(String sql) throws Exception {
        try (Connection c = ds.getConnection(); Statement s = c.createStatement()) {
            s.execute(sql);
        }
    }
}
