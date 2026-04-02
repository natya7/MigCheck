package io.migcheck.dialect;

public record ForeignKey(String table, String referencedTable, String column) {
}
