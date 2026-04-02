package io.migcheck.snapshot;

import java.util.List;
import java.util.Map;

public record TableSnapshot(String table, List<String> columns,
                            List<Map<String, Object>> rows) {
}
