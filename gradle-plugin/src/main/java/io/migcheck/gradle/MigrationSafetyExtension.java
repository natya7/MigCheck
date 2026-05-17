package io.migcheck.gradle;

public class MigrationSafetyExtension {

    private String migrationDir = "src/main/resources/db/migration";

    public String getMigrationDir() {
        return migrationDir;
    }

    public void setMigrationDir(String migrationDir) {
        this.migrationDir = migrationDir;
    }
}
