package io.migcheck.engine;

import javax.sql.DataSource;
import java.util.Set;

public interface MigrationEngine {

    void migrate(DataSource dataSource);

    void rollback(DataSource dataSource, String rollbackSql);

    void clean(DataSource dataSource);

    default Set<String> metadataTables() {
        return Set.of();
    }
}
