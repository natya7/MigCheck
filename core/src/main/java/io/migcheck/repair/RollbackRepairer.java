package io.migcheck.repair;

import io.migcheck.dialect.Dialect;
import io.migcheck.dialect.ForeignKey;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.alter.Alter;
import net.sf.jsqlparser.statement.alter.AlterExpression;
import net.sf.jsqlparser.statement.alter.AlterOperation;
import net.sf.jsqlparser.statement.delete.Delete;
import net.sf.jsqlparser.statement.drop.Drop;
import net.sf.jsqlparser.statement.truncate.Truncate;
import net.sf.jsqlparser.statement.update.Update;
import net.sf.jsqlparser.statement.update.UpdateSet;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class RollbackRepairer {

    private final Dialect dialect;
    private final RepairTemplates templates;
    private final String schema;

    public RollbackRepairer(Dialect dialect, RepairTemplates templates, String schema) {
        this.dialect = dialect;
        this.templates = templates;
        this.schema = schema;
    }

    public Optional<RollbackRepair> repair(DataSource ds, String rollbackSql) {
        List<Statement> statements = parse(rollbackSql);
        if (statements.isEmpty()) {
            return Optional.empty();
        }
        List<Repair> repairs = new ArrayList<>();
        for (Statement statement : statements) {
            Repair repair = classify(statement);
            if (repair == null) {
                return Optional.empty();
            }
            repairs.add(repair);
        }
        if (repairs.stream().allMatch(r -> r.kind == Kind.HARMLESS)) {
            return Optional.empty();
        }
        return generate(ds, repairs);
    }

    private enum Kind { HARMLESS, COLUMN_DROP, TABLE_DROP, TYPE_CHANGE, NULL_COLLAPSE, ROW_DELETE }

    private record Repair(Kind kind, String table, String column, String condition, String sql) {
    }

    private Repair classify(Statement statement) {
        if (statement instanceof Drop drop) {
            if ("TABLE".equalsIgnoreCase(drop.getType())) {
                return new Repair(Kind.TABLE_DROP, drop.getName().getName(), null, null,
                        statement.toString());
            }
            return null;
        }
        if (statement instanceof Truncate truncate) {
            return new Repair(Kind.ROW_DELETE, truncate.getTable().getName(), null, null,
                    statement.toString());
        }
        if (statement instanceof Delete delete) {
            String condition = delete.getWhere() == null ? null : delete.getWhere().toString();
            return new Repair(Kind.ROW_DELETE, delete.getTable().getName(), null, condition,
                    statement.toString());
        }
        if (statement instanceof Update update) {
            return classifyUpdate(update);
        }
        if (statement instanceof Alter alter) {
            return classifyAlter(alter);
        }
        return null;
    }

    private Repair classifyUpdate(Update update) {
        if (update.getUpdateSets().size() != 1 || update.getWhere() == null) {
            return null;
        }
        UpdateSet set = update.getUpdateSets().get(0);
        if (set.getColumns().size() != 1 || set.getValues().size() != 1) {
            return null;
        }
        String column = set.getColumns().get(0).getColumnName();
        Expression value = set.getValues().get(0);
        if (value instanceof Column || value.toString().contains("(")) {
            return null;
        }
        String where = normalized(update.getWhere().toString());
        if (!where.equalsIgnoreCase(column + " IS NULL")) {
            return null;
        }
        return new Repair(Kind.NULL_COLLAPSE, update.getTable().getName(), column, where,
                update.toString());
    }

    private Repair classifyAlter(Alter alter) {
        String table = alter.getTable().getName();
        Kind kind = Kind.HARMLESS;
        String column = null;
        for (AlterExpression expr : alter.getAlterExpressions()) {
            AlterOperation op = expr.getOperation();
            if (op == AlterOperation.DROP && expr.getColumnName() != null) {
                kind = Kind.COLUMN_DROP;
                column = expr.getColumnName();
            } else if ((op == AlterOperation.ALTER || op == AlterOperation.MODIFY)
                    && expr.getColDataTypeList() != null && !expr.getColDataTypeList().isEmpty()) {
                if (expr.getColDataTypeList().get(0).toString().toUpperCase().contains("NOT NULL")) {
                    return null;
                }
                kind = Kind.TYPE_CHANGE;
                column = expr.getColDataTypeList().get(0).getColumnName();
            } else if (op == AlterOperation.DROP) {
                return null;
            }
        }
        return new Repair(kind, table, column, null, alter.toString());
    }

    private Optional<RollbackRepair> generate(DataSource ds, List<Repair> repairs) {
        List<String> parks = new ArrayList<>();
        List<String> originals = new ArrayList<>();
        List<String> restores = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        List<String> existing = dialect.tableNames(ds, schema);
        for (Repair repair : repairs) {
            if (!apply(ds, repair, existing, parks, originals, restores, notes)) {
                return Optional.empty();
            }
        }
        List<String> reversed = new ArrayList<>(restores);
        Collections.reverse(reversed);
        List<String> safeParts = new ArrayList<>(parks);
        safeParts.addAll(originals);
        return Optional.of(new RollbackRepair(String.join("\n", safeParts),
                String.join("\n", reversed), notes));
    }

    private boolean apply(DataSource ds, Repair repair, List<String> existing,
                          List<String> parks, List<String> originals,
                          List<String> restores, List<String> notes) {
        switch (repair.kind) {
            case HARMLESS -> originals.add(terminated(repair.sql));
            case COLUMN_DROP, TYPE_CHANGE -> {
                String park = "migcheck_backup_" + repair.table + "_" + repair.column;
                if (existing.contains(park)) {
                    return false;
                }
                List<String> pk = dialect.primaryKeyColumns(ds, schema, repair.table);
                if (pk.isEmpty()) {
                    return false;
                }
                List<String> pkTypes = new ArrayList<>();
                for (String pkColumn : pk) {
                    pkTypes.add(dialect.columnType(ds, schema, repair.table, pkColumn));
                }
                String columnType = dialect.columnType(ds, schema, repair.table, repair.column);
                parks.add(templates.parkColumnValues(schema, repair.table, park,
                        pk, pkTypes, repair.column, columnType));
                originals.add(terminated(repair.sql));
                restores.add(templates.restoreColumnValues(schema, repair.table, park,
                        pk, repair.column));
                notes.add("parked column " + repair.column + " of table " + repair.table);
            }
            case TABLE_DROP -> {
                String park = "migcheck_backup_" + repair.table;
                if (existing.contains(park)) {
                    return false;
                }
                parks.add(templates.parkTableByRename(schema, repair.table, park));
                restores.add(templates.restoreTableByRename(schema, repair.table, park));
                notes.add("renamed table " + repair.table + " aside instead of dropping it");
            }
            case NULL_COLLAPSE -> {
                String park = "migcheck_backup_" + repair.table + "_rows";
                if (existing.contains(park)) {
                    return false;
                }
                List<String> pk = dialect.primaryKeyColumns(ds, schema, repair.table);
                if (pk.isEmpty()) {
                    return false;
                }
                List<String> pkTypes = new ArrayList<>();
                for (String pkColumn : pk) {
                    pkTypes.add(dialect.columnType(ds, schema, repair.table, pkColumn));
                }
                parks.add(templates.parkRowIds(schema, repair.table, park,
                        pk, pkTypes, repair.condition));
                originals.add(terminated(repair.sql));
                restores.add(templates.restoreNulledColumn(schema, repair.table, park,
                        pk, repair.column));
                notes.add("remembered which rows of " + repair.table
                        + " had NULL " + repair.column);
            }
            case ROW_DELETE -> {
                String park = "migcheck_backup_" + repair.table + "_rows";
                if (existing.contains(park)) {
                    return false;
                }
                parks.add(templates.parkRows(schema, repair.table, park, repair.condition));
                List<String> group = new ArrayList<>();
                group.add(templates.restoreRows(schema, repair.table, park));
                for (ForeignKey fk : dialect.foreignKeys(ds, schema)) {
                    if (!fk.referencedTable().equals(repair.table)) {
                        continue;
                    }
                    String childPark = "migcheck_backup_" + fk.table() + "_rows";
                    if (existing.contains(childPark)) {
                        return false;
                    }
                    List<String> parentPk = dialect.primaryKeyColumns(ds, schema, repair.table);
                    if (parentPk.size() != 1) {
                        return false;
                    }
                    String childCondition = fk.column() + " IN (SELECT " + parentPk.get(0)
                            + " FROM " + repair.table
                            + (repair.condition == null ? "" : " WHERE " + repair.condition) + ")";
                    parks.add(templates.parkRows(schema, fk.table(), childPark, childCondition));
                    group.add(templates.restoreRows(schema, fk.table(), childPark));
                }
                originals.add(terminated(repair.sql));
                restores.add(String.join("\n", group));
                notes.add("parked rows deleted from " + repair.table + " and its children");
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    private String terminated(String sql) {
        return sql.endsWith(";") ? sql : sql + ";";
    }

    private String normalized(String sql) {
        return sql.replaceAll("\\s+", " ").trim();
    }

    private List<Statement> parse(String sql) {
        try {
            return CCJSqlParserUtil.parseStatements(sql);
        } catch (Exception e) {
            return List.of();
        }
    }
}
