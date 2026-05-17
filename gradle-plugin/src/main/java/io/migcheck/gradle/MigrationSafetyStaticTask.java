package io.migcheck.gradle;

import io.migcheck.analysis.RiskLevel;
import io.migcheck.analysis.StaticAnalyzer;
import io.migcheck.analysis.StaticResult;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;

public abstract class MigrationSafetyStaticTask extends DefaultTask {

    @Input
    public abstract Property<String> getMigrationDir();

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
        StaticAnalyzer analyzer = new StaticAnalyzer();
        boolean anyHigh = false;
        for (File file : files) {
            StaticResult result = analyzer.analyze(read(file));
            getLogger().lifecycle(label(result.risk()) + "  " + file.getName());
            if (result.risk() == RiskLevel.HIGH) {
                anyHigh = true;
            }
        }
        if (anyHigh) {
            throw new GradleException("Migration safety check failed: HIGH-risk migrations found");
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
