package io.migcheck.engine;

import javax.sql.DataSource;

public interface MigrationEngine {

    void migrate(DataSource dataSource);

    void rollback(DataSource dataSource, String rollbackSql);

    void clean(DataSource dataSource);
}
