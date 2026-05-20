package io.migcheck.gradle;

import io.migcheck.analysis.RiskLevel;
import io.migcheck.analysis.StaticAnalyzer;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.options.Option;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;

public abstract class MigrationSafetyStaticTask extends DefaultTask {

    @Input
    public abstract Property<String> getMigrationDir();

    @Input
    @Optional
    @Option(option = "fail-on-warning", description = "Fail the build on WARNING (MEDIUM) findings too")
    public abstract Property<Boolean> getFailOnWarning();

    @Inject
    protected abstract ProjectLayout getProjectLayout();

    @TaskAction
    public void analyze() {
        File dir = getProjectLayout().getProjectDirectory().dir(getMigrationDir().get()).getAsFile();
        File[] files = dir.listFiles((d, name) -> name.endsWith(".sql"));
        if (files == null) {
            throw new GradleException("Migration directory not found: " + dir);
        }
        Arrays.sort(files);
        if (files.length == 0) {
            getLogger().warn("No .sql migrations found in " + dir);
        }
        StaticAnalyzer analyzer = new StaticAnalyzer();
        boolean failOnWarning = getFailOnWarning().getOrElse(false);
        boolean failed = false;
        for (File file : files) {
            RiskLevel risk = analyzer.analyze(read(file)).risk();
            getLogger().lifecycle(label(risk) + "  " + file.getName());
            if (risk == RiskLevel.HIGH || (failOnWarning && risk == RiskLevel.MEDIUM)) {
                failed = true;
            }
        }
        if (failed) {
            throw new GradleException("Migration safety check failed");
        }
    }

    private String label(RiskLevel risk) {
        return switch (risk) {
            case HIGH -> "FAIL";
            case MEDIUM -> "WARNING";
            case LOW -> "PASS";
        };
    }

    private String read(File file) {
        try {
            return Files.readString(file.toPath());
        } catch (IOException e) {
            throw new GradleException("Could not read " + file, e);
        }
    }
}
