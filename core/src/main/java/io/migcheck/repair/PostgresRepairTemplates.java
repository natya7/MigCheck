package io.migcheck.repair;

import java.util.ArrayList;
import java.util.List;

public class PostgresRepairTemplates implements RepairTemplates {

    @Override
    public String parkColumnValues(String schema, String table, String park,
                                   List<String> pkNames, List<String> pkTypes,
                                   String column, String columnType) {
        List<String> defs = new ArrayList<>();
        for (int i = 0; i < pkNames.size(); i++) {
            defs.add(q(pkNames.get(i)) + " " + pkTypes.get(i));
        }
        defs.add(q(column) + " " + columnType);
        return "CREATE TABLE " + qualified(schema, park) + " (" + String.join(", ", defs)
                + ", PRIMARY KEY (" + quotedList(pkNames) + "));\n"
                + "INSERT INTO " + qualified(schema, park)
                + " SELECT " + quotedList(pkNames) + ", " + q(column)
                + " FROM " + qualified(schema, table) + ";";
    }

    @Override
    public String restoreColumnValues(String schema, String table, String park,
                                      List<String> pkNames, String column) {
        return "UPDATE " + qualified(schema, table) + " t SET " + q(column) + " = p." + q(column)
                + " FROM " + qualified(schema, park) + " p WHERE " + joinCondition(pkNames) + ";\n"
                + "DROP TABLE " + qualified(schema, park) + ";";
    }

    @Override
    public String parkTableByRename(String schema, String table, String park) {
        return "ALTER TABLE " + qualified(schema, table) + " RENAME TO " + q(park) + ";";
    }

    @Override
    public String restoreTableByRename(String schema, String table, String park) {
        return "DROP TABLE " + qualified(schema, table) + ";\n"
                + "ALTER TABLE " + qualified(schema, park) + " RENAME TO " + q(table) + ";";
    }

    @Override
    public String parkRows(String schema, String table, String park, String condition) {
        String where = condition == null ? "" : " WHERE " + condition;
        return "CREATE TABLE " + qualified(schema, park) + " AS SELECT * FROM "
                + qualified(schema, table) + where + ";";
    }

    @Override
    public String restoreRows(String schema, String table, String park) {
        return "INSERT INTO " + qualified(schema, table) + " SELECT * FROM "
                + qualified(schema, park) + ";\n"
                + "DROP TABLE " + qualified(schema, park) + ";";
    }

    @Override
    public String parkRowIds(String schema, String table, String park,
                             List<String> pkNames, List<String> pkTypes, String condition) {
        List<String> defs = new ArrayList<>();
        for (int i = 0; i < pkNames.size(); i++) {
            defs.add(q(pkNames.get(i)) + " " + pkTypes.get(i));
        }
        return "CREATE TABLE " + qualified(schema, park) + " (" + String.join(", ", defs)
                + ", PRIMARY KEY (" + quotedList(pkNames) + "));\n"
                + "INSERT INTO " + qualified(schema, park)
                + " SELECT " + quotedList(pkNames) + " FROM " + qualified(schema, table)
                + " WHERE " + condition + ";";
    }

    @Override
    public String restoreNulledColumn(String schema, String table, String park,
                                      List<String> pkNames, String column) {
        return "UPDATE " + qualified(schema, table) + " t SET " + q(column) + " = NULL"
                + " FROM " + qualified(schema, park) + " p WHERE " + joinCondition(pkNames) + ";\n"
                + "DROP TABLE " + qualified(schema, park) + ";";
    }

    private String joinCondition(List<String> pkNames) {
        List<String> parts = new ArrayList<>();
        for (String pk : pkNames) {
            parts.add("t." + q(pk) + " = p." + q(pk));
        }
        return String.join(" AND ", parts);
    }

    private String quotedList(List<String> names) {
        List<String> quoted = new ArrayList<>();
        for (String name : names) {
            quoted.add(q(name));
        }
        return String.join(", ", quoted);
    }

    private String qualified(String schema, String table) {
        return q(schema) + "." + q(table);
    }

    private String q(String identifier) {
        return "\"" + identifier + "\"";
    }
}
