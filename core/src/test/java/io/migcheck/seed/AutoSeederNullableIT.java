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

class AutoSeederNullableIT {

    private final DataSource ds = PostgresSupport.dataSource();
    private final AutoSeeder seeder = new AutoSeeder(new PostgresDialect(), "public", 3);

    @BeforeEach
    void setUp() throws Exception {
        PostgresSupport.reset();
        exec("CREATE TABLE note (id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY, "
                + "body VARCHAR(50))");
    }

    @Test
    void seedsAtLeastOneNullInNullableColumn() throws Exception {
        seeder.seed(ds);

        assertThat(rowCount("note")).isEqualTo(3);
        assertThat(nullCount("note", "body")).isGreaterThan(0);
    }

    private int nullCount(String table, String column) throws Exception {
        return scalar("SELECT COUNT(*) FROM " + table + " WHERE " + column + " IS NULL");
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
