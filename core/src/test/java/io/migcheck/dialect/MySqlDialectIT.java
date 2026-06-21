package io.migcheck.dialect;

import io.migcheck.compare.DataComparator;
import io.migcheck.seed.AutoSeeder;
import io.migcheck.snapshot.Snapshot;
import io.migcheck.snapshot.SnapshotEngine;
import io.migcheck.testing.MySqlSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

class MySqlDialectIT {

    private final DataSource ds = MySqlSupport.dataSource();
    private final String schema = MySqlSupport.schema();
    private final MySqlDialect dialect = new MySqlDialect();
    private final AutoSeeder seeder = new AutoSeeder(dialect, schema, 3);
    private final SnapshotEngine snapshots = new SnapshotEngine(dialect, schema);
    private final DataComparator comparator = new DataComparator();

    @BeforeEach
    void setUp() throws Exception {
        MySqlSupport.reset();
        exec("CREATE TABLE customer (id BIGINT AUTO_INCREMENT PRIMARY KEY, "
                + "name VARCHAR(50) NOT NULL)");
        exec("CREATE TABLE `order` (id BIGINT AUTO_INCREMENT PRIMARY KEY, "
                + "label VARCHAR(40) NOT NULL, customer_id BIGINT NOT NULL, "
                + "FOREIGN KEY (customer_id) REFERENCES customer(id))");
    }

    @Test
    void seedsForeignKeysAndDetectsRowChangeOnMySql() throws Exception {
        seeder.seed(ds);

        assertThat(rowCount("customer")).isEqualTo(3);
        assertThat(rowCount("order")).isEqualTo(3);
        assertThat(orphanOrders()).isZero();

        Snapshot before = snapshots.capture(ds);
        exec("UPDATE customer SET name = 'changed' LIMIT 1");
        Snapshot after = snapshots.capture(ds);

        assertThat(comparator.compare(before, after).hasDataLoss()).isTrue();
    }

    private int orphanOrders() throws Exception {
        return scalar("SELECT COUNT(*) FROM `order` o WHERE NOT EXISTS "
                + "(SELECT 1 FROM customer c WHERE c.id = o.customer_id)");
    }

    private int rowCount(String table) throws Exception {
        return scalar("SELECT COUNT(*) FROM `" + table + "`");
    }

    private int scalar(String sql) throws Exception {
        try (Connection c = ds.getConnection();
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery(sql)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private void exec(String sql) throws Exception {
        try (Connection c = ds.getConnection(); Statement s = c.createStatement()) {
            s.execute(sql);
        }
    }
}
