package io.migcheck.certify;

import io.migcheck.dialect.Dialect;
import io.migcheck.repair.RepairTemplates;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.alter.Alter;
import net.sf.jsqlparser.statement.alter.AlterExpression;
import net.sf.jsqlparser.statement.alter.AlterOperation;
import net.sf.jsqlparser.statement.create.table.CreateTable;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class RollbackGenerator {

    private final Dialect dialect;
    private final RepairTemplates templates;
    private final String schema;

    public RollbackGenerator(Dialect dialect, RepairTemplates templates, String schema) {
        this.dialect = dialect;
        this.templates = templates;
        this.schema = schema;
    }

    public Optional<GeneratedRollback> generate(DataSource ds, String migrationSql) {
        List<Statement> statements = parse(migrationSql);
        if (statements.isEmpty()) {
            return Optional.empty();
        }
        List<String> undos = new ArrayList<>();
        List<String> restores = new ArrayList<>();
        for (Statement statement : statements) {
            if (statement instanceof CreateTable create) {
                String table = create.getTable().getName();
                String park = "migcheck_backup_" + table;
                undos.add(templates.parkTableByRename(schema, table, park));
                restores.add(templates.restoreTableByRename(schema, table, park));
            } else if (statement instanceof Alter alter && addedColumn(alter) != null) {
                String table = alter.getTable().getName();
                String column = addedColumn(alter);
                String park = "migcheck_backup_" + table + "_" + column;
                List<String> pk = dialect.primaryKeyColumns(ds, schema, table);
                if (pk.isEmpty()) {
                    return Optional.empty();
                }
                List<String> pkTypes = new ArrayList<>();
                for (String pkColumn : pk) {
                    pkTypes.add(dialect.columnType(ds, schema, table, pkColumn));
                }
                String columnType = dialect.columnType(ds, schema, table, column);
                undos.add(templates.parkColumnValues(schema, table, park, pk, pkTypes,
                        column, columnType)
                        + "\nALTER TABLE " + table + " DROP COLUMN " + column + ";");
                restores.add(templates.restoreColumnValues(schema, table, park, pk, column));
            } else {
                return Optional.empty();
            }
        }
        Collections.reverse(undos);
        return Optional.of(new GeneratedRollback(String.join("\n", undos),
                String.join("\n", restores)));
    }

    private String addedColumn(Alter alter) {
        if (alter.getAlterExpressions() == null || alter.getAlterExpressions().size() != 1) {
            return null;
        }
        AlterExpression expr = alter.getAlterExpressions().get(0);
        if (expr.getOperation() != AlterOperation.ADD
                || expr.getColDataTypeList() == null || expr.getColDataTypeList().size() != 1) {
            return null;
        }
        return expr.getColDataTypeList().get(0).getColumnName();
    }

    private List<Statement> parse(String sql) {
        try {
            return CCJSqlParserUtil.parseStatements(sql);
        } catch (Exception e) {
            return List.of();
        }
    }
}
