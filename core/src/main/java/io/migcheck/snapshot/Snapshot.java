package io.migcheck.snapshot;

import java.util.Map;

public record Snapshot(Map<String, TableSnapshot> tables) {
}
