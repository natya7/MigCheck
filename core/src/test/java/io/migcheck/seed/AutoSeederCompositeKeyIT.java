package io.migcheck.seed;

import io.migcheck.dialect.PostgresDialect;
import io.migcheck.testing.PostgresSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

class AutoSeederCompositeKeyIT {

    private final DataSource ds = PostgresSupport.dataSource();
    private final AutoSeeder seeder = new AutoSeeder(new PostgresDialect(), "public", 3);

    @BeforeEach
    void setUp() throws Exception {
        PostgresSupport.reset();
        exec("CREATE TABLE region (country VARCHAR(20) NOT NULL, area VARCHAR(20) NOT NULL, "
                + "PRIMARY KEY (country, area))");
        exec("CREATE TABLE office (id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY, "
                + "country VARCHAR(20) NOT NULL, area VARCHAR(20) NOT NULL, "
                + "FOREIGN KEY (country, area) REFERENCES region(country, area))");
    }

    @Test
    void seedsCompositeKeyParentAndReusesTupleInChild() throws Exception {
        seeder.seed(ds);

        assertThat(rowCount("region")).isEqualTo(3);
        assertThat(rowCount("office")).isEqualTo(3);
        assertThat(orphanOffices()).isZero();
    }

    private int orphanOffices() throws Exception {
        return scalar("SELECT COUNT(*) FROM office o WHERE NOT EXISTS "
                + "(SELECT 1 FROM region r WHERE r.country = o.country AND r.area = o.area)");
    }

    private int rowCount(String table) throws Exception {
        return scalar("SELECT COUNT(*) FROM " + table);
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
