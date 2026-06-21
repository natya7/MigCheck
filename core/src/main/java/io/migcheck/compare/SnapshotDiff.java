package io.migcheck.compare;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record SnapshotDiff(List<String> tablesRemoved,
                           List<String> tablesAdded,
                           Map<String, TableDiff> changedTables) {

    public boolean hasDataLoss() {
        if (!tablesRemoved.isEmpty()) {
            return true;
        }
        return changedTables.values().stream()
                .anyMatch(td -> !td.removedRows().isEmpty() || !td.modifiedRows().isEmpty());
    }

    public String summary() {
        List<String> lines = differences();
        return lines.isEmpty() ? "rollback preserved all data" : String.join("\n", lines);
    }

    public List<String> differences() {
        List<String> lines = new ArrayList<>();
        for (String table : tablesRemoved) {
            lines.add("table " + table + " was dropped and not restored");
        }
        for (Map.Entry<String, TableDiff> entry : changedTables.entrySet()) {
            String table = entry.getKey();
            TableDiff td = entry.getValue();
            for (Map<String, Object> row : td.removedRows()) {
                lines.add("table " + table + ": row " + row + " lost");
            }
            for (RowChange change : td.modifiedRows()) {
                for (String column : change.changedColumns()) {
                    lines.add("table " + table + ": row " + change.key() + " column " + column
                            + " changed " + change.before().get(column)
                            + " -> " + change.after().get(column));
                }
            }
        }
        return lines;
    }

    public record TableDiff(List<Map<String, Object>> removedRows,
                            List<Map<String, Object>> addedRows,
                            List<RowChange> modifiedRows) {
    }

    public record RowChange(Map<String, Object> key,
                            Map<String, Object> before,
                            Map<String, Object> after) {

        public List<String> changedColumns() {
            List<String> columns = new ArrayList<>();
            for (String column : before.keySet()) {
                if (!Objects.equals(before.get(column), after.get(column))) {
                    columns.add(column);
                }
            }
            return columns;
        }
    }
}
