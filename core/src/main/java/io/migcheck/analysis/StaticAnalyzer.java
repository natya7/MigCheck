package io.migcheck.analysis;

import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.Statements;
import net.sf.jsqlparser.statement.alter.Alter;
import net.sf.jsqlparser.statement.alter.AlterExpression;
import net.sf.jsqlparser.statement.alter.AlterOperation;
import net.sf.jsqlparser.statement.drop.Drop;
import net.sf.jsqlparser.statement.truncate.Truncate;

import java.util.ArrayList;
import java.util.List;

public class StaticAnalyzer {

    public StaticResult analyze(String sql) {
        List<Finding> findings = new ArrayList<>();
        Statements statements;
        try {
            statements = CCJSqlParserUtil.parseStatements(sql);
        } catch (Exception e) {
            findings.add(new Finding(RiskLevel.MEDIUM, "Could not parse SQL: " + e.getMessage()));
            return new StaticResult(RiskLevel.MEDIUM, findings);
        }
        for (Statement statement : statements.getStatements()) {
            inspect(statement, findings);
        }
        return new StaticResult(highestRisk(findings), findings);
    }

    private void inspect(Statement statement, List<Finding> findings) {
        if (statement instanceof Drop drop) {
            if ("TABLE".equalsIgnoreCase(drop.getType())) {
                findings.add(new Finding(RiskLevel.HIGH, "DROP TABLE " + drop.getName()));
            }
        } else if (statement instanceof Truncate truncate) {
            findings.add(new Finding(RiskLevel.HIGH, "TRUNCATE " + truncate.getTable()));
        } else if (statement instanceof Alter alter) {
            for (AlterExpression expr : alter.getAlterExpressions()) {
                inspectAlter(expr, findings);
            }
        }
    }

    private void inspectAlter(AlterExpression expr, List<Finding> findings) {
        AlterOperation op = expr.getOperation();
        if (op == AlterOperation.DROP && expr.getColumnName() != null) {
            findings.add(new Finding(RiskLevel.HIGH, "DROP COLUMN " + expr.getColumnName()));
        } else if ((op == AlterOperation.ALTER || op == AlterOperation.MODIFY)
                && expr.getColDataTypeList() != null && !expr.getColDataTypeList().isEmpty()) {
            findings.add(new Finding(RiskLevel.MEDIUM, "column type change"));
        }
    }

    private RiskLevel highestRisk(List<Finding> findings) {
        RiskLevel highest = RiskLevel.LOW;
        for (Finding finding : findings) {
            if (finding.risk().ordinal() > highest.ordinal()) {
                highest = finding.risk();
            }
        }
        return highest;
    }
}
