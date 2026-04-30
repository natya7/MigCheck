package io.migcheck.dialect;

import javax.sql.DataSource;
import java.util.List;

public interface Dialect {

    List<String> tableNames(DataSource dataSource, String schema);

    List<String> columnNames(DataSource dataSource, String schema, String table);

    List<Column> columns(DataSource dataSource, String schema, String table);

    List<String> primaryKeyColumns(DataSource dataSource, String schema, String table);

    List<ForeignKey> foreignKeys(DataSource dataSource, String schema);

    Object sampleValue(String dataType, int seed);
}
