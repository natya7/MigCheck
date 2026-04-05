package io.migcheck.compare;

import java.util.List;
import java.util.Map;

public record SnapshotDiff(List<String> tablesRemoved,
                           List<String> tablesAdded,
                           Map<String, TableDiff> changedTables) {

    public boolean hasDataLoss() {
        if (!tablesRemoved.isEmpty()) {
            return true;
        }
        return changedTables.values().stream()
                .anyMatch(td -> !td.removedRows().isEmpty());
    }

    public record TableDiff(List<Map<String, Object>> removedRows,
                            List<Map<String, Object>> addedRows) {
    }
}
