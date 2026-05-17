package io.migcheck.gradle;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class MigrationSafetyPluginTest {

    @TempDir
    File projectDir;

    @Test
    void staticTaskReportsRiskAndFailsOnHigh() throws Exception {
        write("settings.gradle", "rootProject.name = 'sample'");
        write("build.gradle",
                "plugins { id 'io.migcheck.migration-safety' }\n"
                        + "migrationSafety { migrationDir = 'migrations' }\n");
        write("migrations/V1__create_users.sql", "CREATE TABLE users (id BIGINT PRIMARY KEY)");
        write("migrations/V2__drop_users.sql", "DROP TABLE users");

        write("gradle.properties", "org.gradle.jvmargs=-Xms32m -Xmx128m");

        BuildResult result = GradleRunner.create()
                .withProjectDir(projectDir)
                .withPluginClasspath()
                .withArguments("migrationSafetyStatic")
                .buildAndFail();

        assertThat(result.getOutput()).contains("PASS").contains("V1__create_users.sql");
        assertThat(result.getOutput()).contains("FAIL").contains("V2__drop_users.sql");
    }

    private void write(String relativePath, String content) throws Exception {
        Path path = projectDir.toPath().resolve(relativePath);
        Files.createDirectories(path.getParent());
        Files.writeString(path, content);
    }
}
