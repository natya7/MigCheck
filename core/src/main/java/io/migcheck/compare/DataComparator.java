package io.migcheck.compare;

import io.migcheck.snapshot.Snapshot;
import io.migcheck.snapshot.TableSnapshot;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DataComparator {

    public SnapshotDiff compare(Snapshot before, Snapshot after) {
        List<String> tablesRemoved = new ArrayList<>();
        List<String> tablesAdded = new ArrayList<>();
        Map<String, SnapshotDiff.TableDiff> changed = new LinkedHashMap<>();

        for (String table : before.tables().keySet()) {
            if (!after.tables().containsKey(table)) {
                tablesRemoved.add(table);
            }
        }
        for (String table : after.tables().keySet()) {
            if (!before.tables().containsKey(table)) {
                tablesAdded.add(table);
            }
        }
        for (String table : before.tables().keySet()) {
            if (!after.tables().containsKey(table)) {
                continue;
            }
            SnapshotDiff.TableDiff td =
                    diffRows(before.tables().get(table), after.tables().get(table));
            if (!td.removedRows().isEmpty() || !td.addedRows().isEmpty()
                    || !td.modifiedRows().isEmpty()) {
                changed.put(table, td);
            }
        }
        return new SnapshotDiff(tablesRemoved, tablesAdded, changed);
    }

    private SnapshotDiff.TableDiff diffRows(TableSnapshot before, TableSnapshot after) {
        List<String> pk = before.pkColumns();
        if (pk.isEmpty()) {
            List<Map<String, Object>> removed = new ArrayList<>(before.rows());
            removed.removeAll(after.rows());
            List<Map<String, Object>> added = new ArrayList<>(after.rows());
            added.removeAll(before.rows());
            return new SnapshotDiff.TableDiff(removed, added, List.of());
        }
        Map<List<Object>, Map<String, Object>> beforeByKey = indexByKey(before.rows(), pk);
        Map<List<Object>, Map<String, Object>> afterByKey = indexByKey(after.rows(), pk);
        List<Map<String, Object>> removed = new ArrayList<>();
        List<Map<String, Object>> added = new ArrayList<>();
        List<SnapshotDiff.RowChange> modified = new ArrayList<>();
        for (Map.Entry<List<Object>, Map<String, Object>> entry : beforeByKey.entrySet()) {
            Map<String, Object> afterRow = afterByKey.get(entry.getKey());
            if (afterRow == null) {
                removed.add(entry.getValue());
            } else if (!afterRow.equals(entry.getValue())) {
                modified.add(new SnapshotDiff.RowChange(keyMap(pk, entry.getKey()),
                        entry.getValue(), afterRow));
            }
        }
        for (Map.Entry<List<Object>, Map<String, Object>> entry : afterByKey.entrySet()) {
            if (!beforeByKey.containsKey(entry.getKey())) {
                added.add(entry.getValue());
            }
        }
        return new SnapshotDiff.TableDiff(removed, added, modified);
    }

    private Map<List<Object>, Map<String, Object>> indexByKey(List<Map<String, Object>> rows,
                                                              List<String> pk) {
        Map<List<Object>, Map<String, Object>> index = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            List<Object> key = new ArrayList<>();
            for (String column : pk) {
                key.add(row.get(column));
            }
            index.put(key, row);
        }
        return index;
    }

    private Map<String, Object> keyMap(List<String> pk, List<Object> key) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < pk.size(); i++) {
            result.put(pk.get(i), key.get(i));
        }
        return result;
    }
}
