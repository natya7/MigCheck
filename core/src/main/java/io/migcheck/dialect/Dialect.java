package io.migcheck.dialect;

import javax.sql.DataSource;
import java.util.List;

public interface Dialect {

    List<String> tableNames(DataSource dataSource, String schema);

    List<String> columnNames(DataSource dataSource, String schema, String table);

    List<Column> columns(DataSource dataSource, String schema, String table);

    List<String> primaryKeyColumns(DataSource dataSource, String schema, String table);

    List<ForeignKey> foreignKeys(DataSource dataSource, String schema);

    default List<CompositeForeignKey> compositeForeignKeys(DataSource dataSource, String schema) {
        return List.of();
    }

    default String quoteIdentifier(String identifier) {
        return "\"" + identifier + "\"";
    }

    default String emptyInsert(String qualifiedTable) {
        return "INSERT INTO " + qualifiedTable + " DEFAULT VALUES";
    }

    Object sampleValue(String dataType, int seed);
}
