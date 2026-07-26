package io.migcheck.certify;

import java.nio.file.Path;

public record MigrationStep(String version, String description, Path rollbackScript) {
}
