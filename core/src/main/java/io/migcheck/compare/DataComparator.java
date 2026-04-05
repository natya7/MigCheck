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
            if (!td.removedRows().isEmpty() || !td.addedRows().isEmpty()) {
                changed.put(table, td);
            }
        }
        return new SnapshotDiff(tablesRemoved, tablesAdded, changed);
    }

    private SnapshotDiff.TableDiff diffRows(TableSnapshot before, TableSnapshot after) {
        List<Map<String, Object>> removed = new ArrayList<>(before.rows());
        removed.removeAll(after.rows());
        List<Map<String, Object>> added = new ArrayList<>(after.rows());
        added.removeAll(before.rows());
        return new SnapshotDiff.TableDiff(removed, added);
    }
}
