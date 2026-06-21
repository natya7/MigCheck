package io.migcheck.seed;

import io.migcheck.compare.DataComparator;
import io.migcheck.dialect.PostgresDialect;
import io.migcheck.snapshot.Snapshot;
import io.migcheck.snapshot.SnapshotEngine;
import io.migcheck.testing.PostgresSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

class AutoSeederBoundaryIT {

    private final DataSource ds = PostgresSupport.dataSource();
    private final PostgresDialect dialect = new PostgresDialect();
    private final AutoSeeder seeder = new AutoSeeder(dialect, "public", 3);
    private final SnapshotEngine snapshots = new SnapshotEngine(dialect, "public");
    private final DataComparator comparator = new DataComparator();

    @BeforeEach
    void setUp() throws Exception {
        PostgresSupport.reset();
        exec("CREATE TABLE measurement (id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY, "
                + "label VARCHAR(40) NOT NULL, amount NUMERIC(12,4) NOT NULL)");
    }

    @Test
    void seededValuesExposeNarrowingRollback() throws Exception {
        seeder.seed(ds);
        Snapshot before = snapshots.capture(ds);

        exec("ALTER TABLE measurement ALTER COLUMN label TYPE VARCHAR(6) USING LEFT(label, 6)");
        exec("ALTER TABLE measurement ALTER COLUMN amount TYPE NUMERIC(12,1)");
        exec("ALTER TABLE measurement ALTER COLUMN label TYPE VARCHAR(40)");
        exec("ALTER TABLE measurement ALTER COLUMN amount TYPE NUMERIC(12,4)");

        Snapshot after = snapshots.capture(ds);

        assertThat(comparator.compare(before, after).hasDataLoss()).isTrue();
    }

    private void exec(String sql) throws Exception {
        try (Connection c = ds.getConnection(); Statement s = c.createStatement()) {
            s.execute(sql);
        }
    }
}
