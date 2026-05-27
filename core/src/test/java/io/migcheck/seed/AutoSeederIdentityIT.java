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

class AutoSeederIdentityIT {

    private final DataSource ds = PostgresSupport.dataSource();
    private final AutoSeeder seeder = new AutoSeeder(new PostgresDialect(), "public", 3);

    @BeforeEach
    void setUp() throws Exception {
        PostgresSupport.reset();
        exec("CREATE TABLE parent (id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY, "
                + "code VARCHAR(1) NOT NULL)");
        exec("CREATE TABLE child (id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY, "
                + "name VARCHAR(50) NOT NULL, parent_id BIGINT NOT NULL REFERENCES parent(id))");
    }

    @Test
    void seedsIdentityPkFkAndTruncatesToColumnLength() throws Exception {
        seeder.seed(ds);

        assertThat(rowCount("parent")).isEqualTo(3);
        assertThat(rowCount("child")).isEqualTo(3);
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
