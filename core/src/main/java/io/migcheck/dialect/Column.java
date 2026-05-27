package io.migcheck.dialect;

public record Column(String name, String dataType, boolean nullable,
                     boolean generated, Integer maxLength) {
}
