package io.migcheck.repair;

import java.util.List;

public interface RepairTemplates {

    String parkColumnValues(String schema, String table, String park,
                            List<String> pkNames, List<String> pkTypes,
                            String column, String columnType);

    String restoreColumnValues(String schema, String table, String park,
                               List<String> pkNames, String column);

    String parkTableByRename(String schema, String table, String park);

    String restoreTableByRename(String schema, String table, String park);

    String parkRows(String schema, String table, String park, String condition);

    String restoreRows(String schema, String table, String park);

    String parkRowIds(String schema, String table, String park,
                      List<String> pkNames, List<String> pkTypes, String condition);

    String restoreNulledColumn(String schema, String table, String park,
                               List<String> pkNames, String column);
}
