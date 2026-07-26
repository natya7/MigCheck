package io.migcheck.engine;

import javax.sql.DataSource;

public interface VersionedMigrationEngine extends MigrationEngine {

    void migrateTo(DataSource dataSource, String version);
}
