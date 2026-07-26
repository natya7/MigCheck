package io.migcheck.gradle;

public class MigrationSafetyExtension {

    private String migrationDir = "src/main/resources/db/migration";
    private String rollbackDir = "src/main/resources/db/rollback";
    private String jdbcUrl;
    private String username = "";
    private String password = "";
    private String rollbackSql;
    private String database = "postgres";
    private String schema = "public";

    public String getMigrationDir() {
        return migrationDir;
    }

    public void setMigrationDir(String migrationDir) {
        this.migrationDir = migrationDir;
    }

    public String getRollbackDir() {
        return rollbackDir;
    }

    public void setRollbackDir(String rollbackDir) {
        this.rollbackDir = rollbackDir;
    }

    public String getJdbcUrl() {
        return jdbcUrl;
    }

    public void setJdbcUrl(String jdbcUrl) {
        this.jdbcUrl = jdbcUrl;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getRollbackSql() {
        return rollbackSql;
    }

    public void setRollbackSql(String rollbackSql) {
        this.rollbackSql = rollbackSql;
    }

    public String getDatabase() {
        return database;
    }

    public void setDatabase(String database) {
        this.database = database;
    }

    public String getSchema() {
        return schema;
    }

    public void setSchema(String schema) {
        this.schema = schema;
    }
}
