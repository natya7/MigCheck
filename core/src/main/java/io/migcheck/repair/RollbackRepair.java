package io.migcheck.repair;

import java.util.List;

public record RollbackRepair(String safeRollbackSql, String restoreSql, List<String> notes) {
}
