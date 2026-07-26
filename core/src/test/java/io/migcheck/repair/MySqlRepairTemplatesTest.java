package io.migcheck.repair;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MySqlRepairTemplatesTest {

    private final MySqlRepairTemplates templates = new MySqlRepairTemplates();

    @Test
    void parksColumnValuesKeyedByPrimaryKey() {
        String sql = templates.parkColumnValues("app", "account", "migcheck_backup_account_note",
                List.of("id"), List.of("bigint"), "note", "varchar(50)");

        assertThat(sql).isEqualTo(
                "CREATE TABLE `app`.`migcheck_backup_account_note` "
                        + "(`id` bigint, `note` varchar(50), PRIMARY KEY (`id`));\n"
                        + "INSERT INTO `app`.`migcheck_backup_account_note` "
                        + "SELECT `id`, `note` FROM `app`.`account`;");
    }

    @Test
    void restoresColumnValuesWithUpdateJoin() {
        String sql = templates.restoreColumnValues("app", "account", "migcheck_backup_account_note",
                List.of("id"), "note");

        assertThat(sql).isEqualTo(
                "UPDATE `app`.`account` t JOIN `app`.`migcheck_backup_account_note` p "
                        + "ON t.`id` = p.`id` SET t.`note` = p.`note`;\n"
                        + "DROP TABLE `app`.`migcheck_backup_account_note`;");
    }

    @Test
    void renamesTablesWithMySqlSyntax() {
        assertThat(templates.parkTableByRename("app", "audit_log", "migcheck_backup_audit_log"))
                .isEqualTo("RENAME TABLE `app`.`audit_log` TO `app`.`migcheck_backup_audit_log`;");
        assertThat(templates.restoreTableByRename("app", "audit_log", "migcheck_backup_audit_log"))
                .isEqualTo("DROP TABLE `app`.`audit_log`;\n"
                        + "RENAME TABLE `app`.`migcheck_backup_audit_log` TO `app`.`audit_log`;");
    }
}
