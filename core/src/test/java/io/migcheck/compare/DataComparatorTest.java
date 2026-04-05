package io.migcheck.compare;

import io.migcheck.snapshot.Snapshot;
import io.migcheck.snapshot.TableSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DataComparatorTest {

    private final DataComparator comparator = new DataComparator();

    @Test
    void detectsRemovedRow() {
        Snapshot before = snapshotOf(row(1, "Ada"), row(2, "Linus"));
        Snapshot after = snapshotOf(row(1, "Ada"));

        SnapshotDiff diff = comparator.compare(before, after);

        assertThat(diff.hasDataLoss()).isTrue();
        assertThat(diff.changedTables().get("person").removedRows())
                .containsExactly(Map.of("id", 2, "name", "Linus"));
    }

    @Test
    void noDiffWhenIdentical() {
        Snapshot before = snapshotOf(row(1, "Ada"));
        Snapshot after = snapshotOf(row(1, "Ada"));

        assertThat(comparator.compare(before, after).hasDataLoss()).isFalse();
    }

    private Map<String, Object> row(Object id, String name) {
        return Map.of("id", id, "name", name);
    }

    @SafeVarargs
    private Snapshot snapshotOf(Map<String, Object>... rows) {
        TableSnapshot ts =
                new TableSnapshot("person", List.of("id", "name"), List.of(rows));
        return new Snapshot(Map.of("person", ts));
    }
}
