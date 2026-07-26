package io.migcheck.dialect;

import io.migcheck.testing.PostgresSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

class PostgresDialectIT {

    private final DataSource ds = PostgresSupport.dataSource();
    private final PostgresDialect dialect = new PostgresDialect();

    @BeforeEach
    void setUp() throws Exception {
        PostgresSupport.reset();
        exec("CREATE TABLE country (id BIGINT PRIMARY KEY)");
        exec("CREATE TABLE customer (id BIGINT PRIMARY KEY, "
                + "country_id BIGINT REFERENCES country(id))");
    }

    @Test
    void listsTables() {
        assertThat(dialect.tableNames(ds, "public"))
                .containsExactlyInAnyOrder("country", "customer");
    }

    @Test
    void listsForeignKeys() {
        assertThat(dialect.foreignKeys(ds, "public"))
                .containsExactly(new ForeignKey("customer", "country", "country_id"));
    }

    @Test
    void listsColumnsInOrder() {
        assertThat(dialect.columnNames(ds, "public", "customer"))
                .containsExactly("id", "country_id");
    }

    @Test
    void reportsExactColumnTypes() throws Exception {
        exec("CREATE TABLE typed (id BIGINT PRIMARY KEY, label VARCHAR(40), amount NUMERIC(14,4))");

        assertThat(dialect.columnType(ds, "public", "typed", "id")).isEqualTo("bigint");
        assertThat(dialect.columnType(ds, "public", "typed", "label"))
                .isEqualTo("character varying(40)");
        assertThat(dialect.columnType(ds, "public", "typed", "amount")).isEqualTo("numeric(14,4)");
    }

    private void exec(String sql) throws Exception {
        try (Connection c = ds.getConnection(); Statement s = c.createStatement()) {
            s.execute(sql);
        }
    }
}
