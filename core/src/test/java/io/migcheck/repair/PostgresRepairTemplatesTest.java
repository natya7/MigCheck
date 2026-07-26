package io.migcheck.repair;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PostgresRepairTemplatesTest {

    private final PostgresRepairTemplates templates = new PostgresRepairTemplates();

    @Test
    void parksColumnValuesKeyedByPrimaryKey() {
        String sql = templates.parkColumnValues("public", "users", "migcheck_backup_users_note",
                List.of("id"), List.of("bigint"), "note", "character varying(50)");

        assertThat(sql).isEqualTo(
                "CREATE TABLE \"public\".\"migcheck_backup_users_note\" "
                        + "(\"id\" bigint, \"note\" character varying(50), PRIMARY KEY (\"id\"));\n"
                        + "INSERT INTO \"public\".\"migcheck_backup_users_note\" "
                        + "SELECT \"id\", \"note\" FROM \"public\".\"users\";");
    }

    @Test
    void restoresColumnValuesWithUpdateFromJoin() {
        String sql = templates.restoreColumnValues("public", "users", "migcheck_backup_users_note",
                List.of("id"), "note");

        assertThat(sql).isEqualTo(
                "UPDATE \"public\".\"users\" t SET \"note\" = p.\"note\" "
                        + "FROM \"public\".\"migcheck_backup_users_note\" p "
                        + "WHERE t.\"id\" = p.\"id\";\n"
                        + "DROP TABLE \"public\".\"migcheck_backup_users_note\";");
    }

    @Test
    void handlesCompositePrimaryKeys() {
        String sql = templates.parkColumnValues("public", "office", "migcheck_backup_office_area",
                List.of("country", "region"),
                List.of("character varying(20)", "character varying(20)"),
                "area", "character varying(20)");

        assertThat(sql).contains("PRIMARY KEY (\"country\", \"region\")");
        assertThat(sql).contains("SELECT \"country\", \"region\", \"area\"");
    }
}
