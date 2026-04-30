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

class AutoSeederIT {

    private final DataSource ds = PostgresSupport.dataSource();
    private final AutoSeeder seeder = new AutoSeeder(new PostgresDialect(), "public", 3);

    @BeforeEach
    void setUp() throws Exception {
        PostgresSupport.reset();
        exec("CREATE TABLE countries (id BIGINT PRIMARY KEY, name VARCHAR(100) NOT NULL)");
        exec("CREATE TABLE customers (id BIGINT PRIMARY KEY, name VARCHAR(100) NOT NULL, "
                + "country_id BIGINT NOT NULL REFERENCES countries(id))");
    }

    @Test
    void seedsParentAndChildRespectingForeignKeys() throws Exception {
        seeder.seed(ds);

        assertThat(rowCount("countries")).isEqualTo(3);
        assertThat(rowCount("customers")).isEqualTo(3);
    }

    private int rowCount(String table) throws Exception {
        try (Connection c = ds.getConnection();
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM " + table)) {
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
