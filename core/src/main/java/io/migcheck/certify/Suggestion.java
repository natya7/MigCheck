package io.migcheck.certify;

public record Suggestion(String version, String description,
                         GeneratedRollback rollback, String reason) {
}
